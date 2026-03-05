package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingTierResponse;
import com.bankengine.pricing.model.PricingTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingTierMapperTest {
    private PricingTierMapper mapper;

    @BeforeEach
    void setUp() {
        this.mapper = new PricingTierMapperImpl(new PriceValueMapperImpl(), new TierConditionMapperImpl());
    }

    @Test
    void testTierMapper() {
        PricingTier tier = new PricingTier();
        tier.setName("Tier 1");
        tier.setMinThreshold(BigDecimal.TEN);

        PricingTierResponse response = mapper.toResponse(tier);
        assertNotNull(response);
        assertEquals("Tier 1", response.getName());
    }
}