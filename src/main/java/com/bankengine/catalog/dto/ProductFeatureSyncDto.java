package com.bankengine.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductFeatureSyncDto {

    @Valid
    @NotNull(message = "The list of features cannot be null.")
    private List<ProductFeatureDto> features;
}