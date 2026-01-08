package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.common.exception.ValidationException;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogConstraintService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;

    /**
     * Checks if a Product is already in a bundle, using the Bank ID from the Context Holder.
     */
    public void validateProductCanBeBundled(Long productId) {

        String currentBankId = BankContextHolder.getBankId();

        // 1. Check Bank Configuration (Dynamic Flag)
        boolean allowMultiBundle = bankConfigurationRepository.findByBankId(currentBankId)
                .map(config -> config.isAllowProductInMultipleBundles())
                .orElse(false); // Default to strict (false) if config is missing

        if (allowMultiBundle) {
            return;
        }

        // 2. Strict Mode Validation (Check DB for existing links)
        Optional<BundleProductLink> existingLink = bundleProductLinkRepository.findByProductId(productId);

        if (existingLink.isPresent()) {
            String existingBundleCode = existingLink.get().getProductBundle().getCode();

            throw new ValidationException(
                    String.format("Product ID %d is already assigned to Bundle %s. Bank configuration (%s) prevents a product from being included in multiple bundles.",
                            productId, existingBundleCode, currentBankId)
            );
        }
    }

    /**
     * Ensures the product being added doesn't conflict with existing products
     * based on Bank-specific category rules.
     */
    public void validateCategoryCompatibility(Product newProduct, List<Product> existingProducts) {
        String currentBankId = BankContextHolder.getBankId();
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
                        "Conflict: Category '%s' cannot be bundled with category '%s' for this bank.",
                        newCategory, existingCategory));
            }
        }
    }
}