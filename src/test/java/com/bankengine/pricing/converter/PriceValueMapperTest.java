package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PriceValueMapperTest {
    private final PriceValueMapper mapper = new PriceValueMapperImpl();

    @Test
    void shouldMapPriceAmountToRawValue() {
        PriceValueRequest dto = new PriceValueRequest();
        dto.setPriceAmount(BigDecimal.TEN);
        PriceValue entity = mapper.toEntity(dto);
        assertEquals(BigDecimal.TEN, entity.getRawValue(), "PriceAmount from DTO should map to RawValue in Entity");
    }

    @Test
    void shouldMapEntityToPriceComponentDetailWithNestedPricingFields() {
        // Given
        PricingComponent component = new PricingComponent();
        component.setCode("MTH_FEE");
        component.setProRataApplicable(true);

        PricingTier tier = new PricingTier();
        tier.setId(100L);
        tier.setCode("TIER_1");
        tier.setApplyChargeOnFullBreach(true);
        tier.setPricingComponent(component);

        PriceValue entity = new PriceValue();
        entity.setRawValue(new BigDecimal("15.00"));
        entity.setValueType(PriceValue.ValueType.FEE_ABSOLUTE);
        entity.setPricingTier(tier);

        // When
        ProductPricingCalculationResult.PriceComponentDetail detail = mapper.toDetailDto(entity);

        // Then
        assertAll("Verify complex mapping from PriceValue Entity to Detail DTO",
            () -> assertEquals("MTH_FEE", detail.getComponentCode(), "Should map component code from nested PricingComponent"),
            () -> assertEquals(100L, detail.getMatchedTierId(), "Should map tier ID from nested PricingTier"),
            () -> assertEquals("TIER_1", detail.getMatchedTierCode(), "Should map tier code from nested PricingTier"),
            () -> assertEquals(PriceValue.ValueType.FEE_ABSOLUTE, detail.getValueType(), "Should explicitly map ValueType enum"),
            () -> assertEquals("CATALOG", detail.getSourceType(), "SourceType should be hardcoded to CATALOG"),
            () -> assertTrue(detail.isProRataApplicable(), "Should map proRataApplicable from deep nested component"),
            () -> assertTrue(detail.isApplyChargeOnFullBreach(), "Should map applyChargeOnFullBreach from nested tier")
        );
    }

    @Test
    void testClone_ShouldDeepCopyAndIgnoreTier() {
        // Given
        PriceValue source = new PriceValue();
        source.setRawValue(BigDecimal.ONE);
        source.setPricingTier(new PricingTier()); // Should be ignored in clone

        // When
        PriceValue cloned = mapper.clone(source);

        // Then
        assertNotNull(cloned);
        assertEquals(BigDecimal.ONE, cloned.getRawValue());
        assertNull(cloned.getPricingTier(), "Cloned object should ignore pricingTier as per @Mapping");
    }
}