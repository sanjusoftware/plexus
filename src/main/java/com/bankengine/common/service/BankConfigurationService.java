package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankConfigurationService extends BaseService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final RoleRepository roleRepository;
    private final AuthorityDiscoveryService authorityDiscoveryService;
    private final PermissionMappingService permissionMappingService;

    @Transactional
    public BankConfigurationResponse createBank(BankConfigurationRequest request) {
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

        // Create BANK_ADMIN role for the new bank
        createBankAdminRole(request.getBankId());

        return mapToResponse(config);
    }

    @Transactional
    public BankConfigurationResponse updateBank(String bankId, BankConfigurationRequest request) {
        BankConfiguration config = bankConfigurationRepository.findTenantAwareByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        validateTenantAccess(config.getBankId());
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
    public BankConfigurationResponse getBank(String bankId) {
        BankConfiguration config = bankConfigurationRepository.findTenantAwareByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        validateTenantAccess(config.getBankId());

        return mapToResponse(config);
    }

    private void validateTenantAccess(String bankId) {
        String currentBankId = getCurrentBankId();
        if (!"SYSTEM".equals(currentBankId) && !currentBankId.equals(bankId)) {
            throw new NotFoundException("Bank configuration not found for: " + bankId);
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
