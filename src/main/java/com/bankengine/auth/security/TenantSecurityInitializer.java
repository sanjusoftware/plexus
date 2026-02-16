package com.bankengine.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bridge component that initializes the static TenantContextHolder
 * with values from the Spring Environment/Properties.
 */
@Component
@Slf4j
public class TenantSecurityInitializer {

    @Value("${app.security.system-bank-id}")
    private String systemBankId;

    @PostConstruct
    public void init() {
        log.info("Initializing TenantContextHolder with System Bank ID: {}", systemBankId);

        if (systemBankId == null || systemBankId.isBlank()) {
            throw new IllegalStateException("Critical Security Configuration Missing: app.security.system-bank-id must be defined.");
        }

        TenantContextHolder.setSystemBankId(systemBankId);
    }
}