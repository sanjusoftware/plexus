package com.bankengine.test.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Helper annotation for System Admin operations.
 * Centralizes the SYSTEM bank, System Admin role, and the required Issuer.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithMockRole(
        roles = {"SYSTEM_ADMIN"},
        bankId = "SYSTEM",
        issuer = "https://login.microsoftonline.com/system-tenant/v2.0"
)
public @interface WithSystemAdminRole {
}