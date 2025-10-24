package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PricingTierService {

    private final PricingTierRepository pricingTierRepository;

    // Constructor Injection
    public PricingTierService(PricingTierRepository pricingTierRepository) {
        this.pricingTierRepository = pricingTierRepository;
    }

    @Transactional(readOnly = true)
    public List<PricingTier> findAllByPricingComponent(PricingComponent component) {
        // Simple delegation to the repository
        return pricingTierRepository.findAllByPricingComponent(component);
    }

    /**
     * Retrieves a PricingTier entity by ID, throwing NotFoundException if not found.
     * This centralizes the logic for a 404 response.
     */
    @Transactional(readOnly = true)
    public PricingTier getPricingTierById(Long id) {
        return pricingTierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PricingTier not found with ID: " + id));
    }
}