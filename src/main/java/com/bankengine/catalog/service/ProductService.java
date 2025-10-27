package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductConverter;
import com.bankengine.catalog.dto.*;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
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
import com.bankengine.catalog.repository.ProductTypeRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final FeatureComponentService featureComponentService;
    private final ProductTypeRepository productTypeRepository;
    private final EntityManager entityManager;
    private final ProductPricingLinkRepository pricingLinkRepository;
    private final PricingComponentService pricingComponentService;
    private final ProductConverter productConverter;

    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository linkRepository,
                          FeatureComponentService featureComponentService, ProductTypeRepository productTypeRepository, EntityManager entityManager, ProductPricingLinkRepository pricingLinkRepository, PricingComponentService pricingComponentService, ProductConverter productConverter) {
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.featureComponentService = featureComponentService;
        this.productTypeRepository = productTypeRepository;
        this.entityManager = entityManager;
        this.pricingLinkRepository = pricingLinkRepository;
        this.pricingComponentService = pricingComponentService;
        this.productConverter = productConverter;
    }

    /**
     * Searches and filters products based on criteria, returning paginated results.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> searchProducts(ProductSearchRequestDto criteria) {

        // 1. Build the dynamic query specification
        Specification<Product> specification = ProductSpecification.filterBy(criteria);

        // 2. Create the Pageable object for pagination and sorting
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortBy());
        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        // 3. Execute the search using the specification and pageable
        Page<Product> productPage = productRepository.findAll(specification, pageable);

        // 4. Map the results to a DTO Page
        return productPage.map(productConverter::convertToResponseDto);
    }

    /**
     * Synchronizes the features linked to a product based on the provided list.
     * This method handles creation of new links, deletion of missing links, and updates to existing ones.
     */
    @Transactional
    public ProductResponseDto syncProductFeatures(Long productId, List<ProductFeatureDto> syncDtos) {
        // 1. Validate Product existence and fetch the entity
        Product product = getProductById(productId);

        // 2. Fetch current links and map them by FeatureComponent ID for fast lookup
        List<ProductFeatureLink> currentLinks = linkRepository.findByProductId(productId);

        // Map: { FeatureComponentId -> ProductFeatureLink }
        Map<Long, ProductFeatureLink> currentLinksMap = currentLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getFeatureComponent().getId(),
                        link -> link
                ));

        // Collect the set of incoming FeatureComponent IDs
        Set<Long> incomingFeatureIds = syncDtos.stream()
                .map(ProductFeatureDto::getFeatureComponentId)
                .collect(Collectors.toSet());

        // 3. IDENTIFY and DELETE links that are no longer present (Cleanup)
        List<ProductFeatureLink> linksToDelete = currentLinks.stream()
                .filter(link -> !incomingFeatureIds.contains(link.getFeatureComponent().getId()))
                .collect(Collectors.toList());

        linkRepository.deleteAll(linksToDelete);
        linkRepository.flush(); // Flush 1: Ensures deletions hit the DB immediately

        // 4. IDENTIFY and CREATE/UPDATE links that are incoming
        for (ProductFeatureDto dto : syncDtos) {
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
                    linkRepository.save(existingLink); // Explicit save for update
                }
                // If value is the same, do nothing.
            } else {
                // CREATE: Link is new
                ProductFeatureLink newLink = new ProductFeatureLink();
                newLink.setProduct(product);
                newLink.setFeatureComponent(component);
                newLink.setFeatureValue(dto.getFeatureValue());
                linkRepository.save(newLink);
            }
        }
        linkRepository.flush(); // Flush 2: Ensures creations/updates hit the DB immediately
        entityManager.clear();

        Product updatedProduct = getProductById(productId);
        return productConverter.convertToResponseDto(updatedProduct);
    }

    /**
     * Synchronizes the pricing components linked to a product based on the provided list.
     * Synchronization is based on the composite key: (PricingComponentId, Context).
     */
    @Transactional
    public ProductResponseDto syncProductPricing(Long productId, List<ProductPricingDto> syncDtos) {
        Product product = getProductById(productId);

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
        for (ProductPricingDto dto : syncDtos) {
            String compositeKey = dto.getPricingComponentId() + "_" + dto.getContext();

            if (!currentLinksMap.containsKey(compositeKey)) {
                // CREATE: Link is new
                PricingComponent component = pricingComponentService.getPricingComponentById(dto.getPricingComponentId());

                ProductPricingLink newLink = new ProductPricingLink();
                newLink.setProduct(product);
                newLink.setPricingComponent(component);
                newLink.setContext(dto.getContext());

                pricingLinkRepository.save(newLink);
            }
        }

        pricingLinkRepository.flush();
        entityManager.clear();

        // Product entity needs to have the @OneToMany mapping defined for pricing links.
        return getProductResponseById(productId);
    }

    /**
     * Creates a new Product from a DTO, converting to Entity and performing lookups.
     */
    @Transactional
    public ProductResponseDto createProduct(CreateProductRequestDto requestDto) {
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
        return productConverter.convertToResponseDto(savedProduct);
    }

    /**
     * Retrieves Product by ID and converts it to a Response DTO.
     */
    @Transactional
    public ProductResponseDto getProductResponseById(Long id) {
        // Use the centralized lookup for consistency
        Product product = getProductById(id);

        return productConverter.convertToResponseDto(product);
    }

    /**
     * Links a FeatureComponent to a Product with a specific value.
     */
    @Transactional
    public ProductFeatureLink linkFeatureToProduct(ProductFeatureDto dto) {
        // 1. Validate Product exists
        Product product = getProductById(dto.getProductId());

        // 2. Validate FeatureComponent exists
        FeatureComponent component = featureComponentService.getFeatureComponentById(dto.getFeatureComponentId());

        // 3. Validate dto.featureValue against component.dataType
        validateFeatureValue(dto.getFeatureValue(), component.getDataType());

        // 4. Create and save the Link
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(product);
        link.setFeatureComponent(component);
        link.setFeatureValue(dto.getFeatureValue());

        return linkRepository.save(link);
    }

    /**
     * Helper method to retrieve a Product entity by ID, throwing NotFoundException on failure (404).
     */
    public Product getProductById(Long id) {
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
     * Retrieves all Product entities.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> findAllProducts() {
        return productRepository.findAll().stream()
                .map(productConverter::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Performs a metadata-only update on an existing Product entity IF it is in DRAFT status.
     * Used for administrative updates before launch.
     */
    @Transactional
    public ProductResponseDto updateProduct(Long id, UpdateProductRequestDto dto) {
        Product product = getProductById(id);

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
        return productConverter.convertToResponseDto(updatedProduct);
    }

    /**
     * Sets the product status to ACTIVE and updates the effective date if provided.
     */
    @Transactional
    public ProductResponseDto activateProduct(Long id, LocalDate effectiveDate) {
        Product product = getProductById(id);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new IllegalStateException("Only DRAFT products can be directly ACTIVATED.");
        }

        product.setStatus("ACTIVE");
        // Only update effectiveDate if a value is provided, otherwise leave the one set at creation.
        if (effectiveDate != null) {
            product.setEffectiveDate(effectiveDate);
        }

        Product savedProduct = productRepository.save(product);
        return productConverter.convertToResponseDto(savedProduct);
    }

    /**
     * Sets the product status to INACTIVE.
     */
    @Transactional
    public ProductResponseDto deactivateProduct(Long id) {
        Product product = getProductById(id);

        if ("INACTIVE".equals(product.getStatus()) || "ARCHIVED".equals(product.getStatus())) {
            throw new IllegalStateException("Product is already inactive or archived.");
        }

        product.setStatus("INACTIVE");
        // It's a good practice to set the expiration date to today on immediate deactivation.
        product.setExpirationDate(LocalDate.now());

        Product savedProduct = productRepository.save(product);
        return productConverter.convertToResponseDto(savedProduct);
    }

    /**
     * Updates only the expiration date (Extending life).
     */
    @Transactional
    public ProductResponseDto extendProductExpiration(Long id, LocalDate newExpirationDate) {
        Product product = getProductById(id);

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
        return productConverter.convertToResponseDto(savedProduct);
    }

    /**
     * Creates a new version of a product. This involves archiving the old version
     * and creating a new entity that inherits all previous features and pricing links.
     * @param oldProductId The ID of the product version to be replaced.
     * @param requestDto The metadata for the new version.
     * @return The response DTO for the newly created product.
     */
    @Transactional
    public ProductResponseDto createNewVersion(Long oldProductId, CreateNewVersionRequestDto requestDto) {

        // 1. Validate and Retrieve the Old Product
        Product oldProduct = getProductById(oldProductId);

        // Enforce business rule: Only ACTIVE products can be versioned (copied).
        if (!"ACTIVE".equals(oldProduct.getStatus())) {
            throw new IllegalStateException("Only ACTIVE products can be used as a base for a new version.");
        }

        // 2. Archive the Old Product
        oldProduct.setStatus("ARCHIVED");
        // Set the expiration date to the day *before* the new version becomes effective
        oldProduct.setExpirationDate(requestDto.getNewEffectiveDate().minusDays(1));
        productRepository.save(oldProduct);

        // 3. Create the New Product Entity (Inherits most data)
        Product newProduct = new Product();

        // Inherited configuration fields
        newProduct.setProductType(oldProduct.getProductType());
        newProduct.setBankId(oldProduct.getBankId());

        // New version metadata
        newProduct.setName(requestDto.getNewName());
        newProduct.setEffectiveDate(requestDto.getNewEffectiveDate());
        newProduct.setStatus("DRAFT"); // New versions start as DRAFT for review/modification

        // Save the new product to generate the ID
        Product savedNewProduct = productRepository.save(newProduct);

        // 4. Copy Links (Features and Pricing)

        // 4a. Copy ProductFeatureLink
        List<ProductFeatureLink> newFeatureLinks = oldProduct.getProductFeatureLinks().stream()
                .map(oldLink -> {
                    ProductFeatureLink newLink = new ProductFeatureLink();
                    newLink.setProduct(savedNewProduct);
                    newLink.setFeatureComponent(oldLink.getFeatureComponent());
                    newLink.setFeatureValue(oldLink.getFeatureValue());
                    return newLink;
                })
                .collect(Collectors.toList());

        linkRepository.saveAll(newFeatureLinks);

        // 4b. Copy ProductPricingLink (Requires fetching the links from the repo if not EAGER)
        List<ProductPricingLink> oldPricingLinks = pricingLinkRepository.findByProductId(oldProductId);

        List<ProductPricingLink> newPricingLinks = oldPricingLinks.stream()
                .map(oldLink -> {
                    ProductPricingLink newLink = new ProductPricingLink();
                    newLink.setProduct(savedNewProduct);
                    newLink.setPricingComponent(oldLink.getPricingComponent());
                    newLink.setContext(oldLink.getContext());
                    return newLink;
                })
                .collect(Collectors.toList());

        pricingLinkRepository.saveAll(newPricingLinks);

        // 5. Ensure new collections are loaded for DTO conversion
        entityManager.clear();
        Product finalNewProduct = getProductById(savedNewProduct.getId());

        return productConverter.convertToResponseDto(finalNewProduct);
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