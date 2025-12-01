package com.bankengine.auth.security;

/**
 * ThreadLocal holder for the Bank ID of the current request.
 * This avoids passing bankId through every method signature.
 */
public class BankContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setBankId(String bankId) {
        CONTEXT.set(bankId);
    }

    public static String getBankId() {
        String bankId = CONTEXT.get();
        if (bankId == null) {
            // This happens if the filter is missing or runs out of order
            throw new IllegalStateException("Bank ID is not set in the context. Authentication may have failed or filter is missing.");
        }
        return bankId;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}