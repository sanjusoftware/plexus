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
import com.bankengine.catalog.repository.specification.ProductSpecification;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.web.exception.NotFoundException;
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
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository featureLinkRepository;
    private final FeatureComponentService featureComponentService;
    private final ProductTypeRepository productTypeRepository;
    private final EntityManager entityManager;
    private final ProductPricingLinkRepository pricingLinkRepository;
    private final PricingComponentService pricingComponentService;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository featureLinkRepository,
                          FeatureComponentService featureComponentService, ProductTypeRepository productTypeRepository, EntityManager entityManager, ProductPricingLinkRepository pricingLinkRepository, PricingComponentService pricingComponentService, ProductMapper productMapper) {
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

        // 1. Build the dynamic query specification
        Specification<Product> specification = ProductSpecification.filterBy(criteria);

        // 2. Create the Pageable object for pagination and sorting
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortBy());
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        // 3. Execute the search using the specification and pageable
        Page<Product> productPage = productRepository.findAll(specification, pageable);

        // 4. Map the results to a DTO Page
        return productPage.map(productMapper::toResponse);
    }

    /**
     * Synchronizes the features linked to a product based on the provided list.
     * This method handles creation of new links, deletion of missing links, and updates to existing ones.
     */
    @Transactional
    public ProductResponse syncProductFeatures(Long productId, List<ProductFeatureRequest> syncDtos) {
        // 1. Validate Product existence and fetch the entity
        Product product = getProductEntityById(productId);

        // 2. Fetch current links and map them by FeatureComponent ID for fast lookup
        List<ProductFeatureLink> currentLinks = featureLinkRepository.findByProductId(productId);

        // Map: { FeatureComponentId -> ProductFeatureLink }
        Map<Long, ProductFeatureLink> currentLinksMap = currentLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getFeatureComponent().getId(),
                        link -> link
                ));

        // Collect the set of incoming FeatureComponent IDs
        Set<Long> incomingFeatureIds = syncDtos.stream()
                .map(ProductFeatureRequest::getFeatureComponentId)
                .collect(Collectors.toSet());

        // 3. IDENTIFY and DELETE links that are no longer present (Cleanup)
        List<ProductFeatureLink> linksToDelete = currentLinks.stream()
                .filter(link -> !incomingFeatureIds.contains(link.getFeatureComponent().getId()))
                .collect(Collectors.toList());

        featureLinkRepository.deleteAll(linksToDelete);
        featureLinkRepository.flush(); // Flush 1: Ensures deletions hit the DB immediately

        // 4. IDENTIFY and CREATE/UPDATE links that are incoming
        for (ProductFeatureRequest dto : syncDtos) {
            Long featureId = dto.getFeatureComponentId();

            // Validate FeatureComponent existence once here
            FeatureComponent component = featureComponentService.getFeatureComponentById(featureId);

            // Validate the value against the required data type
            validateFeatureValue(dto.getFeatureValue(), component.getDataType());

            if (currentLinksMap.containsKey(featureId)) {
                // UPDATE: Link exists, check if featureValue changed
                ProductFeatureLink existingLink = currentLinksMap.get(featureId);

                if (!existingLink.getFeatureValue().equals(dto.getFeatureValue())) {
                    existingLink.setFeatureValue(dto.getFeatureValue());
                    featureLinkRepository.save(existingLink); // Explicit save for update
                }
                // If value is the same, do nothing.
            } else {
                // CREATE: Link is new
                ProductFeatureLink newLink = new ProductFeatureLink();
                newLink.setProduct(product);
                newLink.setFeatureComponent(component);
                newLink.setFeatureValue(dto.getFeatureValue());
                newLink.setBankId(product.getBankId());
                featureLinkRepository.save(newLink);
            }
        }
        featureLinkRepository.flush(); // Flush 2: Ensures creations/updates hit the DB immediately
        entityManager.clear();

        Product updatedProduct = getProductEntityById(productId);
        return productMapper.toResponse(updatedProduct);
    }

    /**
     * Synchronizes the pricing components linked to a product based on the provided list.
     * Synchronization is based on the composite key: (PricingComponentId, Context).
     */
    @Transactional
    public ProductResponse syncProductPricing(Long productId, List<ProductPricingRequest> syncDtos) {
        Product product = getProductEntityById(productId);

        // Define a composite key: ComponentID + Context is unique per product
        // Map: { ComponentID_Context -> ProductPricingLink }
        Map<String, ProductPricingLink> currentLinksMap = pricingLinkRepository.findByProductId(productId).stream()
                .collect(Collectors.toMap(
                        link -> link.getPricingComponent().getId() + "_" + link.getContext(),
                        link -> link
                ));

        // Collect the set of incoming composite keys
        Set<String> incomingKeys = syncDtos.stream()
                .map(dto -> dto.getPricingComponentId() + "_" + dto.getContext())
                .collect(Collectors.toSet());

        // 1. IDENTIFY and DELETE links that are no longer present (Cleanup)
        List<ProductPricingLink> linksToDelete = currentLinksMap.values().stream()
                .filter(link -> !incomingKeys.contains(link.getPricingComponent().getId() + "_" + link.getContext()))
                .collect(Collectors.toList());

        pricingLinkRepository.deleteAll(linksToDelete);
        pricingLinkRepository.flush(); // Flush deletions to the DB

        // 2. IDENTIFY and CREATE links that are incoming
        for (ProductPricingRequest dto : syncDtos) {
            String compositeKey = dto.getPricingComponentId() + "_" + dto.getContext();

            if (!currentLinksMap.containsKey(compositeKey)) {
                // CREATE: Link is new
                PricingComponent component = pricingComponentService.getPricingComponentById(dto.getPricingComponentId());

                ProductPricingLink newLink = new ProductPricingLink();
                newLink.setProduct(product);
                newLink.setPricingComponent(component);
                newLink.setContext(dto.getContext());
                newLink.setBankId(product.getBankId());
                pricingLinkRepository.save(newLink);
            }
        }

        pricingLinkRepository.flush();

        // RELOAD/REFRESH: Force the product to see the new links in the DB
        product = getProductEntityById(productId);
        entityManager.refresh(product);

        return productMapper.toResponse(product);
    }

    /**
     * Creates a new Product from a DTO, converting to Entity and performing lookups.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest requestDto) {
        if (requestDto.getProductTypeId() == null) {
            throw new IllegalArgumentException("Product Type ID is required for creation.");
        }
        // 1. Look up the required ProductType entity (using centralized lookup)
        ProductType productType = getProductTypeById(requestDto.getProductTypeId());

        // 2. Convert DTO to Entity
        Product product = new Product();
        product.setName(requestDto.getName());
        product.setBankId(requestDto.getBankId());
        product.setEffectiveDate(requestDto.getEffectiveDate());
        product.setExpirationDate(requestDto.getExpirationDate());
        product.setStatus(requestDto.getStatus());
        product.setProductType(productType);

        // 3. Save and convert back to DTO for response
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Retrieves Product by ID and converts it to a Response DTO.
     */
    @Transactional
    public ProductResponse getProductResponseById(Long id) {
        Product product = getProductEntityById(id);
        return productMapper.toResponse(product);
    }

    /**
     * Links a FeatureComponent to a Product with a specific value.
     */
    @Transactional
    public ProductResponse linkFeatureToProduct(Long productId, ProductFeatureRequest dto) {
        // 1. Validate Product exists
        Product product = getProductEntityById(productId);

        // 2. Validate FeatureComponent exists
        FeatureComponent component = featureComponentService.getFeatureComponentById(dto.getFeatureComponentId());

        // 3. Validate dto.featureValue against component.dataType
        validateFeatureValue(dto.getFeatureValue(), component.getDataType());

        // 4. Create and save the Link
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(product);
        link.setFeatureComponent(component);
        link.setFeatureValue(dto.getFeatureValue());
        link.setBankId(product.getBankId());
        featureLinkRepository.save(link);
        return getProductResponseById(product.getId());
    }

    /**
     * Helper method to retrieve a Product entity by ID, throwing NotFoundException on failure (404).
     */
    public Product getProductEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + id));
    }

    /**
     * Helper method to retrieve a ProductType entity by ID, throwing NotFoundException on failure (404).
     */
    private ProductType getProductTypeById(Long id) {
        return productTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product Type not found with ID: " + id));
    }

    /**
     * Performs a metadata-only update on an existing Product entity IF it is in DRAFT status.
     * Used for administrative updates before launch.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest dto) {
        Product product = getProductEntityById(id);

        // Don't allow update to INACTIVE/ARCHIVED product
        if ("INACTIVE".equals(product.getStatus()) || "ARCHIVED".equals(product.getStatus())) {
            throw new IllegalStateException("Cannot update an INACTIVE or ARCHIVED product version.");
        }

        // Only allow general metadata update if DRAFT (Otherwise, must use Copy-and-Update)
        if (!"DRAFT".equals(product.getStatus())) {
            throw new IllegalStateException("Metadata update requires a new version. Product must be DRAFT.");
        }

        // Status, Effective Date, and Expiration Date handled by direct methods.
        // Only update administrative metadata:
        product.setName(dto.getName());
        product.setBankId(dto.getBankId());

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    /**
     * Sets the product status to ACTIVE and updates the effective date if provided.
     */
    @Transactional
    public ProductResponse activateProduct(Long id, LocalDate effectiveDate) {
        Product product = getProductEntityById(id);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new IllegalStateException("Only DRAFT products can be directly ACTIVATED.");
        }

        product.setStatus("ACTIVE");
        // Only update effectiveDate if a value is provided, otherwise leave the one set at creation.
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
        // It's a good practice to set the expiration date to today on immediate deactivation.
        product.setExpirationDate(LocalDate.now());

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Updates only the expiration date (Extending life).
     */
    @Transactional
    public ProductResponse extendProductExpiration(Long id, LocalDate newExpirationDate) {
        Product product = getProductEntityById(id);

        if (newExpirationDate == null) {
            throw new IllegalArgumentException("New expiration date cannot be null.");
        }

        // Check if the new date is before the CURRENT date (only if CURRENT date exists)
        if (product.getExpirationDate() != null && newExpirationDate.isBefore(product.getExpirationDate())) {
            throw new IllegalArgumentException("New expiration date must be after the current expiration date.");
        }

        // Also, ensure the new date is not in the past
        if (newExpirationDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("New expiration date must be in the future.");
        }

        product.setExpirationDate(newExpirationDate);

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Creates a new version of a product. This involves archiving the old version
     * and creating a new entity that inherits all previous features and pricing links.
     *
     * @param oldProductId The ID of the product version to be replaced.
     * @param requestDto   The metadata for the new version.
     * @return The response DTO for the newly created product.
     */
    @Transactional
    public ProductResponse createNewVersion(Long oldProductId, NewProductVersionRequest requestDto) {
        Product oldProduct = getProductEntityById(oldProductId);

        if (!"ACTIVE".equals(oldProduct.getStatus())) {
            throw new IllegalStateException("Only ACTIVE products can be used as a base for a new version.");
        }

        String bankId = oldProduct.getBankId();

        // 1. Archive Old
        oldProduct.setStatus("ARCHIVED");
        oldProduct.setExpirationDate(requestDto.getNewEffectiveDate().minusDays(1));
        productRepository.save(oldProduct);

        // 2. Create New Base
        Product newProduct = new Product();
        newProduct.setProductType(oldProduct.getProductType());
        newProduct.setBankId(bankId);
        newProduct.setName(requestDto.getNewName());
        newProduct.setEffectiveDate(requestDto.getNewEffectiveDate());
        newProduct.setStatus("DRAFT");

        Product savedNewProduct = productRepository.save(newProduct);

        // 3. Clone Features
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

        // 4. Clone Pricing (Ensuring values are copied)
        List<ProductPricingLink> oldPricingLinks = pricingLinkRepository.findByProductId(oldProductId);
        List<ProductPricingLink> newPricingLinks = oldPricingLinks.stream()
                .map(oldLink -> {
                    ProductPricingLink newLink = new ProductPricingLink();
                    newLink.setProduct(savedNewProduct);
                    newLink.setPricingComponent(oldLink.getPricingComponent());
                    newLink.setContext(oldLink.getContext());
                    newLink.setFixedValue(oldLink.getFixedValue());
                    newLink.setUseRulesEngine(oldLink.isUseRulesEngine());
                    newLink.setBankId(bankId);
                    return newLink;
                }).toList();
        pricingLinkRepository.saveAll(newPricingLinks);

        // 5. Sync and Refresh
        productRepository.flush();
        featureLinkRepository.flush();
        pricingLinkRepository.flush();

        entityManager.refresh(savedNewProduct);
        return productMapper.toResponse(savedNewProduct);
    }

    /**
     * Helper method to validate the feature value type.
     * Throws IllegalArgumentException (maps to 400 Bad Request) on failure.
     */
    private void validateFeatureValue(String value, FeatureComponent.DataType requiredType) {
        if (value == null || value.trim().isEmpty()) {
            if (requiredType != FeatureComponent.DataType.STRING) {
                throw new IllegalArgumentException("Feature value cannot be empty for data type: " + requiredType);
            }
        }

        switch (requiredType) {
            case INTEGER:
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Feature value '" + value + "' must be a valid INTEGER.");
                }
                break;
            case DECIMAL:
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Feature value '" + value + "' must be a valid DECIMAL.");
                }
                break;
            case BOOLEAN:
                String lowerValue = value.toLowerCase();
                if (!("true".equals(lowerValue) || "false".equals(lowerValue))) {
                    throw new IllegalArgumentException("Feature value '" + value + "' must be 'true' or 'false' for BOOLEAN.");
                }
                break;
            case STRING:
                // No specific format validation is needed for strings.
                break;
            default:
                // Should not happen, but defensive programming is good.
                throw new IllegalArgumentException("Unsupported data type for validation: " + requiredType);
        }
    }

}