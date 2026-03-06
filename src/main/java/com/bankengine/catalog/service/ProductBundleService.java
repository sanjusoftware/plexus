package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductBundleMapper;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.dto.ProductPricingDto;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.model.BundlePricingLink;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.service.PricingComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductBundleService extends BaseService {

    private final ProductBundleRepository productBundleRepository;
    private final ProductRepository productRepository;
    private final CatalogConstraintService constraintService;
    private final PricingComponentService pricingComponentService;
    private final ProductBundleMapper bundleMapper;

    @Override
    protected <T extends VersionableEntity> void handleTemporalVersioning(T newEntity, T oldEntity, VersionRequest request) {
        if (newEntity instanceof ProductBundle bundle && oldEntity instanceof ProductBundle oldBundle) {
            LocalDate date = request.getNewActivationDate() != null
                    ? request.getNewActivationDate()
                    : oldBundle.getActivationDate();
            bundle.setActivationDate(date);
        }
    }

    // --- READ OPERATIONS ---

    @Transactional(readOnly = true)
    public ProductBundleResponse getBundleResponseById(Long id) {
        ProductBundle bundle = getByIdSecurely(productBundleRepository, id, "Bundle");
        return bundleMapper.toResponse(bundle);
    }

    public ProductBundle getProductBundleByCode(String code, Integer version) {
        return getByCodeAndVersionSecurely(productBundleRepository, code, version, "Bundle");
    }

    // --- WRITE OPERATIONS ---

    @Transactional
    public ProductBundleResponse createBundle(ProductBundleRequest request) {
        validateNewVersionable(productBundleRepository, request.getName(), request.getCode());

        // Use Mapper to create DRAFT entity
        ProductBundle bundle = bundleMapper.toEntity(request);
        bundle.setBankId(getCurrentBankId());
        bundle.setStatus(VersionableEntity.EntityStatus.DRAFT);
        bundle.setVersion(1);

        // Link and validate products
        if (request.getProducts() != null) {
            attachProductsToBundle(bundle, request.getProducts());
        }

        // Handle Bundle-Level Pricing
        if (request.getPricing() != null) {
            syncBundlePricingInternal(bundle, request.getPricing());
        }

        return bundleMapper.toResponse(productBundleRepository.save(bundle));
    }

    @Transactional
    public ProductBundleResponse updateBundle(Long id, ProductBundleRequest dto) {
        ProductBundle bundle = getByIdSecurely(productBundleRepository, id, "Bundle");
        validateDraft(bundle);

        // Partial updates for metadata
        if (dto.getName() != null) bundle.setName(dto.getName());
        if (dto.getDescription() != null) bundle.setDescription(dto.getDescription());
        if (dto.getTargetCustomerSegments() != null) bundle.setTargetCustomerSegments(dto.getTargetCustomerSegments());

        // Sync products if the list is provided in the request
        if (dto.getProducts() != null) {
            bundle.getContainedProducts().clear();
            attachProductsToBundle(bundle, dto.getProducts());
        }

        // Sync pricing if the list is provided in the request
        if (dto.getPricing() != null) {
            syncBundlePricingInternal(bundle, dto.getPricing());
        }

        return bundleMapper.toResponse(productBundleRepository.save(bundle));
    }

    /**
     * Scenario: Versioning/Upgrading an existing bundle (Deep Clone).
     */
    @Transactional
    public ProductBundleResponse versionBundle(Long oldBundleId, VersionRequest request) {
        ProductBundle source = getByIdSecurely(productBundleRepository, oldBundleId, "Bundle");
        ProductBundle newVersion = bundleMapper.clone(source);

        prepareNewVersion(newVersion, source, request, productBundleRepository);

        cloneProductLinks(source, newVersion);
        cloneBundlePricingLinks(source, newVersion); // Ensures pricing is deep-copied

        return bundleMapper.toResponse(productBundleRepository.save(newVersion));
    }

    /**
     * Scenario: Activating a DRAFT bundle for production use.
     */
    @Transactional
    public ProductBundleResponse activateBundle(Long bundleId) {
        ProductBundle bundle = getByIdSecurely(productBundleRepository, bundleId, "Bundle");
        validateDraft(bundle);

        if (bundle.getContainedProducts().isEmpty()) {
            throw new IllegalStateException("Cannot activate a bundle with no products.");
        }

        validateMainAccountConstraint(bundle.getContainedProducts());

        // Ensure all constituent products are ACTIVE
        List<String> inactiveProducts = bundle.getContainedProducts().stream()
                .map(BundleProductLink::getProduct)
                .filter(p -> !p.getStatus().equals(VersionableEntity.EntityStatus.ACTIVE))
                .map(Product::getName)
                .toList();

        if (!inactiveProducts.isEmpty()) {
            throw new IllegalStateException("Cannot activate bundle. Products must be ACTIVE: " +
                    String.join(", ", inactiveProducts));
        }

        if (bundle.getActivationDate() == null || bundle.getActivationDate().isBefore(LocalDate.now())) {
            bundle.setActivationDate(LocalDate.now());
        }

        bundle.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        return bundleMapper.toResponse(productBundleRepository.save(bundle));
    }

    @Transactional
    public void archiveBundle(Long bundleId) {
        ProductBundle bundle = getByIdSecurely(productBundleRepository, bundleId, "Bundle");
        bundle.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
        if (bundle.getExpiryDate() == null || bundle.getExpiryDate().isAfter(LocalDate.now())) {
            bundle.setExpiryDate(LocalDate.now());
        }
        productBundleRepository.save(bundle);
    }

    private void syncBundlePricingInternal(ProductBundle bundle, List<ProductPricingDto> pricingDtos) {
        Map<Long, BundlePricingLink> existingMap = bundle.getBundlePricingLinks().stream()
                .collect(Collectors.toMap(l -> l.getPricingComponent().getId(), l -> l));

        Set<Long> incomingIds = pricingDtos.stream()
                .map(ProductPricingDto::getPricingComponentId)
                .collect(Collectors.toSet());

        // Orphan Removal
        bundle.getBundlePricingLinks().removeIf(link -> !incomingIds.contains(link.getPricingComponent().getId()));

        for (ProductPricingDto dto : pricingDtos) {
            if (existingMap.containsKey(dto.getPricingComponentId())) {
                mapBundlePricingFields(existingMap.get(dto.getPricingComponentId()), dto);
            } else {
                PricingComponent comp = pricingComponentService.getPricingComponentById(dto.getPricingComponentId());
                BundlePricingLink link = new BundlePricingLink();
                link.setProductBundle(bundle);
                link.setPricingComponent(comp);
                link.setBankId(bundle.getBankId());
                mapBundlePricingFields(link, dto);
                bundle.getBundlePricingLinks().add(link);
            }
        }
    }

    private void mapBundlePricingFields(BundlePricingLink link, ProductPricingDto dto) {
        // Equality checks for performance/audit
        if (!Objects.equals(link.getFixedValue(), dto.getFixedValue())) {
            link.setFixedValue(dto.getFixedValue());
        }
        if (!Objects.equals(link.getFixedValueType(), dto.getFixedValueType())) {
            link.setFixedValueType(dto.getFixedValueType());
        }
        if (link.isUseRulesEngine() != dto.isUseRulesEngine()) {
            link.setUseRulesEngine(dto.isUseRulesEngine());
        }

        // Validations: If dates are provided, they must be logical
        if (!Objects.equals(link.getEffectiveDate(), dto.getEffectiveDate())) {
            if (dto.getEffectiveDate() != null && dto.getEffectiveDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Effective date cannot be in the past.");
            }
            link.setEffectiveDate(dto.getEffectiveDate());
        }

        if (!Objects.equals(link.getExpiryDate(), dto.getExpiryDate())) {
            if (dto.getExpiryDate() != null && link.getEffectiveDate() != null
                    && dto.getExpiryDate().isBefore(link.getEffectiveDate())) {
                throw new IllegalArgumentException("Expiry date must be after the effective date.");
            }
            link.setExpiryDate(dto.getExpiryDate());
        }
    }

    private void attachProductsToBundle(ProductBundle bundle, List<ProductBundleRequest.BundleProduct> productDtos) {
        if (productDtos == null || productDtos.isEmpty()) return;

        // Validation: Main account check
        long mainCount = productDtos.stream().filter(ProductBundleRequest.BundleProduct::isMainAccount).count();
        if (mainCount > 1) throw new IllegalArgumentException("A bundle can only have 1 Main Account item.");

        List<Product> processedProducts = new ArrayList<>();

        productDtos.forEach(item -> {
            Product product = getByIdSecurely(productRepository, item.getProductId(), "Product");

            constraintService.validateProductCanBeBundled(item.getProductId());
            constraintService.validateCategoryCompatibility(product, List.copyOf(processedProducts));

            BundleProductLink link = bundleMapper.toLink(item);
            link.setProductBundle(bundle);
            link.setProduct(product);
            link.setBankId(getCurrentBankId());

            bundle.getContainedProducts().add(link);
            processedProducts.add(product);
        });
    }

    private void cloneProductLinks(ProductBundle source, ProductBundle target) {
        source.getContainedProducts().forEach(oldLink -> {
            BundleProductLink newLink = new BundleProductLink();
            newLink.setProductBundle(target);
            newLink.setProduct(oldLink.getProduct());
            newLink.setMainAccount(oldLink.isMainAccount());
            newLink.setMandatory(oldLink.isMandatory());
            newLink.setBankId(target.getBankId());
            target.getContainedProducts().add(newLink);
        });
    }

    private void cloneBundlePricingLinks(ProductBundle source, ProductBundle target) {
        source.getBundlePricingLinks().forEach(oldLink -> {
            BundlePricingLink newLink = bundleMapper.clonePricing(oldLink);
            newLink.setProductBundle(target);
            newLink.setBankId(target.getBankId());
            target.getBundlePricingLinks().add(newLink);
        });
    }

    private void validateMainAccountConstraint(List<BundleProductLink> links) {
        long mainAccountCount = links.stream().filter(BundleProductLink::isMainAccount).count();
        if (mainAccountCount != 1) {
            throw new IllegalStateException("Bundle must have exactly 1 Main Account.");
        }
    }
}