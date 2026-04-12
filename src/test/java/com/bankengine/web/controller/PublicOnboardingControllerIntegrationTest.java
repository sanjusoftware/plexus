package com.bankengine.web.controller;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicOnboardingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BankConfigurationRepository bankConfigurationRepository;

    @BeforeEach
    void cleanBanks() {
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
    void submitOnboarding_WhenClientIdMissing_ShouldReturn422() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CAPTCHA_test-captcha", 8);

        String payload = """
                {
                  "bankDetails": {
                    "bankId": "BANK_X",
                    "name": "Bank X",
                    "issuerUrl": "https://issuer-x.com",
                    "clientId": "   ",
                    "currencyCode": "INR",
                    "adminName": "Admin X",
                    "adminEmail": "admin@bankx.com"
                  },
                  "captchaId": "test-captcha",
                  "captchaAnswer": "8"
                }
                """;

        mockMvc.perform(postWithCsrf("/api/v1/public/onboarding")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Client ID is required for onboarding."));
    }

    @Test
    void submitOnboarding_WithExplicitClientId_ShouldCreateBank() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CAPTCHA_test-captcha", 9);

        String payload = """
                {
                  "bankDetails": {
                    "bankId": "BANK_Y",
                    "name": "Bank Y",
                    "issuerUrl": "https://issuer-y.com/",
                    "clientId": "client-y",
                    "currencyCode": "INR",
                    "adminName": "Admin Y",
                    "adminEmail": "admin@banky.com"
                  },
                  "captchaId": "test-captcha",
                  "captchaAnswer": "9"
                }
                """;

        mockMvc.perform(postWithCsrf("/api/v1/public/onboarding")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankId").value("BANK_Y"))
                .andExpect(jsonPath("$.clientId").value("client-y"));

        try {
            TenantContextHolder.setSystemMode(true);
            BankConfiguration saved = bankConfigurationRepository.findByBankIdUnfiltered("BANK_Y").orElse(null);
            assertNotNull(saved);
            assertEquals("client-y", saved.getClientId());
            assertEquals("https://issuer-y.com", saved.getIssuerUrl());
        } finally {
            TenantContextHolder.setSystemMode(false);
            TenantContextHolder.clear();
        }
    }
}

