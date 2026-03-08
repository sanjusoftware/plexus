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
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.service.PricingComponentService;
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
    private final EntityManager entityManager;

    /**
     * Hook implementation to handle Product-specific temporal fields (activationDate).
     */
    @Override
    protected <T extends VersionableEntity> void handleTemporalVersioning(T newEntity, T oldEntity, VersionRequest request) {
        if (newEntity instanceof Product product && oldEntity instanceof Product oldProduct) {
            LocalDate date = request.getNewActivationDate() != null
                    ? request.getNewActivationDate()
                    : oldProduct.getActivationDate();
            product.setActivationDate(date);
        }
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
        validateNewVersionable(productRepository, requestDto.getName(), requestDto.getCode());

        ProductType productType = requestDto.getProductTypeCode() != null
                ? getProductTypeByCode(requestDto.getProductTypeCode())
                : getProductTypeById(requestDto.getProductTypeId());
        Product product = productMapper.toEntity(requestDto, productType);
        product.setBankId(getCurrentBankId());
        product.setStatus(VersionableEntity.EntityStatus.DRAFT);
        product.setVersion(1);

        if (requestDto.getFeatures() != null) syncFeaturesInternal(product, requestDto.getFeatures());
        if (requestDto.getPricing() != null) syncPricingInternal(product, requestDto.getPricing());

        return productMapper.toResponse(productRepository.save(product));
    }

    private void sanitizeRequest(ProductRequest requestDto) {
        requestDto.setCode(CodeGeneratorUtil.sanitizeCode(requestDto.getCode()));
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest dto) {
        sanitizeRequest(dto);
        Product product = getProductEntityById(productId);
        validateDraft(product);

        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getCategory() != null) product.setCategory(dto.getCategory());

        if (dto.getExpiryDate() != null) {
            validateExpirationDate(product, dto.getExpiryDate());
            product.setExpiryDate(dto.getExpiryDate());
        }

        if (dto.getFeatures() != null) syncFeaturesInternal(product, dto.getFeatures());
        if (dto.getPricing() != null) syncPricingInternal(product, dto.getPricing());

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

        // 3. Deep clone associated links
        cloneFeaturesInternal(sourceProduct, newProduct, getCurrentBankId());
        clonePricingInternal(sourceProduct, newProduct, getCurrentBankId());

        // 4. SAVE BOTH (Or ensure sourceProduct is flushed)
        productRepository.save(sourceProduct);
        Product saved = productRepository.save(newProduct);

        productRepository.flush();
        entityManager.refresh(saved);

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

    private void syncFeaturesInternal(Product product, List<ProductFeatureDto> incomingDtos) {
        Map<Long, ProductFeatureLink> existingMap = product.getProductFeatureLinks().stream()
                .collect(Collectors.toMap(l -> l.getFeatureComponent().getId(), l -> l));

        Set<Long> incomingIds = incomingDtos.stream()
                .map(ProductFeatureDto::getFeatureComponentId)
                .collect(Collectors.toSet());

        product.getProductFeatureLinks().removeIf(link -> !incomingIds.contains(link.getFeatureComponent().getId()));

        for (ProductFeatureDto dto : incomingDtos) {
            FeatureComponent component = featureComponentService.getFeatureComponentById(dto.getFeatureComponentId());
            validateFeatureValue(dto.getFeatureValue(), component.getDataType());

            if (existingMap.containsKey(dto.getFeatureComponentId())) {
                ProductFeatureLink existingLink = existingMap.get(dto.getFeatureComponentId());
                // Equality check prevents unnecessary setter calls
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

    private void syncPricingInternal(Product product, List<ProductPricingDto> incomingDtos) {
        Map<Long, ProductPricingLink> existingMap = product.getProductPricingLinks().stream()
                .collect(Collectors.toMap(l -> l.getPricingComponent().getId(), l -> l));

        Set<Long> incomingIds = incomingDtos.stream()
                .map(ProductPricingDto::getPricingComponentId)
                .collect(Collectors.toSet());

        product.getProductPricingLinks().removeIf(link -> !incomingIds.contains(link.getPricingComponent().getId()));

        for (ProductPricingDto dto : incomingDtos) {
            if (existingMap.containsKey(dto.getPricingComponentId())) {
                mapPricingFields(existingMap.get(dto.getPricingComponentId()), dto);
            } else {
                PricingComponent comp = pricingComponentService.getPricingComponentById(dto.getPricingComponentId());
                ProductPricingLink link = new ProductPricingLink();
                link.setProduct(product);
                link.setPricingComponent(comp);
                link.setBankId(product.getBankId());
                mapPricingFields(link, dto);
                product.getProductPricingLinks().add(link);
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

    private void mapPricingFields(ProductPricingLink link, ProductPricingDto dto) {
        if (!Objects.equals(link.getFixedValue(), dto.getFixedValue())) {
            link.setFixedValue(dto.getFixedValue());
        }
        if (!Objects.equals(link.getFixedValueType(), dto.getFixedValueType())) {
            link.setFixedValueType(dto.getFixedValueType());
        }
        if (link.isUseRulesEngine() != dto.isUseRulesEngine()) {
            link.setUseRulesEngine(dto.isUseRulesEngine());
        }
        if (!Objects.equals(link.getTargetComponentCode(), dto.getTargetComponentCode())) {
            link.setTargetComponentCode(dto.getTargetComponentCode());
        }

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

    private void validateFeatureValue(String value, FeatureComponent.DataType dataType) {
        if (value == null || value.trim().isEmpty()) {
            if (dataType != FeatureComponent.DataType.STRING) {
                throw new IllegalArgumentException("Feature value required for type: " + dataType);
            }
            return;
        }

        switch (dataType) {
            case INTEGER -> {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value '" + value + "' must be an INTEGER.");
                }
            }
            case DECIMAL -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value '" + value + "' must be a DECIMAL.");
                }
            }
            case BOOLEAN -> {
                if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                    throw new IllegalArgumentException("Value '" + value + "' must be 'true' or 'false'.");
                }
            }
            case STRING -> {
            }
            default -> throw new IllegalArgumentException("Unsupported data type: " + dataType);
        }
    }
}
