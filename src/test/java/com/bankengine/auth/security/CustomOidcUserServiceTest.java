package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomOidcUserServiceTest {

    @Test
    void enrichUser_WithNullIssuer_ShouldWork() {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        BankConfigurationRepository bankRepo = mock(BankConfigurationRepository.class);

        CustomOidcUserService service = new CustomOidcUserService(mappingService, bankRepo);

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "user123"));
        when(oidcUser.getIssuer()).thenReturn(null);
        when(oidcUser.getIdToken()).thenReturn(new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "user123")));

        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class, RETURNS_DEEP_STUBS);
        when(registration.getClientId()).thenReturn("client123");
        when(request.getClientRegistration()).thenReturn(registration);

        BankConfiguration config = BankConfiguration.builder().bankId("BANK1").name("Bank One").build();

        when(mappingService.mapAuthoritiesWithContext(anyMap(), isNull()))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        OidcUser result = service.enrichUser(request, oidcUser);

        assertNotNull(result);
        assertEquals("BANK1", result.getAttribute("bank_id"));
    }

    @Test
    void enrichUser_WithTrailingSlashIssuer_ShouldWorkViaMappingService() throws Exception {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        BankConfigurationRepository bankRepo = mock(BankConfigurationRepository.class);

        CustomOidcUserService service = new CustomOidcUserService(mappingService, bankRepo);

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "user123"));
        when(oidcUser.getIssuer()).thenReturn(java.net.URI.create("http://issuer/").toURL());
        when(oidcUser.getIdToken()).thenReturn(new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "user123", "iss", "http://issuer/")));

        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class, RETURNS_DEEP_STUBS);
        when(registration.getClientId()).thenReturn("client123");
        when(request.getClientRegistration()).thenReturn(registration);

        BankConfiguration config = BankConfiguration.builder().bankId("BANK1").build();

        when(mappingService.mapAuthoritiesWithContext(anyMap(), eq("http://issuer/")))
                .thenReturn(new AuthorityMappingService.MappingResult(Collections.emptyList(), config));

        OidcUser result = service.enrichUser(request, oidcUser);

        assertNotNull(result);
        assertEquals("BANK1", result.getAttribute("bank_id"));
    }
}