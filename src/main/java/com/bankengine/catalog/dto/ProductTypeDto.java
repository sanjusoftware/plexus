package com.bankengine.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "DTO representing a Product Type")
public class ProductTypeDto {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "The unique ID of the product type")
    private Long id;

    @NotBlank(message = "Product Type name is required.")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters.")
    @Schema(description = "The name of the product type", example = "CASA")
    private String name;
}