package com.bankengine.pricing.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class PriceValueResponseDto {
    private Long id;
    private Long pricingTierId;
    private BigDecimal priceAmount;
    private String valueType;
    private String currency;
}