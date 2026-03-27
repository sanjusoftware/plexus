package com.bankengine.auth.controller;

import com.bankengine.auth.dto.UserResponse;
import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.web.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final BankConfigurationRepository bankConfigurationRepository;

    @GetMapping("/login")
    public void login(@RequestParam("bankId") String bankId, HttpServletResponse response) throws IOException {
        log.info("[AUTH] Login requested for bank: {}", bankId);

        try {
            TenantContextHolder.setSystemMode(true);
            var bankConfigOpt = bankConfigurationRepository.findByBankIdUnfiltered(bankId);

            if (bankConfigOpt.isEmpty()) {
                log.warn("[AUTH] Invalid bankId: {}", bankId);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid Bank ID");
                return;
            }

            if (bankConfigOpt.get().getStatus() != BankStatus.ACTIVE) {
                String message = String.format("Bank %s is in %s status", bankId, bankConfigOpt.get().getStatus());
                log.warn("[AUTH] Login denied: {}", message);
                response.sendError(HttpStatus.FORBIDDEN.value(), message);
                return;
            }
        } finally {
            TenantContextHolder.setSystemMode(false);
        }

        response.sendRedirect("/oauth2/authorization/" + bankId);
    }

    @GetMapping("/check-bank")
    public ResponseEntity<ApiError> checkBank(@RequestParam("bankId") String bankId) {
        log.info("[AUTH] Check bank requested for bank: {}", bankId);

        try {
            TenantContextHolder.setSystemMode(true);
            var bankConfigOpt = bankConfigurationRepository.findByBankIdUnfiltered(bankId);

            if (bankConfigOpt.isEmpty()) {
                log.warn("[AUTH] Invalid bankId: {}", bankId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Bad Request")
                        .message("Invalid Bank ID")
                        .build());
            }

            if (bankConfigOpt.get().getStatus() != BankStatus.ACTIVE) {
                String message = String.format("Bank %s is in %s status", bankId, bankConfigOpt.get().getStatus());
                log.warn("[AUTH] Check bank denied: {}", message);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error("Forbidden")
                        .message(message)
                        .build());
            }
        } finally {
            TenantContextHolder.setSystemMode(false);
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/user")
    public ResponseEntity<UserResponse> getUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        // 1. Extract Identity from Principal (Injected by CustomOidcUserService)
        String sub = principal.getAttribute("sub") != null ? principal.getAttribute("sub") : "unknown-sub";
        String name = principal.getAttribute("name") != null ? (String) principal.getAttribute("name") : null;
        String picture = principal.getAttribute("picture");

        // Use 'sub' as 'name' if name is null, blank, or 'unknown user'
        if ((name == null || name.trim().isEmpty() || "unknown user".equalsIgnoreCase(name.trim()))
                && !"unknown-sub".equals(sub)) {
            name = sub;
        }

        if (name == null || name.trim().isEmpty()) {
            name = "unknown user";
        }

        // 2. Extract Email with fallbacks
        String email = principal.getAttribute("preferred_username") != null
                ? principal.getAttribute("preferred_username")
                : principal.getAttribute("email") != null ? principal.getAttribute("email") : "unknown@email";

        // 3. Roles: Still coming from the IDP token claims
        List<String> roles = principal.getAttribute("roles") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : Collections.emptyList();

        // 4. Bank ID: Now guaranteed to be injected by our Service from the DB
        final String bankId = principal.getAttribute("bank_id") != null
                ? principal.getAttribute("bank_id") : "UNKNOWN";

        // 5. Lookup Bank Name for the UI
        String bankName;
        try {
            TenantContextHolder.setSystemMode(true);
            bankName = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                    .map(config -> config.getName() != null ? config.getName() : bankId)
                    .orElse(bankId);
        } finally {
            TenantContextHolder.setSystemMode(false);
        }

        // 6. Permissions: Mapped authorities
        List<String> permissions = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(UserResponse.builder()
                .name(name)
                .email(email)
                .roles(roles)
                .bank_id(bankId)
                .bankName(bankName)
                .sub(sub)
                .picture(picture)
                .permissions(permissions)
                .build());
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> getCsrf(jakarta.servlet.http.HttpServletRequest request) {
        // Explicitly access the CSRF token to ensure the deferred token is generated and sent as a cookie
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            log.debug("[AUTH] CSRF token accessed: {}", csrfToken.getHeaderName());
        }
        return ResponseEntity.ok().build();
    }
}