package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFeatureComponentRequestDto {

    @NotBlank(message = "Feature name is required.")
    private String name;

    @NotNull(message = "Data Type is required (e.g., INTEGER, DECIMAL, STRING).")
    private String dataType; // Expects a string that maps to FeatureComponent.DataType enum
}