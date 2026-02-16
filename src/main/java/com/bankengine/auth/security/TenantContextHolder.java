package com.bankengine.auth.security;

/**
 * ThreadLocal holder for the Bank ID of the current request.
 * Enforces security by distinguishing between User and System contexts.
 */
public class TenantContextHolder {
    private static final ThreadLocal<String> CONTEXT = new InheritableThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM_MODE = InheritableThreadLocal.withInitial(() -> false);

    private static String systemBankId;

    public static void setBankId(String bankId) {
        CONTEXT.set(bankId);
    }

    public static String getBankId() {
        String bankId = CONTEXT.get();
        if (bankId == null && !SYSTEM_MODE.get()) {
            throw new IllegalStateException("Bank ID is not set in the context. Access Denied.");
        }
        return bankId;
    }

    public static void setSystemBankId(String systemBankId) {
        TenantContextHolder.systemBankId = systemBankId;
    }

    public static String getSystemBankId() {
        if (systemBankId == null) {
            // This acts as a circuit breaker if the app is misconfigured
            throw new IllegalStateException("System Bank ID has not been initialized from environment variables. Please set env variable: app.security.system-bank-id ");
        }
        return systemBankId;
    }

    public static void setSystemMode(boolean isSystem) {
        SYSTEM_MODE.set(isSystem);
    }

    public static boolean isSystemMode() {
        return SYSTEM_MODE.get();
    }

    public static void clear() {
        CONTEXT.remove();
        SYSTEM_MODE.remove();
    }
}