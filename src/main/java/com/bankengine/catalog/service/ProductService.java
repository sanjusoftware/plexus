package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.catalog.specification.ProductSpecification;
import com.bankengine.common.service.BaseService;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.pricing.service.PricingComponentService;
import jakarta.persistence.EntityManager;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService extends BaseService {

    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository featureLinkRepository;
    private final FeatureComponentService featureComponentService;
    private final ProductTypeRepository productTypeRepository;
    private final EntityManager entityManager;
    private final ProductPricingLinkRepository pricingLinkRepository;
    private final PricingComponentService pricingComponentService;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository featureLinkRepository,
                          FeatureComponentService featureComponentService, ProductTypeRepository productTypeRepository,
                          EntityManager entityManager, ProductPricingLinkRepository pricingLinkRepository,
                          PricingComponentService pricingComponentService, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.featureLinkRepository = featureLinkRepository;
        this.featureComponentService = featureComponentService;
        this.productTypeRepository = productTypeRepository;
        this.entityManager = entityManager;
        this.pricingLinkRepository = pricingLinkRepository;
        this.pricingComponentService = pricingComponentService;
        this.productMapper = productMapper;
    }

    /**
     * Searches and filters products based on criteria, returning paginated results.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(ProductSearchRequest criteria) {
        Specification<Product> specification = ProductSpecification.filterBy(criteria);

        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortBy());
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        Page<Product> productPage = productRepository.findAll(specification, pageable);
        return productPage.map(productMapper::toResponse);
    }

    /**
     * Synchronizes the features linked to a product based on the provided list.
     */
    @Transactional
    public ProductResponse syncProductFeatures(Long productId, List<ProductFeature> syncDtos) {
        Product product = getProductEntityById(productId);
        List<ProductFeatureLink> currentLinks = featureLinkRepository.findByProductId(productId);

        Map<Long, ProductFeatureLink> currentLinksMap = currentLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getFeatureComponent().getId(),
                        link -> link
                ));

        Set<Long> incomingFeatureIds = syncDtos.stream()
                .map(ProductFeature::getFeatureComponentId)
                .collect(Collectors.toSet());

        List<ProductFeatureLink> linksToDelete = currentLinks.stream()
                .filter(link -> !incomingFeatureIds.contains(link.getFeatureComponent().getId()))
                .collect(Collectors.toList());

        featureLinkRepository.deleteAll(linksToDelete);
        featureLinkRepository.flush();

        for (ProductFeature dto : syncDtos) {
            Long featureId = dto.getFeatureComponentId();
            FeatureComponent component = featureComponentService.getFeatureComponentById(featureId);
            validateFeatureValue(dto.getFeatureValue(), component.getDataType());

            if (currentLinksMap.containsKey(featureId)) {
                ProductFeatureLink existingLink = currentLinksMap.get(featureId);
                if (!existingLink.getFeatureValue().equals(dto.getFeatureValue())) {
                    existingLink.setFeatureValue(dto.getFeatureValue());
                    featureLinkRepository.save(existingLink);
                }
            } else {
                ProductFeatureLink newLink = new ProductFeatureLink();
                newLink.setProduct(product);
                newLink.setFeatureComponent(component);
                newLink.setFeatureValue(dto.getFeatureValue());
                newLink.setBankId(product.getBankId());
                featureLinkRepository.save(newLink);
            }
        }
        featureLinkRepository.flush();
        entityManager.clear();

        Product updatedProduct = getProductEntityById(productId);
        return productMapper.toResponse(updatedProduct);
    }

    /**
     * Synchronizes the pricing components linked to a product.
     */
    @Transactional
    public ProductResponse syncProductPricing(Long productId, List<ProductPricing> syncDtos) {
        Product product = getProductEntityById(productId);

        Set<Long> incomingIds = syncDtos.stream()
                .map(ProductPricing::getPricingComponentId)
                .collect(Collectors.toSet());

        product.getProductPricingLinks().removeIf(link ->
                !incomingIds.contains(link.getPricingComponent().getId()));

        Set<Long> existingIds = product.getProductPricingLinks().stream()
                .map(link -> link.getPricingComponent().getId())
                .collect(Collectors.toSet());

        for (ProductPricing dto : syncDtos) {
            if (!existingIds.contains(dto.getPricingComponentId())) {
                PricingComponent component = pricingComponentService.getPricingComponentById(dto.getPricingComponentId());
                product.getProductPricingLinks().add(createNewLink(dto, product, component));
            }
        }

        productRepository.save(product);
        productRepository.flush();
        entityManager.clear();

        return getProductResponseById(productId);
    }

    private static ProductPricingLink createNewLink(ProductPricing dto, Product product, PricingComponent component) {
        ProductPricingLink newLink = new ProductPricingLink();
        newLink.setProduct(product);
        newLink.setPricingComponent(component);
        newLink.setBankId(product.getBankId());
        newLink.setFixedValue(dto.getFixedValue());
        newLink.setFixedValueType(dto.getFixedValueType());
        newLink.setUseRulesEngine(dto.isUseRulesEngine());
        newLink.setTargetComponentCode(dto.getTargetComponentCode());
        newLink.setEffectiveDate(dto.getEffectiveDate());
        newLink.setExpiryDate(dto.getExpiryDate());
        return newLink;
    }

    /**
     * Creates a new Product using the Mapper to ensure default statuses and marketing fields are set.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest requestDto) {
        ProductType productType = getProductTypeById(requestDto.getProductTypeId());

        Product product = productMapper.toEntity(requestDto, productType);
        product.setBankId(getCurrentBankId());

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Retrieves Product by ID and converts it to a Response DTO.
     */
    @Transactional
    public ProductResponse getProductResponseById(Long productId) {
        Product product = getProductEntityById(productId);
        return productMapper.toResponse(product);
    }

    /**
     * Links a FeatureComponent to a Product with a specific value.
     */
    @Transactional
    public ProductResponse linkFeatureToProduct(Long productId, ProductFeature dto) {
        Product product = getProductEntityById(productId);
        FeatureComponent component = featureComponentService.getFeatureComponentById(dto.getFeatureComponentId());

        validateFeatureValue(dto.getFeatureValue(), component.getDataType());

        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(product);
        link.setFeatureComponent(component);
        link.setFeatureValue(dto.getFeatureValue());
        link.setBankId(product.getBankId());
        featureLinkRepository.save(link);
        return getProductResponseById(product.getId());
    }

    public Product getProductEntityById(Long id) {
        return getByIdSecurely(productRepository, id, "Product");
    }

    private ProductType getProductTypeById(Long id) {
        return getByIdSecurely(productTypeRepository, id, "Product Type");
    }

    /**
     * Performs a metadata-only update on an existing DRAFT Product.
     */
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest dto) {
        Product product = getProductEntityById(productId);

        if ("INACTIVE".equals(product.getStatus()) || "ARCHIVED".equals(product.getStatus())) {
            throw new IllegalStateException("Cannot update an INACTIVE or ARCHIVED product version.");
        }

        if (!"DRAFT".equals(product.getStatus())) {
            throw new IllegalStateException("Metadata update requires a new version. Product must be DRAFT.");
        }

        product.setName(dto.getName());
        product.setBankId(getCurrentBankId());

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    /**
     * Sets the product status to ACTIVE.
     */
    @Transactional
    public ProductResponse activateProduct(Long id, LocalDate effectiveDate) {
        Product product = getProductEntityById(id);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new IllegalStateException("Only DRAFT products can be directly ACTIVATED.");
        }

        product.setStatus("ACTIVE");
        if (effectiveDate != null) {
            product.setEffectiveDate(effectiveDate);
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Sets the product status to INACTIVE.
     */
    @Transactional
    public ProductResponse deactivateProduct(Long id) {
        Product product = getProductEntityById(id);

        if ("INACTIVE".equals(product.getStatus()) || "ARCHIVED".equals(product.getStatus())) {
            throw new IllegalStateException("Product is already inactive or archived.");
        }

        product.setStatus("INACTIVE");
        product.setExpirationDate(LocalDate.now());

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Updates only the expiration date.
     */
    @Transactional
    public ProductResponse extendProductExpiration(Long id, LocalDate newExpirationDate) {
        Product product = getProductEntityById(id);

        if (newExpirationDate == null) {
            throw new IllegalArgumentException("New expiration date cannot be null.");
        }
        if (product.getExpirationDate() != null && newExpirationDate.isBefore(product.getExpirationDate())) {
            throw new IllegalArgumentException("New expiration date must be after the current expiration date.");
        }
        if (newExpirationDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("New expiration date must be in the future.");
        }

        product.setExpirationDate(newExpirationDate);
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Creates a new version by cloning the old product using the Mapper.
     * This ensures all marketing fields (taglines, descriptions, icons) are preserved.
     */
    @Transactional
    public ProductResponse createNewVersion(Long oldProductId, ProductVersionRequest requestDto) {
        Product oldProduct = getProductEntityById(oldProductId);

        if (!"ACTIVE".equals(oldProduct.getStatus())) {
            throw new IllegalStateException("Only ACTIVE products can be used as a base for a new version.");
        }

        String bankId = oldProduct.getBankId();

        // 1. Archive Old
        oldProduct.setStatus("ARCHIVED");
        oldProduct.setExpirationDate(requestDto.getNewEffectiveDate().minusDays(1));
        productRepository.save(oldProduct);

        // 2. Map and Save New Version (Cloning metadata from oldProduct)
        Product newProduct = productMapper.createNewVersionFrom(oldProduct, requestDto);
        newProduct.setBankId(bankId); // Maintain tenant isolation

        Product savedNewProduct = productRepository.save(newProduct);

        // 3. Clone Feature Links
        List<ProductFeatureLink> newFeatureLinks = oldProduct.getProductFeatureLinks().stream()
                .map(oldLink -> {
                    ProductFeatureLink newLink = new ProductFeatureLink();
                    newLink.setProduct(savedNewProduct);
                    newLink.setFeatureComponent(oldLink.getFeatureComponent());
                    newLink.setFeatureValue(oldLink.getFeatureValue());
                    newLink.setBankId(bankId);
                    return newLink;
                }).toList();
        featureLinkRepository.saveAll(newFeatureLinks);

        // 4. Clone Pricing Links
        List<ProductPricingLink> oldPricingLinks = pricingLinkRepository.findByProductId(oldProductId);
        List<ProductPricingLink> newPricingLinks = oldPricingLinks.stream()
                .map(oldLink -> {
                    ProductPricingLink newLink = new ProductPricingLink();
                    newLink.setProduct(savedNewProduct);
                    newLink.setPricingComponent(oldLink.getPricingComponent());
                    newLink.setFixedValue(oldLink.getFixedValue());
                    newLink.setFixedValueType(oldLink.getFixedValueType());
                    newLink.setUseRulesEngine(oldLink.isUseRulesEngine());
                    newLink.setTargetComponentCode(oldLink.getTargetComponentCode());
                    newLink.setBankId(bankId);
                    return newLink;
                }).toList();
        pricingLinkRepository.saveAll(newPricingLinks);

        // 5. Finalize state
        productRepository.flush();
        featureLinkRepository.flush();
        pricingLinkRepository.flush();

        entityManager.refresh(savedNewProduct);
        return productMapper.toResponse(savedNewProduct);
    }

    private void validateFeatureValue(String value, FeatureComponent.DataType requiredType) {
        if (value == null || value.trim().isEmpty()) {
            if (requiredType != FeatureComponent.DataType.STRING) {
                throw new IllegalArgumentException("Feature value cannot be empty for data type: " + requiredType);
            }
        }

        switch (requiredType) {
            case INTEGER -> {
                try { Integer.parseInt(value); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Feature value '" + value + "' must be a valid INTEGER."); }
            }
            case DECIMAL -> {
                try { Double.parseDouble(value); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Feature value '" + value + "' must be a valid DECIMAL."); }
            }
            case BOOLEAN -> {
                String lowerValue = value.toLowerCase();
                if (!("true".equals(lowerValue) || "false".equals(lowerValue))) {
                    throw new IllegalArgumentException("Feature value '" + value + "' must be 'true' or 'false' for BOOLEAN.");
                }
            }
            case STRING -> {}
            default -> throw new IllegalArgumentException("Unsupported data type: " + requiredType);
        }
    }
}