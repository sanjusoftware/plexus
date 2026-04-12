package com.bankengine.common.controller;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.dto.BankProductCategoryOptionsResponse;
import com.bankengine.common.service.BankConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@Tag(name = "Bank Management", description = "Endpoints for managing bank configurations.")
public class BankConfigurationController {

    private final BankConfigurationService bankConfigurationService;

    @PostMapping
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Create a new bank configuration", description = "Initializes a new bank/tenant in the system.")
    public ResponseEntity<BankConfigurationResponse> createBank(@RequestBody BankConfigurationRequest request) {
        return new ResponseEntity<>(bankConfigurationService.createBank(request), HttpStatus.CREATED);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:bank:write') or hasAuthority('bank:config:write')")
    @Operation(summary = "Update bank configuration", description = "Bank Admins can update only allowProductInMultipleBundles and categoryConflictRules for ACTIVE banks. System Admins can update all fields for any bank.")
    public ResponseEntity<BankConfigurationResponse> updateBank(@RequestBody BankConfigurationRequest request) {
        boolean isSystemAdmin = TenantContextHolder.getSystemBankId().equals(TenantContextHolder.getBankId());
        return ResponseEntity.ok(bankConfigurationService.updateBank(request, isSystemAdmin));
    }

    @PutMapping("/{bankId}")
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Update bank configuration by ID (System Admin only)", description = "Allows System Admin to update any bank configuration by its ID, regardless of status.")
    public ResponseEntity<BankConfigurationResponse> updateBankById(@PathVariable String bankId, @RequestBody BankConfigurationRequest request) {
        return ResponseEntity.ok(bankConfigurationService.updateBankById(bankId, request));
    }

    @PostMapping("/{bankId}/activate")
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Activate a bank configuration")
    public ResponseEntity<BankConfigurationResponse> activateBank(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.activateBank(bankId));
    }

    @PostMapping("/{bankId}/reject")
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Reject a bank configuration (DRAFT only)")
    public ResponseEntity<BankConfigurationResponse> rejectBank(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.rejectBank(bankId));
    }

    @PostMapping("/{bankId}/deactivate")
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Deactivate a bank configuration")
    public ResponseEntity<BankConfigurationResponse> deactivateBank(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.deactivateBank(bankId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:bank:read')")
    @Operation(summary = "List all bank configurations", description = "Retrieves all banks in the system. Restricted to System Admin.")
    public ResponseEntity<java.util.List<BankConfigurationResponse>> getAllBanks() {
        return ResponseEntity.ok(bankConfigurationService.getAllBanks());
    }

    @GetMapping("/{bankId}")
    @PreAuthorize("hasAuthority('system:bank:read') or hasAuthority('bank:config:read')")
    @Operation(summary = "Get bank configuration details")
    public ResponseEntity<BankConfigurationResponse> getBank(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.getBank(bankId));
    }

    @GetMapping("/{bankId}/product-categories")
    @PreAuthorize("hasAuthority('system:bank:read') or hasAuthority('bank:config:read') or hasAuthority('catalog:product:read')")
    @Operation(summary = "Get bank product category options", description = "Returns existing categories aggregated from products and conflict rules, plus starter examples.")
    public ResponseEntity<BankProductCategoryOptionsResponse> getProductCategories(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.getProductCategoryOptions(bankId));
    }
}