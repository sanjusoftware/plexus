package com.bankengine.web.exception;

public class DependencyViolationException extends RuntimeException {
    public DependencyViolationException(String message) {
        super(message);
    }
}