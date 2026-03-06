package com.bankengine.common.aspect;

import com.bankengine.auth.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantFilterAspectTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Filter filter;

    @InjectMocks
    private TenantFilterAspect aspect;

    @Test
    void shouldThrowExceptionWhenNoTenantAndNotSystemMode() {
        // Ensure context is empty
        TenantContextHolder.clear();
        TenantContextHolder.setSystemMode(false);

        // This hits the 'else' branch in the aspect that throws IllegalStateException
        // Verifying the security requirement that data access requires a context
        assertThrows(IllegalStateException.class, () -> aspect.enableTenantFilter());
    }

    @Test
    void shouldDisableFilterInSystemMode() {
        // Scenario: Internal system processes (like background jobs) bypassing tenant filters
        TenantContextHolder.setSystemMode(true);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(TenantFilterAspect.FILTER_NAME)).thenReturn(filter);

        aspect.enableTenantFilter();

        // Verifies that the filter is explicitly disabled to allow global access
        verify(session).disableFilter(TenantFilterAspect.FILTER_NAME);
    }

    @Test
    void shouldEnableFilterWhenBankIdIsPresent() {
        // Scenario: Standard user request with a valid Bank ID
        String testBankId = "BANK_001";
        TenantContextHolder.setBankId(testBankId);
        TenantContextHolder.setSystemMode(false);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantFilterAspect.FILTER_NAME)).thenReturn(filter);

        aspect.enableTenantFilter();

        verify(session).enableFilter(TenantFilterAspect.FILTER_NAME);
        verify(filter).setParameter(TenantFilterAspect.PARAM_NAME, testBankId);
    }
}