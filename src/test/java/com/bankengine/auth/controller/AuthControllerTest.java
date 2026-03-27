package com.bankengine.auth.controller;

import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNameWhenPresent() throws Exception {
        OAuth2User principal = createPrincipal("John Doe", "john-sub");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Collections.emptyList(), "test-client");

        mockMvc.perform(get("/api/v1/auth/user").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.sub").value("john-sub"));
    }

    @Test
    void shouldFallbackToSubWhenNameIsUnknownUser() throws Exception {
        OAuth2User principal = createPrincipal("unknown user", "Sanjeev - Product Manager");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Collections.emptyList(), "test-client");

        mockMvc.perform(get("/api/v1/auth/user").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sanjeev - Product Manager"))
                .andExpect(jsonPath("$.sub").value("Sanjeev - Product Manager"));
    }

    @Test
    void shouldFallbackToSubWhenNameIsNull() throws Exception {
        OAuth2User principal = createPrincipal(null, "Sanjeev - Product Manager");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Collections.emptyList(), "test-client");

        mockMvc.perform(get("/api/v1/auth/user").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sanjeev - Product Manager"))
                .andExpect(jsonPath("$.sub").value("Sanjeev - Product Manager"));
    }

    @Test
    void shouldFallbackToSubWhenNameIsBlank() throws Exception {
        OAuth2User principal = createPrincipal("  ", "Sanjeev - Product Manager");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Collections.emptyList(), "test-client");

        mockMvc.perform(get("/api/v1/auth/user").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sanjeev - Product Manager"))
                .andExpect(jsonPath("$.sub").value("Sanjeev - Product Manager"));
    }

    @Test
    void shouldKeepUnknownUserIfSubIsAlsoMissing() throws Exception {
        // We use "unknown-sub" as the default sub if missing in AuthController,
        // so we test what happens when both are essentially missing/default.
        OAuth2User principal = createPrincipal("unknown user", null);
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Collections.emptyList(), "test-client");

        mockMvc.perform(get("/api/v1/auth/user").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("unknown user"));
    }

    private OAuth2User createPrincipal(String name, String sub) {
        Map<String, Object> attributes = new HashMap<>();
        if (name != null) attributes.put("name", name);
        // "sub" is mandatory for DefaultOAuth2User if we use it as nameAttributeKey
        attributes.put("sub", sub != null ? sub : "unknown-sub");
        attributes.put("bank_id", "SYSTEM");

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
    }
}
