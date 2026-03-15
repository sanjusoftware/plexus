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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
    private static final String CLIENT_ID_A = "CLIENT_ID_A";
    private static final String ISSUER_B = "https://login.microsoftonline.com/tenant-b/v2.0";
    private static final String CLIENT_ID_B = "CLIENT_ID_B";
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
        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .name("New Bank")
                .bankId("NEW_BANK")
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(List.of())
                .issuerUrl(NEW_ISSUER)
                .build();

        // Create a bank
        mockMvc.perform(postWithCsrf("/api/v1/banks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"));

        // System admin can see the bank configuration
        mockMvc.perform(get("/api/v1/banks/NEW_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("NEW_BANK"))
                .andExpect(jsonPath("$.issuerUrl").value(NEW_ISSUER));

        // System admin can list all banks
        mockMvc.perform(get("/api/v1/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bankId").value("NEW_BANK"));
    }

    @Test
    @WithSystemAdminRole
    void systemAdmin_CanUpdateBankById() throws Exception {
        createBank("ID_UPDATE_TEST");
        BankConfigurationRequest updateRequest = BankConfigurationRequest.builder()
                .allowProductInMultipleBundles(true)
                .issuerUrl(NEW_ISSUER)
                .build();

        mockMvc.perform(putWithCsrf("/api/v1/banks/ID_UPDATE_TEST")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("ID_UPDATE_TEST"))
                .andExpect(jsonPath("$.issuerUrl").value(NEW_ISSUER))
                .andExpect(jsonPath("$.allowProductInMultipleBundles").value(true));
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_CanOnlySeeOwnBank() throws Exception {
        txHelper.doInTransaction(() -> {
            TenantContextHolder.setSystemMode(true);

            BankConfiguration otherBank = new BankConfiguration();
            otherBank.setBankId("BANK_B");
            otherBank.setIssuerUrl(ISSUER_B);
            otherBank.setClientId(CLIENT_ID_B);
            otherBank.setAllowProductInMultipleBundles(false);

            BankConfiguration ownBank = new BankConfiguration();
            ownBank.setBankId("BANK_A");
            ownBank.setIssuerUrl(ISSUER_A);
            ownBank.setClientId(CLIENT_ID_A);
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
        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .bankId("FAIL")
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(List.of())
                .issuerUrl("https://fail-issuer.com")
                .build();

        mockMvc.perform(postWithCsrf("/api/v1/banks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_UpdatingOtherBank_Returns404Not403() throws Exception {
        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .bankId("BANK_B")
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(List.of())
                .issuerUrl(ISSUER_A)
                .build();

        mockMvc.perform(get("/api/v1/banks/BANK_B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithSystemAdminRole
    void systemAdmin_CanUpdateBank() throws Exception {
        createBank("UPDATE_TEST");
        BankConfigurationRequest updateRequest = BankConfigurationRequest.builder()
                .name("Updated Bank")
                .bankId("UPDATE_TEST")
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(List.of())
                .issuerUrl(ISSUER_A)
                .build();

        mockMvc.perform(putWithCsrf("/api/v1/banks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowProductInMultipleBundles").value(true));
    }

    @Test
    @WithMockRole(roles = {BANK_A_ADMIN_ROLE}, bankId = "BANK_A")
    void bankAdmin_CanUpdateOwnBank() throws Exception {
        createBank("BANK_A");
        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .bankId("BANK_A")
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(List.of())
                .issuerUrl(ISSUER_A)
                .build();

        mockMvc.perform(putWithCsrf("/api/v1/banks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

//    @Test
//    @WithSystemAdminRole
//    void systemAdmin_CannotCreateDuplicateIssuer() throws Exception {
//        createBank("EXISTING_BANK");
//        BankConfigurationRequest request = BankConfigurationRequest.builder()
//                .name("Duplicate Bank")
//                .bankId("DUPLICATE_BANK")
//                .allowProductInMultipleBundles(true)
//                .categoryConflictRules(List.of())
//                .issuerUrl(ISSUER_A)
//                .build();
//        mockMvc.perform(postWithCsrf("/api/v1/banks")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().is4xxClientError());
//    }

    @Test
    void whenUntrustedIssuer_shouldReturn401Json() throws Exception {
        // A structurally valid but untrusted JWT (Header.Payload.Signature)
        // Payload contains "iss": "https://untrusted-issuer.com"
        String validLookingJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpc3MiOiJodHRwczovL3VudHJ1c3RlZC1pc3N1ZXIuY29tIn0." +
                "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        mockMvc.perform(get("/api/v1/banks/SOME_BANK")
                        .header("Authorization", "Bearer " + validLookingJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                // This validates your specific log/error message logic
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Untrusted issuer")));
    }

    private void createBank(String bank_id) {
        txHelper.doInTransaction(() -> {
            try {
                TenantContextHolder.setSystemMode(true);
                BankConfiguration bank = new BankConfiguration();
                bank.setBankId(bank_id);
                bank.setIssuerUrl(ISSUER_A);
                bank.setClientId(CLIENT_ID_A);
                bank.setAllowProductInMultipleBundles(false);
                bankConfigurationRepository.save(bank);
                return null;
            } finally {
                TenantContextHolder.setSystemMode(false);
            }
        });
    }

}
