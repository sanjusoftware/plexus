package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.TierCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Conditional expression that must match for the pricing tier to apply.")
public class TierConditionDto {

    @NotBlank(message = "Attribute name is required.")
    @Schema(description = "Registered pricing input attribute to evaluate.", example = "customerSegment")
    private String attributeName;

    @NotNull(message = "Operator is required.")
    @Schema(description = "Comparison operator applied to the attribute.", example = "EQ")
    private TierCondition.Operator operator;

    @NotBlank(message = "Attribute value is required.")
    @Schema(description = "Comparison value used by the operator.", example = "PREMIUM")
    private String attributeValue;

    // Logical connector is optional, only used if there are multiple conditions
    @Schema(description = "Logical connector to the next condition when multiple conditions are defined.", example = "AND")
    private TierCondition.LogicalConnector connector;
}