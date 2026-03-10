package com.bankengine.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VersionRequest {
    private String newName; // Optional: If null, keep old name
    private String newCode; // Optional: If provided, triggers "New Product/Branch" (v1)
    private LocalDate activationDate; // Optional: For activationDate
    private LocalDate expiryDate; // Optional: For expiryDate
}
