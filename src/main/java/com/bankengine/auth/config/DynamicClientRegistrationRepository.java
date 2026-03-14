package com.bankengine.auth.config;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.Iterator;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DynamicClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final BankConfigurationRepository bankConfigurationRepository;

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        log.debug("[AUTH] Resolving ClientRegistration for bankId: {}", registrationId);

        try {
            TenantContextHolder.setSystemMode(true);
            return bankConfigurationRepository.findByBankIdUnfiltered(registrationId)
                    .map(this::toClientRegistration)
                    .orElse(null);
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
    }

    private ClientRegistration toClientRegistration(BankConfiguration config) {
        String registrationId = config.getBankId();

        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .clientId(config.getClientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "offline_access", "api://bank-engine-api/access_as_user")
                .authorizationUri(config.getIssuerUrl() + "/oauth2/v2.0/authorize")
                .tokenUri(config.getIssuerUrl() + "/oauth2/v2.0/token")
                .jwkSetUri(config.getIssuerUrl() + "/discovery/v2.0/keys")
                .issuerUri(config.getIssuerUrl())
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Bank Engine - " + registrationId);

        return builder.build();
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        // This is mainly for debugging or UI lists
        try {
            TenantContextHolder.setSystemMode(true);
            return bankConfigurationRepository.findAll().stream()
                    .map(this::toClientRegistration)
                    .iterator();
        } finally {
            TenantContextHolder.setSystemMode(false);
        }
    }
}
