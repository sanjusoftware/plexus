package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.TierCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TierConditionDto {

    @NotBlank(message = "Attribute name is required.")
    private String attributeName;

    @NotNull(message = "Operator is required.")
    private TierCondition.Operator operator;

    @NotBlank(message = "Attribute value is required.")
    private String attributeValue;

    // Logical connector is optional, only used if there are multiple conditions
    private TierCondition.LogicalConnector connector;
}