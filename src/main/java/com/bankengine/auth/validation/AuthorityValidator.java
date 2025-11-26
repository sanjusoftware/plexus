package com.bankengine.auth.validation;

import com.bankengine.auth.service.AuthorityDiscoveryService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthorityValidator implements ConstraintValidator<ValidAuthorities, Set<String>> {

    // Inject the service that knows all valid permissions.
    private final AuthorityDiscoveryService authorityDiscoveryService;

    @Override
    public boolean isValid(Set<String> inputAuthorities, ConstraintValidatorContext context) {
        if (inputAuthorities == null || inputAuthorities.isEmpty()) {
            return true; // @NotEmpty handles the empty check
        }

        // Get the master list of all valid authorities (from the system-authorities endpoint logic)
        Set<String> systemAuthorities = authorityDiscoveryService.discoverAllAuthorities();

        // Check if all input authorities are contained within the system authorities
        boolean allValid = systemAuthorities.containsAll(inputAuthorities);

        if (!allValid) {
            // Customize the validation message to show which authorities are invalid
            context.disableDefaultConstraintViolation();

            // Find the invalid ones to include in the message
            Set<String> invalidAuthorities = inputAuthorities.stream()
                    .filter(auth -> !systemAuthorities.contains(auth))
                    .collect(Collectors.toSet());

            context.buildConstraintViolationWithTemplate(
                    "The following authorities are invalid: " + invalidAuthorities
            ).addConstraintViolation();
        }

        return allValid;
    }
}