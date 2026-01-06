package com.bankengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewProductVersionRequest {

    @NotBlank(message = "New product name is required.")
    private String newName;

    @NotNull(message = "New effective date is required for the new version.")
    private LocalDate newEffectiveDate;
}