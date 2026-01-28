package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingMappersTest {

    private final PricingInputMetadataMapper metadataMapper = Mappers.getMapper(PricingInputMetadataMapper.class);

    @Test
    void testMetadataMapper() {
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey("key1");
        entity.setDataType("STRING");
        entity.setDisplayName("Display Name");

        PricingMetadataDto dto = metadataMapper.toResponse(entity);

        assertNotNull(dto);
        assertEquals("key1", dto.getAttributeKey());
        assertEquals("STRING", dto.getDataType());
        assertEquals("Display Name", dto.getDisplayName());

        PricingInputMetadata entity2 = metadataMapper.toEntity(dto);
        assertEquals("key1", entity2.getAttributeKey());
    }
}
