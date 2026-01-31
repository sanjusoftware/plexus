package com.bankengine.catalog.service;

import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
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

    /**
     * Checks if a Product is already in a bundle.
     */
    public void validateProductCanBeBundled(Long productId) {
        String currentBankId = getCurrentBankId();

        boolean allowMultiBundle = bankConfigurationRepository.findByBankId(currentBankId)
                .map(BankConfiguration::isAllowProductInMultipleBundles)
                .orElse(false);

        if (allowMultiBundle) {
            return;
        }

        // 1. Fetch all historical links
        List<BundleProductLink> allLinks = bundleProductLinkRepository.findAllByProductId(productId);

        // 2. Filter: Only count links that are in DRAFT or ACTIVE bundles
        // ARCHIVED bundles are considered "historical" and do not block new bundling
        Optional<BundleProductLink> activeConflict = allLinks.stream()
                .filter(link -> link.getProductBundle().getStatus() != ProductBundle.BundleStatus.ARCHIVED)
                .findFirst();

        if (activeConflict.isPresent()) {
            String bundleCode = activeConflict.get().getProductBundle().getCode();
            ProductBundle.BundleStatus status = activeConflict.get().getProductBundle().getStatus();

            throw new ValidationException(
                    String.format("Product ID %d is currently in an %s bundle (%s). " +
                                    "Multi-bundling is disabled for bank %s.",
                            productId, status, bundleCode, currentBankId)
            );
        }
    }

    /**
     * Ensures the product being added doesn't conflict with existing products.
     */
    public void validateCategoryCompatibility(Product newProduct, List<Product> existingProducts) {
        String currentBankId = getCurrentBankId();
        String newCategory = newProduct.getCategory();

        // Fetch the specific bank's conflict rules
        List<CategoryConflictRule> conflictRules = bankConfigurationRepository
                .findByBankId(currentBankId)
                .map(BankConfiguration::getCategoryConflictRules)
                .orElse(List.of());

        for (Product existing : existingProducts) {
            String existingCategory = existing.getCategory();

            boolean isConflicting = conflictRules.stream()
                    .anyMatch(rule -> rule.isConflict(newCategory, existingCategory));

            if (isConflicting) {
                throw new ValidationException(String.format(
                        "Conflict: Category '%s' cannot be bundled with category '%s' for bank %s.",
                        newCategory, existingCategory, currentBankId));
            }
        }
    }
}