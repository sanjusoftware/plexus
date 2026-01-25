package com.bankengine.common;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BankConfigurationRepository bankConfigurationRepository;
    @Autowired private com.bankengine.pricing.TestTransactionHelper txHelper;
    @Autowired private com.bankengine.auth.service.PermissionMappingService permissionMappingService;

    // Pattern: Define Role Constants with unique prefixes
    public static final String ROLE_PREFIX = "BCIT_";
    private static final String SYSTEM_ADMIN_ROLE = ROLE_PREFIX + "SYSTEM_ADMIN";
    private static final String BANK_A_ADMIN_ROLE = ROLE_PREFIX + "BANK_A_ADMIN";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "GUEST";

    @BeforeAll
    static void setupCommittedData(@Autowired com.bankengine.pricing.TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            SYSTEM_ADMIN_ROLE, Set.of("system:bank:write", "system:bank:read"),
            BANK_A_ADMIN_ROLE, Set.of("bank:config:read", "bank:config:write"),
            UNAUTHORIZED_ROLE, Set.of("catalog:read")
        ));
    }

    @BeforeEach
    void setup() {
        TenantContextHolder.setSystemMode(true);
        bankConfigurationRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @WithMockRole(roles = {SYSTEM_ADMIN_ROLE}, bankId = "SYSTEM")
    void systemAdmin_CanCreateAndSeeAllBanks() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("NEW_BANK", true, List.of());

        // Create a bank
        mockMvc.perform(post("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));

        // System admin can see the bank configuration
        mockMvc.perform(get("/api/v1/banks/NEW_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));
    }

    // --- 2. BANK ADMIN TESTS ---

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE})
    void bankAdmin_CanOnlySeeOwnBank() throws Exception {
        // Setup data manually in DB
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setSystemMode(true);

            BankConfiguration otherBank = new BankConfiguration();
            otherBank.setBankId("BANK_B");
            otherBank.setAllowProductInMultipleBundles(false);

            BankConfiguration ownBank = new BankConfiguration();
            ownBank.setBankId("BANK_A");
            ownBank.setAllowProductInMultipleBundles(true);

            bankConfigurationRepository.saveAll(List.of(otherBank, ownBank));
            return null;
        });

        // Bank A admin sees their own bank
        mockMvc.perform(get("/api/v1/banks/BANK_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("BANK_A"));

        // Bank A admin cannot see Bank B
        mockMvc.perform(get("/api/v1/banks/BANK_B"))
                .andExpect(status().isNotFound());
    }

    // --- 3. NEGATIVE TESTS ---

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void unauthorizedUser_CannotCreateBank() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("FAIL", true, List.of());

        mockMvc.perform(post("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}