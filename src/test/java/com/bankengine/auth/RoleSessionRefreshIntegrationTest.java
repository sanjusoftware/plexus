package com.bankengine.auth;

import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleSessionRefreshIntegrationTest extends AbstractIntegrationTest {

    private static final String ROLE_API = "/api/v1/roles";
    private static final String AUTH_USER_API = "/api/v1/auth/user";
    private static final String ADMIN_ROLE = "ADMIN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestTransactionHelper txHelper;

    @Autowired
    private BankConfigurationRepository bankConfigurationRepository;

    @BeforeEach
    void seedBankAndAdminRole() {
        txHelper.doInTransaction(() -> {
            txHelper.saveBankConfiguration(TEST_BANK_ID, TEST_BANK_ISS_URL);
            txHelper.getOrCreateRoleInDb(ADMIN_ROLE, Set.of("auth:role:write", "auth:role:read"));
        });

        try {
            com.bankengine.auth.security.TenantContextHolder.setSystemMode(true);
            bankConfigurationRepository.findByBankIdUnfiltered(TEST_BANK_ID).ifPresent(cfg -> {
                cfg.setStatus(BankStatus.ACTIVE);
                cfg.setName("Test Bank");
                bankConfigurationRepository.save(cfg);
            });
        } finally {
            com.bankengine.auth.security.TenantContextHolder.setSystemMode(false);
        }
    }

    @Test
    void updatingRoleUsedByCurrentUser_refreshesAuthUserPermissionsInSameSession() throws Exception {
        String targetRole = "SELF_REFRESH_UPDATE";

        txHelper.doInTransaction(() ->
                txHelper.getOrCreateRoleInDb(targetRole, Set.of("catalog:product:read"))
        );

        MockHttpSession session = createAuthenticatedSession(
                List.of(ADMIN_ROLE, targetRole),
                Set.of("auth:role:write", "auth:role:read", "catalog:product:read")
        );

        mockMvc.perform(get(AUTH_USER_API).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("catalog:product:read")))
                .andExpect(jsonPath("$.permissions", not(hasItem("pricing:metadata:read"))));

        RoleAuthorityMappingDto updateDto = new RoleAuthorityMappingDto();
        updateDto.setRoleName(targetRole);
        updateDto.setAuthorities(Set.of("pricing:metadata:read"));

        mockMvc.perform(postWithCsrf(ROLE_API + "/mapping")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(AUTH_USER_API).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("pricing:metadata:read")))
                .andExpect(jsonPath("$.permissions", not(hasItem("catalog:product:read"))))
                .andExpect(jsonPath("$.permissions", hasItem("auth:role:write")));
    }

    @Test
    void deletingRoleUsedByCurrentUser_refreshesAuthUserPermissionsInSameSession() throws Exception {
        String targetRole = "SELF_REFRESH_DELETE";

        txHelper.doInTransaction(() ->
                txHelper.getOrCreateRoleInDb(targetRole, Set.of("pricing:component:read"))
        );

        MockHttpSession session = createAuthenticatedSession(
                List.of(ADMIN_ROLE, targetRole),
                Set.of("auth:role:write", "auth:role:read", "pricing:component:read")
        );

        mockMvc.perform(get(AUTH_USER_API).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("pricing:component:read")));

        mockMvc.perform(deleteWithCsrf(ROLE_API + "/{roleName}", targetRole)
                        .session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied: You cannot delete a role assigned to your own account."));

        mockMvc.perform(get(AUTH_USER_API).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("pricing:component:read")))
                .andExpect(jsonPath("$.permissions", hasItem("auth:role:write")));
    }

    private MockHttpSession createAuthenticatedSession(List<String> roles, Set<String> authorities) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "session-user");
        attributes.put("name", "Session User");
        attributes.put("preferred_username", "session.user@email");
        attributes.put("bank_id", TEST_BANK_ID);
        attributes.put("roles", roles);
        attributes.put("iss", TEST_BANK_ISS_URL);
        attributes.put("azp", "test-client-id-" + TEST_BANK_ID);

        Set<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        OAuth2User principal = new DefaultOAuth2User(grantedAuthorities, attributes, "sub");
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, grantedAuthorities, TEST_BANK_ID);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}

