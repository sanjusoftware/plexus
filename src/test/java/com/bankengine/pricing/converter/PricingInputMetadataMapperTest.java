package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.dto.PricingMetadataResponse;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.PricingInputMetadata.AttributeSourceType;
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
        entity.setSourceType(AttributeSourceType.FACT_FIELD);
        entity.setSourceField("customerSegment");

        PricingMetadataResponse dto = mapper.toResponse(entity);
        assertNotNull(dto);
        assertEquals("Display Name", dto.getDisplayName());
        assertEquals("FACT_FIELD", dto.getSourceType());
        assertEquals("customerSegment", dto.getSourceField());

        PricingMetadataRequest requestDto = new PricingMetadataRequest();
        requestDto.setAttributeKey("key1");
        requestDto.setDataType("STRING");
        requestDto.setDisplayName("Display Name");
        requestDto.setSourceType("CUSTOM_ATTRIBUTE");
        requestDto.setSourceField("key1");

        PricingInputMetadata entity2 = mapper.toEntity(requestDto);
        assertEquals("key1", entity2.getAttributeKey());
        assertEquals(AttributeSourceType.CUSTOM_ATTRIBUTE, entity2.getSourceType());
    }
}
