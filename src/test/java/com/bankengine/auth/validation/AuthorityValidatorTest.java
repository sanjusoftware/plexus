package com.bankengine.auth.validation;

import com.bankengine.auth.service.AuthorityDiscoveryService;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorityValidatorTest {

    @Mock
    private AuthorityDiscoveryService authorityDiscoveryService;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    private AuthorityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AuthorityValidator(authorityDiscoveryService);
    }

    @Test
    void testIsValid_Success() {
        when(authorityDiscoveryService.discoverAllAuthorities()).thenReturn(Set.of("auth1", "auth2"));
        assertTrue(validator.isValid(Set.of("auth1"), context));
    }

    @Test
    void testIsValid_NullInput() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void testIsValid_EmptyInput() {
        assertTrue(validator.isValid(Set.of(), context));
    }

    @Test
    void testIsValid_InvalidAuthority() {
        when(authorityDiscoveryService.discoverAllAuthorities()).thenReturn(Set.of("auth1", "auth2"));
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        assertFalse(validator.isValid(Set.of("auth1", "invalid"), context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(contains("invalid"));
        verify(violationBuilder).addConstraintViolation();
    }
}
