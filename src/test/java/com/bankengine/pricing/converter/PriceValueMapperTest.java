package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.model.PriceValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceValueMapperTest {
    private final PriceValueMapper mapper = new PriceValueMapperImpl();

    @Test
    void shouldMapPriceAmountToRawValue() {
        PriceValueRequest dto = new PriceValueRequest();
        dto.setPriceAmount(BigDecimal.TEN);
        PriceValue entity = mapper.toEntity(dto);
        assertEquals(BigDecimal.TEN, entity.getRawValue());
    }
}