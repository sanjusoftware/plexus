package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingTierRepository;
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

    // You can add other tier-related methods here if needed
}