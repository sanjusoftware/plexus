package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedPriceResponse {

    private String bankId;

    private BigDecimal grossTotalAmount;

    private Map<String, BigDecimal> adjustments;

    private BigDecimal netTotalAmount;

    private List<ProductPriceResultDto> productResults;
}