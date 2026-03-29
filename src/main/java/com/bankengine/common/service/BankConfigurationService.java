package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.common.annotation.SystemAdminBypass;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${springdoc.swagger-ui.oauth.client-id:}")
    private String defaultClientId;

    private String getSystemClientId() {
        return bankConfigurationRepository.findByBankIdUnfiltered(getSystemBankId())
                .map(BankConfiguration::getClientId)
                .orElse(defaultClientId);
    }

    @Transactional
    @SystemAdminBypass // Allows SYSTEM to create/update across tenants
    public BankConfigurationResponse createBank(BankConfigurationRequest request) {
        String bankId = deriveBankId(request);

        if (bankConfigurationRepository.findByBankIdUnfiltered(bankId).isPresent()) {
             throw new IllegalStateException("Bank already exists: " + bankId);
        }

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setName(request.getName());
        config.setIssuerUrl(request.getIssuerUrl() != null ? request.getIssuerUrl().replaceAll("/$", "") : null);
        config.setClientId(request.getClientId() != null && !request.getClientId().isBlank()
                ? request.getClientId() : getSystemClientId());
        config.setClientSecret(request.getClientSecret());
        config.setStatus(BankStatus.DRAFT);
        config.setAdminName(request.getAdminName());
        config.setAdminEmail(request.getAdminEmail());

        if (request.getCurrencyCode() != null) {
            config.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.getAllowProductInMultipleBundles() != null) {
            config.setAllowProductInMultipleBundles(request.getAllowProductInMultipleBundles());
        }

        if (request.getCategoryConflictRules() != null) {
            config.setCategoryConflictRules(request.getCategoryConflictRules().stream()
                    .map(dto -> new CategoryConflictRule(dto.getCategoryA(), dto.getCategoryB()))
                    .collect(Collectors.toList()));
        }

        bankConfigurationRepository.save(config);

        return mapToResponse(config);
    }

    private String deriveBankId(BankConfigurationRequest request) {
        if (request.getBankId() != null && !request.getBankId().isBlank()) {
            return CodeGeneratorUtil.sanitizeAsCode(request.getBankId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            return CodeGeneratorUtil.sanitizeAsCode(request.getName());
        }
        throw new IllegalArgumentException("Bank ID or Name is required to derive Bank ID.");
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse submitOnboarding(BankConfigurationRequest request) {
        String bankId = deriveBankId(request);

        if (bankConfigurationRepository.findByBankIdUnfiltered(bankId).isPresent()) {
            throw new IllegalStateException("Bank ID already in use: " + bankId);
        }

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .name(request.getName())
                .issuerUrl(request.getIssuerUrl() != null ? request.getIssuerUrl().replaceAll("/$", "") : null)
                .clientId(request.getClientId())
                .currencyCode(request.getCurrencyCode())
                .adminName(request.getAdminName())
                .adminEmail(request.getAdminEmail())
                .status(BankStatus.DRAFT)
                .allowProductInMultipleBundles(true)
                .build();

        bankConfigurationRepository.save(config);
        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse updateBank(BankConfigurationRequest request) {
        String bankId = (request.getBankId() != null && getSystemBankId().equals(getCurrentBankId()))
                ? request.getBankId() : getCurrentBankId();

        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (config.getStatus() != BankStatus.DRAFT) {
            throw new IllegalStateException("Only banks in DRAFT status can be edited.");
        }

        if (request.getName() != null) {
            config.setName(request.getName());
        }

        if (request.getIssuerUrl() != null) {
            config.setIssuerUrl(request.getIssuerUrl().replaceAll("/$", ""));
        }

        if (request.getClientId() != null) {
            config.setClientId(request.getClientId().isBlank() ? getSystemClientId() : request.getClientId());
        }

        if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
            config.setClientSecret(request.getClientSecret());
        }

        if (request.getCurrencyCode() != null) {
            config.setCurrencyCode(request.getCurrencyCode());
        }

        if (request.getAllowProductInMultipleBundles() != null) {
            config.setAllowProductInMultipleBundles(request.getAllowProductInMultipleBundles());
        }

        if (request.getAdminName() != null) {
            config.setAdminName(request.getAdminName());
        }

        if (request.getAdminEmail() != null) {
            config.setAdminEmail(request.getAdminEmail());
        }

        if (request.getCategoryConflictRules() != null) {
            config.getCategoryConflictRules().clear();
            config.getCategoryConflictRules().addAll(request.getCategoryConflictRules().stream()
                    .map(dto -> new CategoryConflictRule(dto.getCategoryA(), dto.getCategoryB()))
                    .toList());
        }

        bankConfigurationRepository.save(config);
        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse activateBank(String bankId) {
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));
        config.setStatus(BankStatus.ACTIVE);
        bankConfigurationRepository.save(config);

        if (roleRepository.findByNameAndBankId("BANK_ADMIN", bankId).isEmpty()) {
            createBankAdminRole(bankId);
        }

        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse rejectBank(String bankId) {
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (config.getStatus() != BankStatus.DRAFT) {
            throw new IllegalStateException("Only banks in DRAFT status can be rejected.");
        }

        config.setStatus(BankStatus.REJECTED);
        bankConfigurationRepository.save(config);

        roleRepository.findByNameAndBankId("BANK_ADMIN", bankId).ifPresent(roleRepository::delete);

        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse deactivateBank(String bankId) {
        if (bankId.equals(getCurrentBankId())) {
            throw new IllegalStateException("System Admin cannot deactivate their own bank.");
        }
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (config.getStatus() != BankStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE banks can be deactivated.");
        }

        config.setStatus(BankStatus.INACTIVE);
        bankConfigurationRepository.save(config);
        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public java.util.List<BankConfigurationResponse> getAllBanks() {
        String currentBankId = getCurrentBankId();
        if (!getSystemBankId().equals(currentBankId)) {
            throw new org.springframework.security.access.AccessDeniedException("System Admin authority required.");
        }
        return bankConfigurationRepository.findAll().stream()
                .filter(b -> !b.getBankId().equals(currentBankId))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankConfigurationResponse getBank(String bankId) {
        validateTenantAccess(bankId);
        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankConfigurationResponse getPublicBankConfig(String bankId) {
        // Unfiltered because the user is not yet authenticated, so no tenant context
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        return BankConfigurationResponse.builder()
                .bankId(config.getBankId())
                .issuerUrl(config.getIssuerUrl())
                .clientId(config.getClientId())
                .hasClientSecret(config.getClientSecret() != null && !config.getClientSecret().isBlank())
                .build();
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
        bankAuthorities.add("bank:stats:read");

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
                .name(config.getName())
                .allowProductInMultipleBundles(config.isAllowProductInMultipleBundles())
                .issuerUrl(config.getIssuerUrl())
                .clientId(config.getClientId())
                .hasClientSecret(config.getClientSecret() != null && !config.getClientSecret().isBlank())
                .categoryConflictRules(config.getCategoryConflictRules().stream()
                        .map(r -> new BankConfigurationRequest.CategoryConflictDto(r.getCategoryA(), r.getCategoryB()))
                        .collect(Collectors.toList()))
                .currencyCode(config.getCurrencyCode())
                .status(config.getStatus().name())
                .adminName(config.getAdminName())
                .adminEmail(config.getAdminEmail())
                .build();
    }
}
