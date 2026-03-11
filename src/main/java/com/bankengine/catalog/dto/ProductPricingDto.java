package com.bankengine.catalog.dto;

import com.bankengine.pricing.model.PriceValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pricing link detail for a product.")
public class ProductPricingDto {

    @NotBlank(message = "Pricing component code is required.")
    @Schema(example = "FEE_001", description = "Unique code of the pricing component.")
    private String pricingComponentCode;

    @Schema(example = "Monthly Maintenance Fee", description = "Name of the component.")
    private String pricingComponentName;

    @Schema(example = "15.00", description = "Static override value if not using rules engine.")
    private BigDecimal fixedValue;

    @Schema(example = "FEE_ABSOLUTE")
    private PriceValue.ValueType fixedValueType;

    @Schema(example = "false", description = "If true, the pricing will be calculated via Drools rules.")
    private boolean useRulesEngine;

    @Schema(example = "CORE_FEE", description = "Business context for this pricing link.")
    private String targetComponentCode;

    @Schema(example = "2024-06-01")
    private LocalDate effectiveDate;

    @Schema(example = "2030-12-31")
    private LocalDate expiryDate;
}
