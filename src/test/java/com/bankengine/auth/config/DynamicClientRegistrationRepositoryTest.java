package com.bankengine.auth.config;

import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.repository.BankConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DynamicClientRegistrationRepositoryTest {

    @Mock
    private BankConfigurationRepository bankConfigurationRepository;

    private DynamicClientRegistrationRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new DynamicClientRegistrationRepository(bankConfigurationRepository);
    }

    @Test
    void findByRegistrationId_ShouldReturnRegistration_WhenBankExists() {
        BankConfiguration config = BankConfiguration.builder()
                .bankId("TEST_BANK")
                .clientId("test-client")
                .issuerUrl("http://issuer")
                .build();
        when(bankConfigurationRepository.findByBankIdUnfiltered("TEST_BANK")).thenReturn(Optional.of(config));

        ClientRegistration registration = repository.findByRegistrationId("TEST_BANK");

        assertThat(registration).isNotNull();
        assertThat(registration.getRegistrationId()).isEqualTo("TEST_BANK");
        assertThat(registration.getClientId()).isEqualTo("test-client");
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
    }


    @Test
    void findByRegistrationId_ShouldReturnNull_WhenBankDoesNotExist() {
        when(bankConfigurationRepository.findByBankIdUnfiltered("NON_EXISTENT")).thenReturn(Optional.empty());

        ClientRegistration registration = repository.findByRegistrationId("NON_EXISTENT");

        assertThat(registration).isNull();
    }

    @Test
    void iterator_ShouldReturnAllRegistrations() {
        BankConfiguration config = BankConfiguration.builder()
                .bankId("BANK_1")
                .clientId("client-1")
                .issuerUrl("http://issuer-1")
                .build();
        when(bankConfigurationRepository.findAll()).thenReturn(List.of(config));

        Iterator<ClientRegistration> iterator = repository.iterator();

        assertThat(iterator.hasNext()).isTrue();
        ClientRegistration registration = iterator.next();
        assertThat(registration.getRegistrationId()).isEqualTo("BANK_1");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void iterator_ShouldReturnEmpty_WhenNoBanks() {
        when(bankConfigurationRepository.findAll()).thenReturn(Collections.emptyList());

        Iterator<ClientRegistration> iterator = repository.iterator();

        assertThat(iterator.hasNext()).isFalse();
    }
}
