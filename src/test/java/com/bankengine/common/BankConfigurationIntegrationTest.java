package com.bankengine.common;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.bankengine.test.config.WithSystemAdminRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BankConfigurationRepository bankConfigurationRepository;
    @Autowired
    private TestTransactionHelper txHelper;

    public static final String ROLE_PREFIX = "BCIT_";
    private static final String SYSTEM_ADMIN_ROLE = ROLE_PREFIX + "SYSTEM_ADMIN";
    private static final String BANK_A_ADMIN_ROLE = ROLE_PREFIX + "BANK_A_ADMIN";
    private static final String UNAUTHORIZED_ROLE = ROLE_PREFIX + "GUEST";

    // Standard test issuers
    private static final String ISSUER_A = "https://login.microsoftonline.com/tenant-a/v2.0";
    private static final String ISSUER_B = "https://login.microsoftonline.com/tenant-b/v2.0";
    private static final String NEW_ISSUER = "https://login.microsoftonline.com/new-tenant/v2.0";

    @BeforeAll
    static void setupCommittedData(@Autowired TestTransactionHelper txHelperStatic) {
        txHelperStatic.saveBankConfiguration("SYSTEM", "https://login.microsoftonline.com/system-tenant/v2.0");
        seedBaseRoles(txHelperStatic, Map.of(
                SYSTEM_ADMIN_ROLE, Set.of("system:bank:write", "system:bank:read"),
                BANK_A_ADMIN_ROLE, Set.of("bank:config:read", "bank:config:write"),
                UNAUTHORIZED_ROLE, Set.of("catalog:read")
        ));
    }

    @BeforeEach
    void setup() {
        try {
            TenantContextHolder.setSystemMode(true);
            bankConfigurationRepository.deleteAllInBatch();
            bankConfigurationRepository.flush();
            entityManager.clear();
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
        TenantContextHolder.clear();
    }

    @Test
    @WithSystemAdminRole
    void systemAdmin_CanCreateAndSeeAllBanks() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("NEW_BANK", true, List.of(), NEW_ISSUER);

        // Create a bank
        mockMvc.perform(post("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));

        // System admin can see the bank configuration
        mockMvc.perform(get("/api/v1/banks/NEW_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"))
                .andExpect(jsonPath("$.issuerUrl").value(NEW_ISSUER));
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_CanOnlySeeOwnBank() throws Exception {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setSystemMode(true);

            BankConfiguration otherBank = new BankConfiguration();
            otherBank.setBankId("BANK_B");
            otherBank.setIssuerUrl(ISSUER_B);
            otherBank.setAllowProductInMultipleBundles(false);

            BankConfiguration ownBank = new BankConfiguration();
            ownBank.setBankId("BANK_A");
            ownBank.setIssuerUrl(ISSUER_A);
            ownBank.setAllowProductInMultipleBundles(true);

            bankConfigurationRepository.saveAll(List.of(otherBank, ownBank));
            return null;
        });

        mockMvc.perform(get("/api/v1/banks/BANK_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("BANK_A"));

        mockMvc.perform(get("/api/v1/banks/BANK_B"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {UNAUTHORIZED_ROLE})
    void unauthorizedUser_CannotCreateBank() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("FAIL", true, List.of(), "https://fail-issuer.com");

        mockMvc.perform(post("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_UpdatingOtherBank_Returns404Not403() throws Exception {
        BankConfigurationRequest request = new BankConfigurationRequest("BANK_B", true, List.of(), ISSUER_A);

        mockMvc.perform(get("/api/v1/banks/BANK_B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithSystemAdminRole
    void systemAdmin_CanUpdateBank() throws Exception {
        txHelper.doInTransaction(() -> {
            try {
                TenantContextHolder.setSystemMode(true);
                BankConfiguration bank = new BankConfiguration();
                bank.setBankId("UPDATE_TEST");
                bank.setIssuerUrl(ISSUER_A);
                bank.setAllowProductInMultipleBundles(false);
                bankConfigurationRepository.save(bank);
                return null;
            } finally {
                TenantContextHolder.setSystemMode(false);
            }
        });

        // 2. Update via API
        BankConfigurationRequest updateRequest = new BankConfigurationRequest("UPDATE_TEST", true, List.of(), ISSUER_A);

        mockMvc.perform(put("/api/v1/banks/UPDATE_TEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowProductInMultipleBundles").value(true));
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_CanUpdateOwnBank() throws Exception {
        txHelper.doInTransaction(() -> {
            try {
                TenantContextHolder.setSystemMode(true);
                BankConfiguration bank = new BankConfiguration();
                bank.setBankId("BANK_A");
                bank.setIssuerUrl(ISSUER_A);
                bank.setAllowProductInMultipleBundles(false);
                bankConfigurationRepository.save(bank);
                return null;
            } finally {
                TenantContextHolder.setSystemMode(false);
            }
        });

        BankConfigurationRequest request = new BankConfigurationRequest("BANK_A", true, List.of(), ISSUER_A);

        mockMvc.perform(put("/api/v1/banks/BANK_A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithSystemAdminRole
    void systemAdmin_CannotCreateDuplicateIssuer() throws Exception {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setSystemMode(true);
            BankConfiguration bank = new BankConfiguration();
            bank.setBankId("EXISTING_BANK");
            bank.setIssuerUrl(ISSUER_A);
            bankConfigurationRepository.save(bank);
            return null;
        });

        BankConfigurationRequest request = new BankConfigurationRequest("DUPLICATE_BANK", true, List.of(), ISSUER_A);
        mockMvc.perform(post("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}