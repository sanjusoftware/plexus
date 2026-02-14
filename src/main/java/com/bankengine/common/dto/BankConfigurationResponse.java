package com.bankengine.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConfigurationResponse {
    private String bankId;
    private String issuerUrl;
    private boolean allowProductInMultipleBundles;
    private List<BankConfigurationRequest.CategoryConflictDto> categoryConflictRules;
}
