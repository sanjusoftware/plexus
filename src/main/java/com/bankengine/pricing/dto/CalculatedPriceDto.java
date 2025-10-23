package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CalculatedPriceDto {
    private String componentName;
    private BigDecimal finalAmount;
    private String type;
}