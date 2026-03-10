package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.dto.PricingMetadataResponse;
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

        PricingMetadataResponse dto = mapper.toResponse(entity);
        assertNotNull(dto);
        assertEquals("Display Name", dto.getDisplayName());

        PricingMetadataRequest requestDto = new PricingMetadataRequest();
        requestDto.setAttributeKey("key1");
        requestDto.setDataType("STRING");
        requestDto.setDisplayName("Display Name");

        PricingInputMetadata entity2 = mapper.toEntity(requestDto);
        assertEquals("key1", entity2.getAttributeKey());
    }
}
