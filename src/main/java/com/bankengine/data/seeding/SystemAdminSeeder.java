package com.bankengine.data.seeding;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Order(1) // Run before other seeders
public class SystemAdminSeeder implements CommandLineRunner {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("--- Seeding System Admin Data ---");

        String systemBankId = "SYSTEM";

        TenantContextHolder.setSystemMode(true);
        try {
            if (bankConfigurationRepository.findTenantAwareByBankId(systemBankId).isEmpty()) {
                BankConfiguration systemBank = new BankConfiguration();
                systemBank.setBankId(systemBankId);
                systemBank.setAllowProductInMultipleBundles(true);
                systemBank.setIssuerUrl("https://internal.bankengine.system/auth");

                bankConfigurationRepository.save(systemBank);
                System.out.println("Seeded SYSTEM bank.");
            }

            if (roleRepository.findByName("SYSTEM_ADMIN").isEmpty()) {
                Role systemAdmin = new Role();
                systemAdmin.setName("SYSTEM_ADMIN");
                systemAdmin.setBankId(systemBankId);
                systemAdmin.setAuthorities(Set.of(
                        "system:bank:write",
                        "system:bank:read",
                        "auth:role:write",
                        "auth:role:read"
                ));
                roleRepository.save(systemAdmin);
                System.out.println("Seeded SYSTEM_ADMIN role.");
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}