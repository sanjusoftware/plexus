package com.bankengine.common.util;

import java.util.UUID;

public class CodeGeneratorUtil {

    /**
     * Generates a valid alphanumeric code (with underscores/dashes)
     * from a display name. Guaranteed to pass VersionableEntity validation.
     */
    public static String generateValidCode(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return "CODE_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 1. Standardize to Uppercase
        // 2. Replace spaces with underscores
        // 3. Strip all non-alphanumeric/underscore/dash characters
        String sanitized = entityName.toUpperCase()
                .trim()
                .replace(" ", "_")
                .replaceAll("[^A-Z0-9_-]", "");

        // 4. Ensure it fits within the @Column(length = 100) constraint
        // (Name + underscore + 8-char UUID)
        String suffix = "_" + UUID.randomUUID().toString().substring(0, 8);
        int maxNameLength = 100 - suffix.length();

        if (sanitized.length() > maxNameLength) {
            sanitized = sanitized.substring(0, maxNameLength);
        }

        return sanitized + suffix;
    }

    /**
     * Sanitizes and uppercases a code string.
     */
    public static String sanitizeCode(String code) {
        if (code == null) return null;
        return code.toUpperCase().trim().replace(" ", "_").replaceAll("[^A-Z0-9_-]", "");
    }
}
