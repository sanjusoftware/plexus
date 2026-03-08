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
public class BankConfigurationRequest {
    private String bankId;
    private Boolean allowProductInMultipleBundles;
    private List<CategoryConflictDto> categoryConflictRules;
    private String issuerUrl;
    private String currencyCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryConflictDto {
        private String categoryA;
        private String categoryB;
    }
}
