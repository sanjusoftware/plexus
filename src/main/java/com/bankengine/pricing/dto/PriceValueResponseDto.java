package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue.ValueType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PriceValueResponseDto {
    private Long id;
    private BigDecimal priceAmount;
    private ValueType valueType;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}