package com.bankengine.auth.security;

/**
 * Interface for retrieving security-related information from the current context,
 * particularly in a multi-tenant environment.
 */
public interface SecurityContext {

    /**
     * Retrieves the Bank ID from the current authentication principal (JWT claims).
     * @return The Bank ID associated with the current user.
     * @throws IllegalStateException if the Bank ID cannot be found.
     */
    String getCurrentBankId();
}