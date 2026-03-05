package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingInputMetadataMapperTest {
    private final PricingInputMetadataMapper mapper = new PricingInputMetadataMapperImpl();

    @Test
    void testMetadataMapper() {
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey("key1");
        entity.setDataType("STRING");
        entity.setDisplayName("Display Name");

        PricingMetadataDto dto = mapper.toResponse(entity);
        assertNotNull(dto);
        assertEquals("Display Name", dto.getDisplayName());

        PricingInputMetadata entity2 = mapper.toEntity(dto);
        assertEquals("key1", entity2.getAttributeKey());
    }
}