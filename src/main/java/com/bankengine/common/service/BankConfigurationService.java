package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.annotation.SystemAdminBypass;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConfigurationService extends BaseService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final RoleRepository roleRepository;
    private final AuthorityDiscoveryService authorityDiscoveryService;
    private final PermissionMappingService permissionMappingService;

    @Transactional
    @SystemAdminBypass // Allows SYSTEM to create/update across tenants
    public BankConfigurationResponse createBank(BankConfigurationRequest request) {
        if (bankConfigurationRepository.findByBankId(request.getBankId()).isPresent()) {
             throw new IllegalStateException("Bank already exists: " + request.getBankId());
        }

        BankConfiguration config = new BankConfiguration();
        config.setBankId(request.getBankId());
        config.setIssuerUrl(request.getIssuerUrl());
        config.setAllowProductInMultipleBundles(request.isAllowProductInMultipleBundles());

        if (request.getCategoryConflictRules() != null) {
            config.setCategoryConflictRules(request.getCategoryConflictRules().stream()
                    .map(dto -> new CategoryConflictRule(dto.getCategoryA(), dto.getCategoryB()))
                    .collect(Collectors.toList()));
        }

        bankConfigurationRepository.save(config);
        createBankAdminRole(request.getBankId());

        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse updateBank(String bankId, BankConfigurationRequest request) {
        validateTenantAccess(bankId);
        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (request.getIssuerUrl() != null) {
            config.setIssuerUrl(request.getIssuerUrl());
        }
        config.setAllowProductInMultipleBundles(request.isAllowProductInMultipleBundles());

        if (request.getCategoryConflictRules() != null) {
            config.getCategoryConflictRules().clear();
            config.getCategoryConflictRules().addAll(request.getCategoryConflictRules().stream()
                    .map(dto -> new CategoryConflictRule(dto.getCategoryA(), dto.getCategoryB()))
                    .collect(Collectors.toList()));
        }

        bankConfigurationRepository.save(config);
        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankConfigurationResponse getBank(String bankId) {
        validateTenantAccess(bankId);
        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        return mapToResponse(config);
    }

    private void validateTenantAccess(String requestedBankId) {
        String currentBankId = getCurrentBankId();
        // If not SYSTEM and not the owner, it's a 404
        if (!getSystemBankId().equals(currentBankId) && !currentBankId.equals(requestedBankId)) {
            log.warn("[SECURITY] Tenant mismatch! User {} tried to access {}", currentBankId, requestedBankId);
            throw new NotFoundException("Bank configuration not found for: " + requestedBankId);
        }
    }

    private void createBankAdminRole(String bankId) {
        Set<String> allAuthorities = authorityDiscoveryService.discoverAllAuthorities();

        // Filter out system-level authorities for bank-level super admins to ensure proper isolation
        Set<String> bankAuthorities = allAuthorities.stream()
                .filter(auth -> !auth.startsWith("system:"))
                .collect(Collectors.toSet());

        // Add bank-specific configuration permissions
        bankAuthorities.add("bank:config:read");
        bankAuthorities.add("bank:config:write");

        Role superAdmin = new Role();
        superAdmin.setName("BANK_ADMIN");
        superAdmin.setBankId(bankId);
        superAdmin.setAuthorities(new HashSet<>(bankAuthorities));
        roleRepository.save(superAdmin);

        // Evict cache to ensure new role is recognized
        permissionMappingService.evictAllRolePermissionsCache();
    }

    private BankConfigurationResponse mapToResponse(BankConfiguration config) {
        return BankConfigurationResponse.builder()
                .bankId(config.getBankId())
                .allowProductInMultipleBundles(config.isAllowProductInMultipleBundles())
                .issuerUrl(config.getIssuerUrl())
                .categoryConflictRules(config.getCategoryConflictRules().stream()
                        .map(r -> new BankConfigurationRequest.CategoryConflictDto(r.getCategoryA(), r.getCategoryB()))
                        .collect(Collectors.toList()))
                .build();
    }
}
