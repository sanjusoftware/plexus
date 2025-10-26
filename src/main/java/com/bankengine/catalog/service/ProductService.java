package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.web.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.dto.ProductFeatureLinkDto;
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

    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository linkRepository,
                          FeatureComponentService featureComponentService, ProductTypeRepository productTypeRepository, EntityManager entityManager) {
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.featureComponentService = featureComponentService;
        this.productTypeRepository = productTypeRepository;
        this.entityManager = entityManager;
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

        // 5. Clear the Persistence Context and Reload
        entityManager.clear();

        Product updatedProduct = getProductById(productId);
        return convertToResponseDto(updatedProduct);
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
        product.setActivationDate(requestDto.getActivationDate());
        product.setExpirationDate(requestDto.getExpirationDate());
        product.setStatus(requestDto.getStatus());
        product.setProductType(productType);

        // 3. Save and convert back to DTO for response
        Product savedProduct = productRepository.save(product);
        return convertToResponseDto(savedProduct);
    }

    /**
     * Retrieves Product by ID and converts it to a Response DTO.
     */
    @Transactional
    public ProductResponseDto getProductResponseById(Long id) {
        // Use the centralized lookup for consistency
        Product product = getProductById(id);

        return convertToResponseDto(product);
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
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Archives a product by setting its status to INACTIVE.
     * This is an update operation, using the existing getProductEntityById for 404 handling.
     */
    @Transactional
    public ProductResponseDto archiveProduct(Long id) {
        Product product = getProductById(id);
        product.setStatus("INACTIVE");
        product.setExpirationDate(LocalDate.now());
        Product updatedProduct = productRepository.save(product);
        return convertToResponseDto(updatedProduct);
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

    // --- Helper Method for DTO Conversion ---
    private ProductResponseDto convertToResponseDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setProductTypeName(product.getProductType() != null ? product.getProductType().getName() : null);
        dto.setBankId(product.getBankId());
        dto.setEffectiveDate(product.getEffectiveDate());
        dto.setStatus(product.getStatus());
        dto.setActivationDate(product.getActivationDate());
        dto.setExpirationDate(product.getExpirationDate());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        if (product.getProductFeatureLinks() != null) {
            dto.setFeatures(
                    product.getProductFeatureLinks().stream()
                            .map(this::convertLinkToDto) // Use the new helper method
                            .collect(Collectors.toList())
            );
        } else {
            dto.setFeatures(Collections.emptyList()); // Return an empty list instead of null
        }

        return dto;
    }

    /**
     * Helper Method: Converts a ProductFeatureLink Entity to its DTO.
     */
    private ProductFeatureLinkDto convertLinkToDto(ProductFeatureLink link) {
        ProductFeatureLinkDto dto = new ProductFeatureLinkDto();

        // Ensure you fetch the component name, not the entire component object
        dto.setFeatureName(link.getFeatureComponent().getName());
        dto.setFeatureValue(link.getFeatureValue());

        return dto;
    }
}