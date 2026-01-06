package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureComponentRequest {

    @NotBlank(message = "Feature name is required.")
    private String name;

    @NotBlank(message = "Data Type is required (e.g., STRING, BOOLEAN, INTEGER).")
    private String dataType;
}