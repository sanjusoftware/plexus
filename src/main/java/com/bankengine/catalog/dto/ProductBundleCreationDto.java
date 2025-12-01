package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductBundleCreationDto {

    @NotBlank(message = "Bundle code is required.")
    private String code;

    @NotBlank(message = "Bundle name is required.")
    private String name;

    private String description;

    @NotBlank(message = "Eligibility segment is required (e.g., Retail, SME).")
    private String eligibilitySegment;

    @NotNull(message = "Activation date is required.")
    private LocalDate activationDate;

    private LocalDate expiryDate;
}