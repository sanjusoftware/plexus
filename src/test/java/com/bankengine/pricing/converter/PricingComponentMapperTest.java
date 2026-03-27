package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingComponentResponse;
import com.bankengine.pricing.model.PricingComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingComponentMapperTest {
    private PricingComponentMapper mapper;

    @BeforeEach
    void setUp() {
        PricingTierMapper tierMapper = new PricingTierMapperImpl(new PriceValueMapperImpl(), new TierConditionMapperImpl());
        this.mapper = new PricingComponentMapperImpl(tierMapper);
    }

    @Test
    void testComponentMapper() {
        PricingComponent component = new PricingComponent();
        component.setName("Test Comp");
        component.setDescription("Test Description");
        PricingComponentResponse response = mapper.toResponseDto(component);
        assertEquals("Test Comp", response.getName());
        assertEquals("Test Description", response.getDescription());
    }

    @Test
    void mapComponentType_ShouldCoverAllBranches() {
        assertEquals(PricingComponent.ComponentType.FEE, mapper.mapComponentType("FEE"));

        assertThrows(IllegalArgumentException.class, () -> mapper.mapComponentType(null));
        assertThrows(IllegalArgumentException.class, () -> mapper.mapComponentType("INVALID"));
    }
}