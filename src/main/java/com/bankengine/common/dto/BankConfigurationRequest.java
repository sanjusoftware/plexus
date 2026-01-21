package com.bankengine.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankConfigurationRequest {
    private String bankId;
    private boolean allowProductInMultipleBundles;
    private List<CategoryConflictDto> categoryConflictRules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryConflictDto {
        private String categoryA;
        private String categoryB;
    }
}
