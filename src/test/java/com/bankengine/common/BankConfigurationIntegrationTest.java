package com.bankengine.common;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BankConfigurationRepository bankConfigurationRepository;

    @Autowired
    private com.bankengine.pricing.TestTransactionHelper txHelper;

    @Autowired
    private com.bankengine.auth.service.PermissionMappingService permissionMappingService;

    @BeforeEach
    void setup() {
        TenantContextHolder.setSystemMode(true);
        bankConfigurationRepository.deleteAll();

        // Seed SYSTEM_ADMIN role for SYSTEM bank
        TenantContextHolder.setBankId("SYSTEM");
        txHelper.getOrCreateRoleInDb("SYSTEM_ADMIN", java.util.Set.of("system:bank:write", "system:bank:read"));

        // Seed SUPER_ADMIN role for BANK_A
        TenantContextHolder.setBankId("BANK_A");
        txHelper.getOrCreateRoleInDb("SUPER_ADMIN", java.util.Set.of("bank:config:read", "bank:config:write"));

        txHelper.flushAndClear();
        // CRITICAL: Evict cache AFTER seeding so that WithMockRole (which might have failed earlier)
        // doesn't poison subsequent calls, though WithMockRole for the CURRENT method
        // has already executed.
        permissionMappingService.evictAllRolePermissionsCache();
        TenantContextHolder.clear();
    }

    @Test
    void systemAdmin_CanCreateAndSeeAllBanks() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("NEW_BANK", true, List.of());

        // Create a bank
        mockMvc.perform(post("/api/v1/banks")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("bank_id", "SYSTEM"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("system:bank:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));

        // System admin can see the bank configuration
        mockMvc.perform(get("/api/v1/banks/NEW_BANK")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("bank_id", "SYSTEM"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("system:bank:read"))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));
    }

    @Test
    void bankAdmin_CanOnlySeeOwnBank() throws Exception {
        // Setup another bank in DB
        TenantContextHolder.setSystemMode(true);
        BankConfiguration otherBank = new BankConfiguration();
        otherBank.setBankId("BANK_B");
        otherBank.setAllowProductInMultipleBundles(false);

        BankConfiguration ownBank = new BankConfiguration();
        ownBank.setBankId("BANK_A");
        ownBank.setAllowProductInMultipleBundles(true);

        bankConfigurationRepository.saveAll(List.of(otherBank, ownBank));
        TenantContextHolder.clear();

        // Bank A admin sees their own bank
        mockMvc.perform(get("/api/v1/banks/BANK_A")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("bank_id", "BANK_A"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("bank:config:read"))))
                .andDo(print())
                .andExpect(status().isOk());

        // Bank A admin cannot see Bank B (404 due to manual isolation in service)
        mockMvc.perform(get("/api/v1/banks/BANK_B")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("bank_id", "BANK_A"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("bank:config:read"))))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedUser_CannotCreateBank() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("FAIL", true, List.of());

        mockMvc.perform(post("/api/v1/banks")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("bank_id", "BANK_A"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("catalog:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
