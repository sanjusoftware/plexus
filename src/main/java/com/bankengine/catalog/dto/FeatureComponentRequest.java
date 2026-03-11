package com.bankengine.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FeatureComponentRequest {

    @NotBlank(message = "Feature code is required.")
    @Schema(example = "LOUNGE-ACCESS", description = "Unique code for the feature.")
    private String code;

    @NotBlank(message = "Feature name is required.")
    @Schema(example = "Airport Lounge Access", description = "Display name for the feature.")
    private String name;

    @NotBlank(message = "Data Type is required (e.g., STRING, BOOLEAN, INTEGER).")
    @Schema(example = "BOOLEAN", description = "The data type of the feature value. Allowed values: STRING, INTEGER, BOOLEAN, DECIMAL, DATE")
    private String dataType;

    @Schema(description = "Optional activation date.")
    private LocalDate activationDate;

    @Schema(description = "Optional expiry date.")
    private LocalDate expiryDate;
}
