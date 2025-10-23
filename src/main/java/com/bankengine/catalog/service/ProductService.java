package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.dto.ProductFeatureLinkDto;
import com.bankengine.catalog.repository.ProductTypeRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final FeatureComponentService featureComponentService;
    private final ProductTypeRepository productTypeRepository;

    // Constructor Injection (Spring automatically provides the repository instances)
    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository linkRepository,
                          FeatureComponentService featureComponentService, ProductTypeRepository productTypeRepository) {
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.featureComponentService = featureComponentService;
        this.productTypeRepository = productTypeRepository;
    }

    /**
     * Retrieves a Product and its associated features by ID.
     * @param id The ID of the Product.
     * @return An Optional containing the Product if found.
     */
    public Optional<Product> getProductById(Long id) {
        // Step 1: Get the Product (using JPA's built-in findById)
        Optional<Product> product = productRepository.findById(id);

        // Step 2: If the Product exists, fetch and set its features
        product.ifPresent(p -> {
            // Find all feature links associated with this product ID
            List<ProductFeatureLink> features = linkRepository.findByProductId(p.getId());

            // NOTE: In a more complex system, we would wrap this data
            // in a DTO (Data Transfer Object) before returning.
            // For now, we'll keep the logic simple.
            System.out.println("Product retrieved with " + features.size() + " features.");
            // We would typically process and attach these features to the DTO here.
        });

        return product;
    }

    /**
     * Creates a new Product from a DTO, converting to Entity and performing lookups.
     */
    @Transactional
    public ProductResponseDto createProduct(CreateProductRequestDto requestDto) {
        // 1. Look up the required ProductType entity
        ProductType productType = productTypeRepository.findById(requestDto.getProductTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Product Type not found with ID: " + requestDto.getProductTypeId()));

        // 2. Convert DTO to Entity
        Product product = new Product();
        product.setName(requestDto.getName());
        product.setBankId(requestDto.getBankId());
        product.setEffectiveDate(requestDto.getEffectiveDate());
        product.setStatus(requestDto.getStatus());
        product.setProductType(productType); // Set the looked-up entity

        // 3. Save and convert back to DTO for response
        Product savedProduct = productRepository.save(product);
        return convertToResponseDto(savedProduct); // New helper method
    }

    /**
     * Retrieves Product by ID and converts it to a Response DTO.
     */
    @Transactional // CRITICAL: Ensures the feature links can be loaded from the DB
    public ProductResponseDto getProductResponseById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + id));

        // Accessing the feature links here (implicitly in convertToResponseDto)
        // works because the method is inside a transaction.
        return convertToResponseDto(product);
    }

    /**
     * Links a FeatureComponent to a Product with a specific value.
     */
    @Transactional
    public ProductFeatureLink linkFeatureToProduct(ProductFeatureDto dto) {
        // 1. Validate Product exists
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));

        // 2. Validate FeatureComponent exists
        FeatureComponent component = featureComponentService.getFeatureComponent(dto.getFeatureComponentId())
                .orElseThrow(() -> new IllegalArgumentException("Feature Component not found."));

        // 3. Optional: Add logic here to validate dto.featureValue against component.dataType

        // 4. Create and save the Link
        ProductFeatureLink link = new ProductFeatureLink();
        link.setProduct(product);
        link.setFeatureComponent(component);
        link.setFeatureValue(dto.getFeatureValue());

        return linkRepository.save(link);
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