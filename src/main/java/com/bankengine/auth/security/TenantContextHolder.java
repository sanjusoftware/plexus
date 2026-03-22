package com.bankengine.auth.security;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadLocal holder for the Bank ID of the current request.
 * Enforces security by distinguishing between User and System contexts.
 */

@Slf4j
public class TenantContextHolder {
    private static final ThreadLocal<String> CONTEXT = new InheritableThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM_MODE = InheritableThreadLocal.withInitial(() -> false);

    private static String systemBankId;

    public static void setBankId(String bankId) {
        CONTEXT.set(bankId);
    }

    public static String getBankId() {
        return CONTEXT.get();
    }

    public static void setSystemBankId(String systemBankId) {
        TenantContextHolder.systemBankId = systemBankId;
    }

    public static String getSystemBankId() {
        if (systemBankId == null) {
            throw new IllegalStateException("System Bank ID has not been initialized from environment variables. Please set env variable: app.security.system-bank-id ");
        }
        return systemBankId;
    }

    public static void setSystemMode(boolean isSystem) {
        log.debug("[TENANT-CONTEXT] System Mode set to: {}", isSystem);
        SYSTEM_MODE.set(isSystem);
    }

    public static boolean isSystemMode() {
        Boolean mode = SYSTEM_MODE.get();
        return mode != null && mode;
    }

    public static void clear() {
        CONTEXT.remove();
        SYSTEM_MODE.remove();
    }
}
