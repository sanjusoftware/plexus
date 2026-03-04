package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PriceValue;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "Comprehensive breakdown of product costs and applied rules")
public class ProductPricingCalculationResult {

    @Schema(description = "Total cost to the customer after all fees and discounts", example = "12.50")
    private BigDecimal finalChargeablePrice;

    @ArraySchema(schema = @Schema(description = "List of individual fee and discount applications"))
    private List<PriceComponentDetail> componentBreakdown;

    @Data
    @Builder
    public static class PriceComponentDetail {
        @Schema(example = "SAV_MAINT_FEE")
        private String componentCode;

        @Schema(description = "For discounts, this specifies which fee code is being reduced", example = "SAV_MAINT_FEE")
        private String targetComponentCode;

        @Schema(description = "The original value defined in the tier", example = "15.00")
        private BigDecimal rawValue;

        @Schema(description = "Value type (e.g., FIXED, PERCENTAGE)", example = "FEE_ABSOLUTE")
        private PriceValue.ValueType valueType;

        // Restored fields
        @Schema(description = "Indicates if the fee is adjusted based on enrollment day")
        private boolean proRataApplicable;

        @Schema(description = "If true, the full amount is charged if a limit is breached")
        private boolean applyChargeOnFullBreach;

        @Schema(description = "The dollar-value impact of this specific component", example = "15.00")
        private BigDecimal calculatedAmount;

        @Schema(description = "Metadata indicating if rules engine was used", example = "RULES_ENGINE")
        private String sourceType;

        @Schema(description = "Database ID of the specific tier that matched the logic", example = "1024")
        private Long matchedTierId;
    }
}