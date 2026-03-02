package com.bankengine.catalog.service;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.common.service.BaseService;
import com.bankengine.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogConstraintService extends BaseService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;

    public void validateProductCanBeBundled(Long productId) {
        boolean allowMultiBundle = bankConfigurationRepository.findCurrent()
                .map(BankConfiguration::isAllowProductInMultipleBundles)
                .orElse(false);

        if (allowMultiBundle) {
            return;
        }

        List<BundleProductLink> allLinks = bundleProductLinkRepository.findAllByProductId(productId);
        List<VersionableEntity.EntityStatus> blockingStatuses = List.of(
                VersionableEntity.EntityStatus.DRAFT,
                VersionableEntity.EntityStatus.ACTIVE
        );

        Optional<BundleProductLink> activeConflict = allLinks.stream()
                .filter(link -> blockingStatuses.contains(link.getProductBundle().getStatus()))
                .findFirst();

        if (activeConflict.isPresent()) {
            BundleProductLink conflict = activeConflict.get();
            ProductBundle bundle = activeConflict.get().getProductBundle();
            Product product = conflict.getProduct();
            String errorMessage = String.format("[BUNDLE_CONSTRAINT_VIOLATION] Product '%s' (ID: %d) is currently in an %s bundle (%s). " +
                            "Bundling a product in more than 1 bundles is disabled for bank %s.",
                    product.getCode(), productId, bundle.getStatus(), bundle.getCode(), getCurrentBankId());
            throw new ValidationException(errorMessage);
        }
    }

    /**
     * Ensures the product being added doesn't conflict with existing products.
     */
    public void validateCategoryCompatibility(Product newProduct, List<Product> existingProducts) {
        String newCategory = newProduct.getCategory();

        // Fetch the specific bank's conflict rules
        List<CategoryConflictRule> conflictRules = bankConfigurationRepository
                .findCurrent()
                .map(BankConfiguration::getCategoryConflictRules)
                .orElse(List.of());

        for (Product existing : existingProducts) {
            String existingCategory = existing.getCategory();

            boolean isConflicting = conflictRules.stream()
                    .anyMatch(rule -> rule.isConflict(newCategory, existingCategory));

            if (isConflicting) {
                throw new ValidationException(String.format(
                        "Conflict: Category '%s' cannot be bundled with category '%s' for bank %s.",
                        newCategory, existingCategory, getCurrentBankId()));
            }
        }
    }
}