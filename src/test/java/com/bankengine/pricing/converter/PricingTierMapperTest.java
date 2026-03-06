package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingTierRequest;
import com.bankengine.pricing.dto.PricingTierResponse;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("Tier 1", response.getName(), "Name should be mapped correctly to response");
    }

    @Test
    void shouldMapDtoToEntityAndPreserveDefaultEmptyCollections() {
        // Given
        PricingTierRequest request = new PricingTierRequest();
        request.setName("New Request Tier");
        request.setApplyChargeOnFullBreach(true);

        // When
        PricingTier entity = mapper.toEntity(request);

        // Then
        assertAll("Verify DTO to Entity mapping",
            () -> assertEquals("New Request Tier", entity.getName()),
            () -> assertTrue(entity.isApplyChargeOnFullBreach()),
            // Asserting isEmpty() because the Entity initializes these fields to new HashSet<>()
            () -> assertTrue(entity.getConditions().isEmpty(), "Conditions should be an empty set (ignored by mapper)"),
            () -> assertTrue(entity.getPriceValues().isEmpty(), "PriceValues should be an empty set (ignored by mapper)")
        );
    }

    @Test
    void shouldCloneTierAndExplicitlyMapCollections() {
        // Given
        PriceValue pv = new PriceValue();
        pv.setRawValue(BigDecimal.ONE);

        TierCondition condition = new TierCondition();

        PricingTier old = new PricingTier();
        old.setName("Original Tier");
        old.setPriceValues(Set.of(pv));
        old.setConditions(Set.of(condition));

        // When
        PricingTier cloned = mapper.clone(old);

        // Then
        assertNotNull(cloned, "Cloned object should not be null");
        assertAll("Verify clone logic overrides ignore policy for collections",
            () -> assertEquals("Original Tier", cloned.getName()),
            () -> assertEquals(1, cloned.getPriceValues().size(), "PriceValues should be mapped from 'old' source"),
            () -> assertEquals(1, cloned.getConditions().size(), "Conditions should be mapped from 'old' source"),
            () -> assertNull(cloned.getPricingComponent(), "PricingComponent should still be ignored in clone")
        );
    }

    @Test
    void shouldUpdateFromDtoWithoutClearingExistingCollections() {
        // Given
        PricingTier entity = new PricingTier();
        entity.setName("Old Name");
        entity.getConditions().add(new TierCondition()); // Manually add one to check preservation

        PricingTierRequest request = new PricingTierRequest();
        request.setName("Updated Name");

        // When
        mapper.updateFromDto(request, entity);

        // Then
        assertAll("Verify partial update from DTO",
            () -> assertEquals("Updated Name", entity.getName()),
            () -> assertEquals(1, entity.getConditions().size(), "Existing conditions must not be cleared when ignored")
        );
    }
}