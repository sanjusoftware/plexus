package com.bankengine.common.persistence;

import com.bankengine.auth.security.BankContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provides the Bank ID for Spring Data JPA Auditing (@CreatedBy).
 * Retrieves the ID from the BankContextHolder, which is populated
 * by the BankContextFilter during a live request, or manually in tests.
 */
@Component
public class BankIdAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        try {
            // Retrieve the bankId from your thread-local context
            return Optional.of(BankContextHolder.getBankId());
        } catch (IllegalStateException e) {
            // Context is not set (e.g., during unauthenticated operations or test setup).
            // This will cause a constraint violation if the column is NOT NULL, which is the desired integrity check.
            return Optional.empty();
        }
    }
}