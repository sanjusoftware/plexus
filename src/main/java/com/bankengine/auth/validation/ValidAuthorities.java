package com.bankengine.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = AuthorityValidator.class)
@Target({ FIELD })
@Retention(RUNTIME)
public @interface ValidAuthorities {
    String message() default "One or more authorities provided are invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}