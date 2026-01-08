package com.bankengine.auth.security;

/**
 * ThreadLocal holder for the Bank ID of the current request.
 * Enforces security by distinguishing between User and System contexts.
 */
public class BankContextHolder {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM_MODE = ThreadLocal.withInitial(() -> false);

    public static void setBankId(String bankId) {
        CONTEXT.set(bankId);
    }

    public static String getBankId() {
        String bankId = CONTEXT.get();
        // Strict Enforcement: Block access if context is missing and not in system mode.
        if (bankId == null && !SYSTEM_MODE.get()) {
            throw new IllegalStateException("Bank ID is not set in the context. Access Denied.");
        }
        return bankId;
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