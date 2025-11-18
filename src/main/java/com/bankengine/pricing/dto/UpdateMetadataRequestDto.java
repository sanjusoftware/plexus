package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMetadataRequestDto {

    // DisplayName: Can be updated at any time.
    @NotBlank(message = "Display name is required for update.")
    @Size(max = 100, message = "Display name must be less than 100 characters.")
    private String displayName;

    // DataType: Can be updated, but service layer should check if this change breaks existing tiers.
    @NotBlank(message = "Data type is required for update.")
    @Pattern(regexp = "^(STRING|DECIMAL|INTEGER|BOOLEAN|DATE)$", message = "Data type must be one of: STRING, DECIMAL, INTEGER, BOOLEAN, DATE.")
    private String dataType;
}