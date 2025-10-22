package com.bankengine.catalog.service;

import com.bankengine.catalog.dto.ProductFeatureDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductFeatureLink;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final FeatureComponentService featureComponentService;

    // Constructor Injection (Spring automatically provides the repository instances)
    public ProductService(ProductRepository productRepository, ProductFeatureLinkRepository linkRepository, FeatureComponentService featureComponentService) {
        this.productRepository = productRepository;
        this.linkRepository = linkRepository;
        this.featureComponentService = featureComponentService;
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
     * Saves a new Product definition.
     * NOTE: This simplified method only saves the core Product.
     * Full creation would require handling the list of FeatureLinks separately.
     */
    @Transactional
    public Product createProduct(Product product) {
        // Basic business logic validation can go here (e.g., checking bankId format)
        if (product.getBankId() == null || product.getBankId().isEmpty()) {
            throw new IllegalArgumentException("Bank ID must be provided for the product.");
        }
        return productRepository.save(product);
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
}