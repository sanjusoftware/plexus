package com.bankengine.common.controller;

import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
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
@Tag(name = "Bank Management", description = "Endpoints for managing bank configurations (System Admin only).")
public class BankConfigurationController {

    private final BankConfigurationService bankConfigurationService;

    @PostMapping
    @PreAuthorize("hasAuthority('system:bank:write')")
    @Operation(summary = "Create a new bank configuration", description = "Initializes a new bank/tenant in the system.")
    public ResponseEntity<BankConfigurationResponse> createBank(@RequestBody BankConfigurationRequest request) {
        return new ResponseEntity<>(bankConfigurationService.createBank(request), HttpStatus.CREATED);
    }

    @PutMapping("/{bankId}")
    @PreAuthorize("hasAuthority('system:bank:write') or hasAuthority('bank:config:write')")
    @Operation(summary = "Update an existing bank configuration")
    public ResponseEntity<BankConfigurationResponse> updateBank(@PathVariable String bankId, @RequestBody BankConfigurationRequest request) {
        return ResponseEntity.ok(bankConfigurationService.updateBank(bankId, request));
    }

    @GetMapping("/{bankId}")
    @PreAuthorize("hasAuthority('system:bank:read') or hasAuthority('bank:config:read')")
    @Operation(summary = "Get bank configuration details")
    public ResponseEntity<BankConfigurationResponse> getBank(@PathVariable String bankId) {
        return ResponseEntity.ok(bankConfigurationService.getBank(bankId));
    }
}
