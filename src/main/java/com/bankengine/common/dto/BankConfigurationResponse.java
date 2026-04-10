package com.bankengine.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConfigurationResponse {
    private String bankId;
    private String name;
    private String issuerUrl;
    private String clientId;
    private boolean hasClientSecret;
    private boolean allowProductInMultipleBundles;
    private String currencyCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String adminName;
    private String adminEmail;
    private List<BankConfigurationRequest.CategoryConflictDto> categoryConflictRules;
}
