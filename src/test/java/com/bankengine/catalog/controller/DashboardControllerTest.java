package com.bankengine.catalog.controller;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestTransactionHelper txHelper;

    @Test
    void getLocalStats_AsBankAdmin_ReturnsLocalStats() throws Exception {
        txHelper.doInTransaction(() -> {
            txHelper.createValidProduct("Product 1", "LOAN", VersionableEntity.EntityStatus.ACTIVE);
            txHelper.createValidProduct("Product 2", "CASA", VersionableEntity.EntityStatus.DRAFT);
            txHelper.getOrCreateRoleInDb("BANK_ADMIN", Set.of("read"));
        });

        OAuth2User principal = new DefaultOAuth2User(
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("bank:stats:read"),
                Map.of("sub", "user"),
                "sub"
        );
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), TEST_BANK_ID);

        mockMvc.perform(get("/api/v1/dashboard/stats/local").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.ACTIVE", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.products.DRAFT", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.roles.ACTIVE", greaterThanOrEqualTo(1)));
    }

    @Test
    void getGlobalStats_AsSystemAdmin_ReturnsGlobalStats() throws Exception {
        // Setup Local Bank A
        txHelper.doInTransaction(() -> {
            txHelper.createValidProduct("Local Product", "LOAN", VersionableEntity.EntityStatus.ACTIVE);
        });

        // Setup Bank B (External)
        try {
            TenantContextHolder.setBankId("BANK_B");
            txHelper.doInTransaction(() -> {
                txHelper.createValidProduct("Bank B Product", "CASA", VersionableEntity.EntityStatus.ACTIVE);
                txHelper.saveBankConfiguration("BANK_B", "http://bank-b");
            });
        } finally {
            TenantContextHolder.setBankId(TEST_BANK_ID);
        }

        OAuth2User principal = new DefaultOAuth2User(
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("system:stats:read"),
                Map.of("sub", "admin"),
                "sub"
        );
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), TEST_BANK_ID);

        mockMvc.perform(get("/api/v1/dashboard/stats/global").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.ACTIVE", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalBanks", greaterThanOrEqualTo(2)));
    }

    @Test
    void getGlobalStats_AsBankAdmin_ReturnsForbidden() throws Exception {
        OAuth2User principal = new DefaultOAuth2User(
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("bank:stats:read"),
                Map.of("sub", "user"),
                "sub"
        );
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), TEST_BANK_ID);

        mockMvc.perform(get("/api/v1/dashboard/stats/global").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
