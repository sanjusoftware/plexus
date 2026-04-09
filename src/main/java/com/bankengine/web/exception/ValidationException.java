package com.bankengine.web.exception;

import com.bankengine.web.dto.Violation;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ValidationException extends RuntimeException {

    private final String code;
    private final List<Violation> violations;

    public ValidationException(String message) {
        this("BUSINESS_RULE_VIOLATION", message, new ArrayList<>());
    }

    public ValidationException(String code, String message) {
        this(code, message, new ArrayList<>());
    }

    public ValidationException(String code, String message, List<Violation> violations) {
        super(message);
        this.code = code;
        this.violations = violations;
    }
}