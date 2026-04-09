package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureLinkMapper;
import com.bankengine.catalog.converter.PricingLinkMapper;
import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.catalog.specification.ProductSpecification;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.service.BaseService;
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.web.dto.Violation;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService extends BaseService {

    private final ProductRepository productRepository;
    private final ProductTypeRepository productTypeRepository;
    private final FeatureComponentService featureComponentService;
    private final PricingComponentService pricingComponentService;
    private final ProductMapper productMapper;
    private final FeatureLinkMapper featureLinkMapper;
    private final PricingLinkMapper pricingLinkMapper;
    private final com.bankengine.catalog.repository.BundleProductLinkRepository bundleProductLinkRepository;
    private final EntityManager entityManager;

    /**
     * Hook implementation to handle Product-specific temporal fields (activationDate).
     */
    @Override
    protected <T extends VersionableEntity> void handleTemporalVersioning(T newEntity, T oldEntity, VersionRequest request) {
        // BaseService handles common activationDate and expiryDate
    }

    // --- READ OPERATIONS ---

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(ProductSearchRequest criteria) {
        Specification<Product> specification = ProductSpecification.filterBy(criteria);
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortBy());
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        return productRepository.findAll(specification, pageable).map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductResponseById(Long productId) {
        return productMapper.toResponse(getProductEntityById(productId));
    }

    // --- WRITE OPERATIONS ---

    @Transactional
    public ProductResponse createProduct(ProductRequest requestDto) {
        sanitizeRequest(requestDto);

        List<Violation> violations = new java.util.ArrayList<>();

        try {
            validateNewVersionable(productRepository, requestDto.getCode());
        } catch (IllegalArgumentException e) {
            violations.add(Violation.builder()
                    .field("code")
                    .reason(e.getMessage())
                    .severity(Violation.Severity.ERROR)
                    .build());
        }

        // Example Warning Quota Check
        if ("RETAIL".equalsIgnoreCase(requestDto.getCategory())) {
            violations.add(Violation.builder()
                    .field("category")
                    .reason("Category 'RETAIL' is approaching its quota for this bank.")
                    .severity(Violation.Severity.WARNING)
                    .build());
        }

        ProductType productType = null;
        try {
            productType = getProductTypeByCode(requestDto.getProductTypeCode());
        } catch (NotFoundException e) {
            violations.add(Violation.builder()
                    .field("productTypeCode")
                    .reason(e.getMessage())
                    .severity(Violation.Severity.ERROR)
                    .build());
        }

        Product product = null;
        if (productType != null) {
            product = productMapper.toEntity(requestDto, productType);
            product.setBankId(getCurrentBankId());
            product.setStatus(VersionableEntity.EntityStatus.DRAFT);
            product.setVersion(1);

            if (requestDto.getFeatures() != null) syncFeaturesInternal(product, requestDto.getFeatures(), violations);
            if (requestDto.getPricing() != null) syncPricingInternal(product, requestDto.getPricing(), violations);
        }

        if (!violations.isEmpty()) {
            throw new ValidationException("BUSINESS_RULE_VIOLATION", "Product creation failed due to multiple conflicts.", violations);
        }

        return productMapper.toResponse(productRepository.save(product));
    }

    private void sanitizeRequest(ProductRequest requestDto) {
        requestDto.setCode(CodeGeneratorUtil.sanitizeAsCode(requestDto.getCode()));
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest dto) {
        sanitizeRequest(dto);
        Product product = getProductEntityById(productId);
        validateDraft(product);

        List<Violation> violations = new java.util.ArrayList<>();

        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getCategory() != null) product.setCategory(dto.getCategory());

        if (dto.getExpiryDate() != null) {
            validateExpirationDate(product, dto.getExpiryDate());
            product.setExpiryDate(dto.getExpiryDate());
        }

        if (dto.getFeatures() != null) syncFeaturesInternal(product, dto.getFeatures(), violations);
        if (dto.getPricing() != null) syncPricingInternal(product, dto.getPricing(), violations);

        if (!violations.isEmpty()) {
            throw new ValidationException("BUSINESS_RULE_VIOLATION", "Product update failed due to multiple conflicts.", violations);
        }

        return productMapper.toResponse(productRepository.save(product));
    }

    // --- LIFECYCLE OPERATIONS ---

    @Transactional
    public ProductResponse cloneProduct(Long sourceProductId, VersionRequest requestDto) {
        Product sourceProduct = getProductEntityById(sourceProductId);

        // 1. Create a clean clone
        Product newProduct = productMapper.createNewVersionFrom(sourceProduct, requestDto);

        // 2. This modifies BOTH newProduct and sourceProduct (archiving it if same code)
        prepareNewVersion(newProduct, sourceProduct, requestDto, productRepository);

        // Clear links in the clone to avoid duplicate IDs (only if prepareNewVersion didn't throw)
        if (newProduct.getProductFeatureLinks() != null) newProduct.getProductFeatureLinks().clear();
        if (newProduct.getProductPricingLinks() != null) newProduct.getProductPricingLinks().clear();
        if (newProduct.getBundleLinks() != null) newProduct.getBundleLinks().clear();

        // 3. Deep clone associated links
        cloneFeaturesInternal(sourceProduct, newProduct, getCurrentBankId());
        clonePricingInternal(sourceProduct, newProduct, getCurrentBankId());

        // 4. SAVE BOTH (Or ensure sourceProduct is flushed)
        productRepository.save(sourceProduct);
        Product saved = productRepository.save(newProduct);

        // Update bundle links if it was a revision of an ACTIVE product
        if (sourceProduct.isArchived() && saved.isActive()) {
            bundleProductLinkRepository.updateProductReference(sourceProduct.getId(), saved);
        }

        productRepository.flush();
        // Skip entityManager.refresh(saved) in some cases it might cause issues if not fully persisted in the current test context
        // Instead just return mapped response from saved entity
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse activateProduct(Long id, LocalDate activationDate) {
        Product product = getProductEntityById(id);
        validateDraft(product);

        product.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        if (activationDate != null) {
            product.setActivationDate(activationDate);
        } else if (product.getActivationDate() == null || product.getActivationDate().isBefore(LocalDate.now())) {
            product.setActivationDate(LocalDate.now());
        }

        // Auto-activate linked DRAFT components, propagating the product's activation date
        product.getProductPricingLinks().forEach(link -> {
            if (link.getEffectiveDate() == null) {
                link.setEffectiveDate(product.getActivationDate());
            }
            PricingComponent pc = link.getPricingComponent();
            if (pc.isDraft()) {
                pricingComponentService.activateComponent(pc.getId(), product.getActivationDate());
            }
        });

        product.getProductFeatureLinks().forEach(link -> {
            FeatureComponent fc = link.getFeatureComponent();
            if (fc.isDraft()) {
                featureComponentService.activateFeature(fc.getId(), product.getActivationDate());
            }
        });

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse deactivateProduct(Long id) {
        Product product = getProductEntityById(id);
        if (VersionableEntity.EntityStatus.INACTIVE.equals(product.getStatus())) {
            throw new IllegalStateException("Product is already inactive.");
        }

        product.setStatus(VersionableEntity.EntityStatus.INACTIVE);
        product.setExpiryDate(LocalDate.now());

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse extendProductExpiry(Long productId, LocalDate newExpiryDate) {
        Product product = getProductEntityById(productId);
        if (!product.isDraft() && !product.isActive()) {
            throw new IllegalStateException(
                    String.format("Cannot extend expiry for product in %s status.", product.getStatus()));
        }

        if (product.getExpiryDate() != null) {
            if (newExpiryDate.isBefore(product.getExpiryDate()) || newExpiryDate.isEqual(product.getExpiryDate())) {
                throw new IllegalArgumentException("New expiry date must be after the current expiry date.");
            }
        } else {
            // If there is no current expiry, at least ensure the new one is not in the past
            if (newExpiryDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("New expiry date must be in the future.");
            }
        }

        product.setExpiryDate(newExpiryDate);
        return productMapper.toResponse(productRepository.save(product));
    }

    // --- INTERNAL RECONCILIATION & CLONING ---

    private void syncFeaturesInternal(Product product, List<ProductFeatureDto> incomingDtos, List<Violation> violations) {
        Map<String, ProductFeatureLink> existingMap = product.getProductFeatureLinks().stream()
                .collect(Collectors.toMap(l -> l.getFeatureComponent().getCode(), l -> l));

        Set<String> incomingCodes = incomingDtos.stream()
                .map(ProductFeatureDto::getFeatureComponentCode)
                .collect(Collectors.toSet());

        product.getProductFeatureLinks().removeIf(link -> !incomingCodes.contains(link.getFeatureComponent().getCode()));

        for (int i = 0; i < incomingDtos.size(); i++) {
            ProductFeatureDto dto = incomingDtos.get(i);
            String fieldPrefix = "features[" + i + "].";
            FeatureComponent component = null;
            try {
                component = featureComponentService.getFeatureComponentByCode(dto.getFeatureComponentCode(), null);
            } catch (NotFoundException e) {
                // Fat DTO: Create new feature if it doesn't exist
                if (dto.getDataType() == null || dto.getFeatureName() == null) {
                    violations.add(Violation.builder()
                            .field(fieldPrefix + "featureComponentCode")
                            .reason("Feature component with code '" + dto.getFeatureComponentCode() + "' not found and required data for creation (dataType, featureName) is missing.")
                            .severity(Violation.Severity.ERROR)
                            .build());
                    continue;
                }
                FeatureComponentRequest createRequest = new FeatureComponentRequest();
                createRequest.setCode(dto.getFeatureComponentCode());
                createRequest.setName(dto.getFeatureName());
                createRequest.setDataType(dto.getDataType());
                FeatureComponentResponse created = featureComponentService.createFeature(createRequest);
                component = featureComponentService.getFeatureComponentById(created.getId());
            }

            if (component != null) {
                try {
                    validateDraft(component);
                } catch (IllegalStateException e) {
                    violations.add(Violation.builder()
                            .field(fieldPrefix + "featureComponentCode")
                            .reason(e.getMessage())
                            .severity(Violation.Severity.ERROR)
                            .build());
                }
                validateFeatureValue(dto.getFeatureValue(), component.getDataType(), fieldPrefix + "featureValue", violations);
            }

            if (component != null && existingMap.containsKey(dto.getFeatureComponentCode())) {
                ProductFeatureLink existingLink = existingMap.get(dto.getFeatureComponentCode());
                if (!Objects.equals(existingLink.getFeatureValue(), dto.getFeatureValue())) {
                    existingLink.setFeatureValue(dto.getFeatureValue());
                }
            } else {
                ProductFeatureLink link = new ProductFeatureLink();
                link.setProduct(product);
                link.setFeatureComponent(component);
                link.setFeatureValue(dto.getFeatureValue());
                link.setBankId(product.getBankId());
                product.getProductFeatureLinks().add(link);
            }
        }
    }

    private void syncPricingInternal(Product product, List<ProductPricingDto> incomingDtos, List<Violation> violations) {
        Map<String, ProductPricingLink> existingMap = product.getProductPricingLinks().stream()
                .collect(Collectors.toMap(l -> l.getPricingComponent().getCode(), l -> l));

        Set<String> incomingCodes = incomingDtos.stream()
                .map(ProductPricingDto::getPricingComponentCode)
                .collect(Collectors.toSet());

        product.getProductPricingLinks().removeIf(link -> !incomingCodes.contains(link.getPricingComponent().getCode()));

        for (int i = 0; i < incomingDtos.size(); i++) {
            ProductPricingDto dto = incomingDtos.get(i);
            String fieldPrefix = "pricing[" + i + "].";
            if (existingMap.containsKey(dto.getPricingComponentCode())) {
                mapPricingFields(existingMap.get(dto.getPricingComponentCode()), dto, fieldPrefix, violations);
            } else {
                PricingComponent comp = null;
                try {
                    comp = pricingComponentService.getPricingComponentByCode(dto.getPricingComponentCode(), null);
                    validateDraft(comp);
                } catch (NotFoundException e) {
                    violations.add(Violation.builder()
                            .field(fieldPrefix + "pricingComponentCode")
                            .reason(e.getMessage())
                            .severity(Violation.Severity.ERROR)
                            .build());
                } catch (IllegalStateException e) {
                    violations.add(Violation.builder()
                            .field(fieldPrefix + "pricingComponentCode")
                            .reason(e.getMessage())
                            .severity(Violation.Severity.ERROR)
                            .build());
                }

                if (comp != null) {
                    ProductPricingLink link = new ProductPricingLink();
                    link.setProduct(product);
                    link.setPricingComponent(comp);
                    link.setBankId(product.getBankId());
                    mapPricingFields(link, dto, fieldPrefix, violations);
                    product.getProductPricingLinks().add(link);
                }
            }
        }
    }

    private void cloneFeaturesInternal(Product source, Product target, String bankId) {
        List<ProductFeatureLink> clonedFeatures = source.getProductFeatureLinks().stream()
                .map(oldLink -> {
                    ProductFeatureLink newLink = featureLinkMapper.clone(oldLink);
                    newLink.setProduct(target);
                    newLink.setBankId(bankId);
                    return newLink;
                }).toList();
        target.getProductFeatureLinks().addAll(clonedFeatures);
    }

    private void clonePricingInternal(Product source, Product target, String bankId) {
        List<ProductPricingLink> clonedPricing = source.getProductPricingLinks().stream()
                .map(oldLink -> {
                    ProductPricingLink newLink = pricingLinkMapper.clone(oldLink);
                    newLink.setProduct(target);
                    newLink.setBankId(bankId);
                    return newLink;
                }).toList();
        target.getProductPricingLinks().addAll(clonedPricing);
    }

    // --- HELPERS ---

    private void mapPricingFields(ProductPricingLink link, ProductPricingDto dto, String fieldPrefix, List<Violation> violations) {
        if (!Objects.equals(link.getFixedValue(), dto.getFixedValue())) {
            // Example custom validation rule
            if (dto.getFixedValue() != null && dto.getFixedValue().compareTo(new java.math.BigDecimal("10000")) > 0) {
                violations.add(Violation.builder()
                        .field(fieldPrefix + "fixedValue")
                        .reason("Value exceeds the maximum allowed for this component type.")
                        .severity(Violation.Severity.ERROR)
                        .build());
            }
            link.setFixedValue(dto.getFixedValue());
        }
        if (!Objects.equals(link.getFixedValueType(), dto.getFixedValueType())) {
            link.setFixedValueType(dto.getFixedValueType());
        }
        if (link.isUseRulesEngine() != dto.isUseRulesEngine()) {
            link.setUseRulesEngine(dto.isUseRulesEngine());
        }
        if (!Objects.equals(link.getTargetComponentCode(), dto.getTargetComponentCode())) {
            // Requirement 18: Validate targetComponentCode
            if (dto.getTargetComponentCode() != null) {
                try {
                    PricingComponent target = pricingComponentService.getPricingComponentByCode(dto.getTargetComponentCode(), null);
                    if (target.isArchived() || target.isInActive()) {
                        violations.add(Violation.builder()
                                .field(fieldPrefix + "targetComponentCode")
                                .reason("Target component must be ACTIVE or DRAFT: " + dto.getTargetComponentCode())
                                .severity(Violation.Severity.ERROR)
                                .build());
                    }
                } catch (NotFoundException e) {
                    violations.add(Violation.builder()
                            .field(fieldPrefix + "targetComponentCode")
                            .reason(e.getMessage())
                            .severity(Violation.Severity.ERROR)
                            .build());
                }
            }
            link.setTargetComponentCode(dto.getTargetComponentCode());
        }

        if (!Objects.equals(link.getEffectiveDate(), dto.getEffectiveDate())) {
            if (dto.getEffectiveDate() != null && dto.getEffectiveDate().isBefore(LocalDate.now())) {
                violations.add(Violation.builder()
                        .field(fieldPrefix + "effectiveDate")
                        .reason("Effective date cannot be in the past.")
                        .severity(Violation.Severity.ERROR)
                        .build());
            }
            link.setEffectiveDate(dto.getEffectiveDate());
        }

        if (!Objects.equals(link.getExpiryDate(), dto.getExpiryDate())) {
            if (dto.getExpiryDate() != null && link.getEffectiveDate() != null
                    && dto.getExpiryDate().isBefore(link.getEffectiveDate())) {
                violations.add(Violation.builder()
                        .field(fieldPrefix + "expiryDate")
                        .reason("Expiry date must be after the effective date.")
                        .severity(Violation.Severity.ERROR)
                        .build());
            }
            link.setExpiryDate(dto.getExpiryDate());
        }
    }

    public Product getProductEntityById(Long id) {
        return getByIdSecurely(productRepository, id, "Product");
    }

    public Product getProductEntityByCode(String code, Integer version) {
        return getByCodeAndVersionSecurely(productRepository, code, version, "Product");
    }

    private ProductType getProductTypeById(Long id) {
        return getByIdSecurely(productTypeRepository, id, "Product Type");
    }

    private ProductType getProductTypeByCode(String code) {
        return productTypeRepository.findByBankIdAndCode(getCurrentBankId(), code)
                .orElseThrow(() -> new NotFoundException("Product Type not found with code: " + code));
    }

    private void validateExpirationDate(Product product, LocalDate newDate) {
        if (product.getExpiryDate() != null && newDate.isBefore(product.getExpiryDate())) {
            throw new IllegalArgumentException("New expiration date cannot be before current expiration date.");
        }
        if (newDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future.");
        }
    }

    private void validateFeatureValue(String value, FeatureComponent.DataType dataType, String fieldPath, List<Violation> violations) {
        if (value == null || value.trim().isEmpty()) {
            if (dataType != FeatureComponent.DataType.STRING) {
                violations.add(Violation.builder()
                        .field(fieldPath)
                        .reason("Feature value required for type: " + dataType)
                        .severity(Violation.Severity.ERROR)
                        .build());
            }
            return;
        }

        switch (dataType) {
            case INTEGER -> {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    violations.add(Violation.builder()
                            .field(fieldPath)
                            .reason("Value '" + value + "' must be an INTEGER.")
                            .severity(Violation.Severity.ERROR)
                            .build());
                }
            }
            case DECIMAL -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    violations.add(Violation.builder()
                            .field(fieldPath)
                            .reason("Value '" + value + "' must be a DECIMAL.")
                            .severity(Violation.Severity.ERROR)
                            .build());
                }
            }
            case BOOLEAN -> {
                if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                    violations.add(Violation.builder()
                            .field(fieldPath)
                            .reason("Value '" + value + "' must be 'true' or 'false'.")
                            .severity(Violation.Severity.ERROR)
                            .build());
                }
            }
            case STRING -> {
            }
            default -> {
                violations.add(Violation.builder()
                        .field(fieldPath)
                        .reason("Unsupported data type: " + dataType)
                        .severity(Violation.Severity.ERROR)
                        .build());
            }
        }
    }
}
