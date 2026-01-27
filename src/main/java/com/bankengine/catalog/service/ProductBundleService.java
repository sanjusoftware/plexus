package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.service.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductBundleService extends BaseService {

    private final ProductBundleRepository productBundleRepository;
    private final ProductRepository productRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;
    private final CatalogConstraintService constraintService;

    @Transactional
    public Long createBundle(ProductBundleRequest dto) {
        return saveBundleWithLinks(dto).getId();
    }

    @Transactional
    public void activateBundle(Long bundleId) {
        ProductBundle bundle = getByIdSecurely(productBundleRepository, bundleId, "Bundle");

        if (bundle.getStatus() != ProductBundle.BundleStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT bundles can be activated. Current: " + bundle.getStatus());
        }

        if (bundle.getContainedProducts().isEmpty()) {
            throw new IllegalStateException("Cannot activate a bundle with no products.");
        }

        // Logic check for business integrity
        validateMainAccountConstraint(bundle.getContainedProducts());

        // Validate Product States (The products are already filtered by bankId due to Hibernate Filters)
        List<String> inactiveProducts = bundle.getContainedProducts().stream()
                .map(BundleProductLink::getProduct)
                .filter(p -> !"ACTIVE".equalsIgnoreCase(p.getStatus()))
                .map(Product::getName)
                .toList();

        if (!inactiveProducts.isEmpty()) {
            throw new IllegalStateException("Cannot activate bundle. Products must be ACTIVE: " +
                    String.join(", ", inactiveProducts));
        }

        if (bundle.getActivationDate().isBefore(LocalDate.now())) {
            bundle.setActivationDate(LocalDate.now());
        }

        bundle.setStatus(ProductBundle.BundleStatus.ACTIVE);
        productBundleRepository.save(bundle);
    }

    @Transactional
    public Long updateBundle(Long oldBundleId, ProductBundleRequest dto) {
        ProductBundle oldBundle = getByIdSecurely(productBundleRepository, oldBundleId, "Bundle");

        // 1. Mark as archived
        oldBundle.setStatus(ProductBundle.BundleStatus.ARCHIVED);
        oldBundle.setExpiryDate(LocalDate.now());
        productBundleRepository.saveAndFlush(oldBundle);

        // 2. Create the new version
        return saveBundleWithLinks(dto).getId();
    }

    @Transactional
    public Long cloneBundle(Long bundleId, String newName) {
        ProductBundle source = getByIdSecurely(productBundleRepository, bundleId, "Bundle");
        validateMainAccountConstraint(source.getContainedProducts());
        String bankId = getCurrentBankId();

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
        ProductBundle bundle = getByIdSecurely(productBundleRepository, bundleId, "Bundle");
        bundle.setStatus(ProductBundle.BundleStatus.ARCHIVED);
        // Ensure the expiry date is set to now if it wasn't already expired
        if (bundle.getExpiryDate() == null || bundle.getExpiryDate().isAfter(LocalDate.now())) {
            bundle.setExpiryDate(LocalDate.now());
        }
        productBundleRepository.save(bundle);
    }

    private ProductBundle saveBundleWithLinks(ProductBundleRequest dto) {
        // 1. Structural Validation: Check Main Account constraint
        if (dto.getItems() != null) {
            long mainCount = dto.getItems().stream()
                    .filter(ProductBundleRequest.BundleItemRequest::isMainAccount)
                    .count();
            if (mainCount > 1) {
                throw new IllegalArgumentException("A bundle can only have 1 Main Account item.");
            }
        }

        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(getCurrentBankId());
        bundle.setCode(dto.getCode());
        bundle.setName(dto.getName());
        bundle.setDescription(dto.getDescription());
        bundle.setEligibilitySegment(dto.getEligibilitySegment());
        bundle.setActivationDate(dto.getActivationDate());
        bundle.setExpiryDate(dto.getExpiryDate());
        bundle.setStatus(ProductBundle.BundleStatus.DRAFT);

        ProductBundle savedBundle = productBundleRepository.save(bundle);

        if (dto.getItems() != null) {
            List<Product> processedProducts = new ArrayList<>();

            dto.getItems().forEach(item -> {
                // SECURE: Fetch product using our secure pattern.
                // If the productId belongs to another bank, this throws 404.
                Product product = getByIdSecurely(productRepository, item.getProductId(), "Product");

                constraintService.validateProductCanBeBundled(item.getProductId());
                constraintService.validateCategoryCompatibility(product, List.copyOf(processedProducts));

                BundleProductLink link = new BundleProductLink(savedBundle, product, item.isMainAccount(), item.isMandatory());
                link.setBankId(getCurrentBankId());
                bundleProductLinkRepository.save(link);

                processedProducts.add(product);
            });
        }
        return savedBundle;
    }

    private void validateMainAccountConstraint(List<BundleProductLink> links) {
        long mainAccountCount = links.stream()
                .filter(BundleProductLink::isMainAccount)
                .count();
        if (mainAccountCount > 1) {
            throw new IllegalStateException("Data Integrity Error: Bundle has more than 1 Main Account.");
        }
    }
}