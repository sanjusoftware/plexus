package com.bankengine.catalog.dto;

import com.bankengine.pricing.model.PriceValue;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BundlePricingResponse {
    private Long pricingComponentId;
    private String pricingComponentName;
    private BigDecimal fixedValue;
    private PriceValue.ValueType fixedValueType;
    private boolean useRulesEngine;
}
