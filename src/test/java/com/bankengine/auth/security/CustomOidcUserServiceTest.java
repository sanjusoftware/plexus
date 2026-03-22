package com.bankengine.auth.security;

import com.bankengine.auth.service.AuthorityMappingService;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
        ClientRegistration registration = mock(ClientRegistration.class);
        when(registration.getClientId()).thenReturn("client123");
        when(request.getClientRegistration()).thenReturn(registration);

        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");
        config.setName("Bank One");

        when(bankRepo.findByIssuerUrlAndClientIdUnfiltered(isNull(), eq("client123"))).thenReturn(Optional.of(config));
        when(mappingService.mapAuthorities(anyMap(), isNull())).thenReturn(Collections.emptyList());

        OidcUser result = service.enrichUser(request, oidcUser);

        assertNotNull(result);
        assertEquals("BANK1", result.getAttribute("bank_id"));
    }

    @Test
    void enrichUser_WithTrailingSlashIssuer_ShouldNormalize() throws Exception {
        AuthorityMappingService mappingService = mock(AuthorityMappingService.class);
        BankConfigurationRepository bankRepo = mock(BankConfigurationRepository.class);

        CustomOidcUserService service = new CustomOidcUserService(mappingService, bankRepo);

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "user123"));
        when(oidcUser.getIssuer()).thenReturn(new java.net.URL("http://issuer/"));
        when(oidcUser.getIdToken()).thenReturn(new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "user123", "iss", "http://issuer/")));

        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        when(registration.getClientId()).thenReturn("client123");
        when(request.getClientRegistration()).thenReturn(registration);

        BankConfiguration config = new BankConfiguration();
        config.setBankId("BANK1");

        when(bankRepo.findByIssuerUrlAndClientIdUnfiltered(eq("http://issuer"), eq("client123"))).thenReturn(Optional.of(config));

        OidcUser result = service.enrichUser(request, oidcUser);
        assertNotNull(result);
        assertEquals("BANK1", result.getAttribute("bank_id"));
    }
}
