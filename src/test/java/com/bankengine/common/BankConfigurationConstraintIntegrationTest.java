package com.bankengine.common;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithSystemAdminRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankConfigurationConstraintIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BankConfigurationRepository bankConfigurationRepository;
    @Autowired
    private TestTransactionHelper txHelper;

    private static final String ISSUER_A = "https://login.microsoftonline.com/tenant-a/v2.0";
    private static final String CLIENT_ID_A = "CLIENT_ID_A";

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
    void shouldReturnUserFriendlyMessage_WhenDuplicateBankId() throws Exception {
        createBank("BANK_A", ISSUER_A, CLIENT_ID_A);

        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .name("Another Bank")
                .bankId("BANK_A")
                .issuerUrl("https://another-issuer.com")
                .clientId("ANOTHER_CLIENT")
                .adminName("Admin")
                .adminEmail("admin@banka.com")
                .build();

        mockMvc.perform(postWithCsrf("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bank ID should be unique"));
    }

    @Test
    @WithSystemAdminRole
    void shouldReturnUserFriendlyMessage_WhenDuplicateIssuerAndClientId() throws Exception {
        createBank("BANK_A", ISSUER_A, CLIENT_ID_A);

        BankConfigurationRequest request = BankConfigurationRequest.builder()
                .name("Bank B")
                .bankId("BANK_B")
                .issuerUrl(ISSUER_A)
                .clientId(CLIENT_ID_A)
                .adminName("Admin B")
                .adminEmail("admin@bankb.com")
                .build();

        mockMvc.perform(postWithCsrf("/api/v1/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A bank with this Issuer URL and Client ID combination already exists."));
    }

    private void createBank(String bankId, String issuerUrl, String clientId) {
        txHelper.doInTransaction(() -> {
            try {
                TenantContextHolder.setSystemMode(true);
                BankConfiguration bank = new BankConfiguration();
                bank.setBankId(bankId);
                bank.setIssuerUrl(issuerUrl);
                bank.setClientId(clientId);
                bank.setAdminName("Admin");
                bank.setAdminEmail("admin@test.com");
                bank.setName("Test Bank");
                bankConfigurationRepository.save(bank);
                return null;
            } finally {
                TenantContextHolder.setSystemMode(false);
            }
        });
    }
}
