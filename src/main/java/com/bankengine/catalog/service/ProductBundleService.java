package com.bankengine.catalog.service;

import com.bankengine.auth.security.BankContextHolder;
import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductBundleRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductBundleService {

    private final ProductBundleRepository productBundleRepository;
    private final ProductRepository productRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;
    private final CatalogConstraintService constraintService;

    @Transactional
    public Long createBundle(ProductBundleRequest dto) {
        String bankId = BankContextHolder.getBankId();

        ProductBundle bundle = new ProductBundle();
        bundle.setBankId(bankId);
        bundle.setCode(dto.getCode());
        bundle.setName(dto.getName());
        bundle.setDescription(dto.getDescription());
        bundle.setEligibilitySegment(dto.getEligibilitySegment());
        bundle.setActivationDate(dto.getActivationDate());
        bundle.setExpiryDate(dto.getExpiryDate());

        return productBundleRepository.save(bundle).getId();
    }

    @Transactional
    public void linkProduct(Long bundleId, Long productId, boolean isMainAccount, boolean isMandatory) {

        // 1. Validation: Check multi-bundling constraint
        constraintService.validateProductCanBeBundled(productId);

        // 2. Fetch Entities
        ProductBundle bundle = productBundleRepository.findById(bundleId)
                .orElseThrow(() -> new NotFoundException("Product Bundle not found with ID: " + bundleId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + productId));

        // 3. Create Link
        BundleProductLink link = new BundleProductLink(bundle, product, isMainAccount, isMandatory);
        link.setBankId(BankContextHolder.getBankId());

        bundleProductLinkRepository.save(link);
    }
}