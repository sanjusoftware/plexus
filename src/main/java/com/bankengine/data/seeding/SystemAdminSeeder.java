package com.bankengine.data.seeding;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Order(1)
public class SystemAdminSeeder implements CommandLineRunner {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final RoleRepository roleRepository;

    @Value("${app.security.system-bank-id}")
    private String systemBankId;

    @Value("${app.security.system-issuer}")
    private String systemIssuer;

    @Value("${app.security.system-bank-name}")
    private String systemBankName;

    @Value("${app.security.system-bank-admin-name}")
    private String systemAdminName;

    @Value("${app.security.system-bank-admin-email}")
    private String systemAdminEmail;

    @Value("${app.security.system-bank-admin-currency-code}")
    private String systemCurrencyCode;

    @Value("${springdoc.swagger-ui.oauth.client-id:}")
    private String defaultClientId;

    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("--- Seeding System Admin: " + systemBankId + " with ISSUER: "+ systemIssuer +" ---");

        // Normalize issuer to ensure matching with JwtAuthConverter
        String normalizedIssuer = systemIssuer.replaceAll("/$", "");

        TenantContextHolder.setSystemMode(true);
        try {
            // 1. Manage System Bank Configuration
            bankConfigurationRepository.findByBankIdUnfiltered(systemBankId).ifPresentOrElse(
                config -> {
                    boolean updated = false;
                    if (!normalizedIssuer.equalsIgnoreCase(config.getIssuerUrl())) {
                        config.setIssuerUrl(normalizedIssuer);
                        updated = true;
                        System.out.println("Updated issuer for " + systemBankId);
                    }
                    if (config.getClientId() == null || config.getClientId().isBlank()) {
                        config.setClientId(defaultClientId);
                        updated = true;
                        System.out.println("Updated client_id for " + systemBankId);
                    }
                    if (config.getStatus() != BankStatus.ACTIVE) {
                        config.setStatus(BankStatus.ACTIVE);
                        updated = true;
                    }
                    if (!systemBankName.equals(config.getName())) {
                        config.setName(systemBankName);
                        updated = true;
                    }
                    if (!systemAdminName.equals(config.getAdminName())) {
                        config.setAdminName(systemAdminName);
                        updated = true;
                    }
                    if (!systemAdminEmail.equals(config.getAdminEmail())) {
                        config.setAdminEmail(systemAdminEmail);
                        updated = true;
                    }
                    if (!systemCurrencyCode.equals(config.getCurrencyCode())) {
                        config.setCurrencyCode(systemCurrencyCode);
                        updated = true;
                    }
                    if (updated) {
                        bankConfigurationRepository.save(config);
                    }
                },
                () -> {
                    BankConfiguration config = new BankConfiguration();
                    config.setBankId(systemBankId);
                    config.setName(systemBankName);
                    config.setIssuerUrl(normalizedIssuer);
                    config.setClientId(defaultClientId);
                    config.setAllowProductInMultipleBundles(true);
                    config.setStatus(BankStatus.ACTIVE);
                    config.setAdminName(systemAdminName);
                    config.setAdminEmail(systemAdminEmail);
                    config.setCurrencyCode(systemCurrencyCode);
                    bankConfigurationRepository.save(config);
                    System.out.println("Created root bank: " + systemBankId + " with client_id: " + defaultClientId);
                }
            );

            // 2. Manage SYSTEM_ADMIN Role
            if (roleRepository.findByNameAndBankId("SYSTEM_ADMIN", systemBankId).isEmpty()) {
                Role admin = new Role();
                admin.setName("SYSTEM_ADMIN");
                admin.setBankId(systemBankId);
                admin.setAuthorities(Set.of(
                    "system:bank:write",
                    "system:bank:read",
                    "auth:role:write",
                    "auth:role:read"
                ));
                roleRepository.save(admin);
                System.out.println("Seeded SYSTEM_ADMIN for " + systemBankId);
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}