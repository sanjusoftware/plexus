package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BundleProductLinkRequestDto {

    @NotNull(message = "Product ID is required for linking.")
    private Long productId;

    // Defines if the product is designated as the primary account in the bundle
    private boolean isMainAccount = false;

    // Defines if the product is required to be taken with the bundle
    private boolean isMandatory = true;
}