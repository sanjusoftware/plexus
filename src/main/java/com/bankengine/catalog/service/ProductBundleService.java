package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductBundleService {

    private final ProductBundleRepository productBundleRepository;
    private final ProductRepository productRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;
    private final CatalogConstraintService constraintService;

    @Transactional
    public Long createBundle(ProductBundleRequest dto) {
        String bankId = BankContextHolder.getBankId();
        return saveBundleWithLinks(dto, bankId, ProductBundle.BundleStatus.DRAFT).getId();
    }

    @Transactional
    public void activateBundle(Long bundleId) {
        ProductBundle bundle = getBundle(bundleId);

        // 1. Basic State Validations
        if (bundle.getStatus() != ProductBundle.BundleStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT bundles can be activated. Current: " + bundle.getStatus());
        }

        if (bundle.getContainedProducts().isEmpty()) {
            throw new IllegalStateException("Cannot activate a bundle with no products.");
        }

        // 2. Validate "One Main Account" Rule (Double check before activation)
        long mainAccountCount = bundle.getContainedProducts().stream()
                .filter(BundleProductLink::isMainAccount)
                .count();
        if (mainAccountCount > 1) {
            throw new IllegalStateException("Data Integrity Error: Bundle has more than 1 Main Account.");
        }

        // 3. Validate Product States (Constituents must be ACTIVE)
        List<String> inactiveProducts = bundle.getContainedProducts().stream()
                .map(BundleProductLink::getProduct)
                .filter(p -> !"ACTIVE".equalsIgnoreCase(p.getStatus()))
                .map(Product::getName)
                .toList();

        if (!inactiveProducts.isEmpty()) {
            throw new IllegalStateException("Cannot activate bundle. Products must be ACTIVE: " +
                    String.join(", ", inactiveProducts));
        }

        // 4. Temporal Alignment
        if (bundle.getActivationDate().isBefore(LocalDate.now())) {
            bundle.setActivationDate(LocalDate.now());
        }

        // 5. Finalize
        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        productBundleRepository.save(bundle);
    }

    @Transactional
    public Long updateBundle(Long oldBundleId, ProductBundleRequest dto) {
        String bankId = BankContextHolder.getBankId();

        ProductBundle oldBundle = getBundle(oldBundleId);
        oldBundle.setStatus(ProductBundle.BundleStatus.ARCHIVED);
        oldBundle.setExpiryDate(LocalDate.now());
        productBundleRepository.save(oldBundle);

        // 2. Create the new version (defaults to DRAFT for review)
        return saveBundleWithLinks(dto, bankId, ProductBundle.BundleStatus.DRAFT).getId();
    }

    @Transactional
    public Long cloneBundle(Long bundleId, String newName) {
        ProductBundle source = getBundle(bundleId);
        String bankId = BankContextHolder.getBankId();

        // VALIDATION: Ensure source is valid before cloning
        long mainCount = source.getContainedProducts().stream()
                .filter(BundleProductLink::isMainAccount)
                .count();
        if (mainCount > 1) {
            throw new IllegalStateException("Cannot clone: Source bundle has multiple Main Accounts.");
        }

        ProductBundle cloned = new ProductBundle();
        cloned.setBankId(bankId);
        cloned.setName(newName);
        cloned.setCode(source.getCode() + "_COPY_" + System.currentTimeMillis());
        cloned.setDescription(source.getDescription());
        cloned.setEligibilitySegment(source.getEligibilitySegment());
        cloned.setActivationDate(source.getActivationDate());
        cloned.setExpiryDate(source.getExpiryDate());
        cloned.setStatus(ProductBundle.BundleStatus.DRAFT);

        ProductBundle savedClone = productBundleRepository.save(cloned);

        source.getContainedProducts().forEach(oldLink -> {
            BundleProductLink newLink = new BundleProductLink(
                savedClone,
                oldLink.getProduct(),
                oldLink.isMainAccount(),
                oldLink.isMandatory()
            );
            newLink.setBankId(bankId);
            bundleProductLinkRepository.save(newLink);
        });

        return savedClone.getId();
    }

    @Transactional
    public void archiveBundle(Long bundleId) {
        ProductBundle bundle = getBundle(bundleId);
        bundle.setStatus(ProductBundle.BundleStatus.ARCHIVED);
        if (bundle.getExpiryDate() == null || bundle.getExpiryDate().isAfter(LocalDate.now())) {
            bundle.setExpiryDate(LocalDate.now());
        }
        productBundleRepository.save(bundle);
    }

    private ProductBundle saveBundleWithLinks(ProductBundleRequest dto, String bankId, ProductBundle.BundleStatus status) {
        // 1. Structural Validation: Check Main Account constraint
        if (dto.getItems() != null) {
            long mainCount = dto.getItems().stream()
                    .filter(ProductBundleRequest.BundleItemRequest::isMainAccount)
                    .count();
            if (mainCount > 1) {
                throw new IllegalArgumentException("A bundle can only have 1 Main Account item.");
            }
        }

        // 2. Initialize Bundle
        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(bankId);
        bundle.setCode(dto.getCode());
        bundle.setName(dto.getName());
        bundle.setDescription(dto.getDescription());
        bundle.setEligibilitySegment(dto.getEligibilitySegment());
        bundle.setActivationDate(dto.getActivationDate());
        bundle.setExpiryDate(dto.getExpiryDate());
        bundle.setStatus(status);

        ProductBundle savedBundle = productBundleRepository.save(bundle);

        // 3. Process Items and Enforce Constraints
        if (dto.getItems() != null) {
            // We use a list to track already-processed products for cross-compatibility checks
            List<Product> processedProducts = new ArrayList<>();

            dto.getItems().forEach(item -> {
                // Constraint A: Check if product is already in another bundle (if strict mode active)
                constraintService.validateProductCanBeBundled(item.getProductId());

                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new NotFoundException("Product not found: " + item.getProductId()));

                // Constraint B: Check if this product is commercially compatible with others in this bundle
                constraintService.validateCategoryCompatibility(product, List.copyOf(processedProducts));

                // Create Link
                BundleProductLink link = new BundleProductLink(savedBundle, product, item.isMainAccount(), item.isMandatory());
                link.setBankId(bankId);
                bundleProductLinkRepository.save(link);

                // Add to processed list for the next iteration's compatibility check
                processedProducts.add(product);
            });
        }
        return savedBundle;
    }

    private ProductBundle getBundle(Long id) {
        return productBundleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bundle not found: " + id));
    }
}