package com.bankengine.auth.controller;

import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.auth.security.TenantContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

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
                log.warn("[AUTH] Login denied: Bank {} is in {} status", bankId, bankConfigOpt.get().getStatus());
                response.sendError(HttpStatus.FORBIDDEN.value(), "Bank account is not active");
                return;
            }
        } finally {
            TenantContextHolder.setSystemMode(false);
        }

        // Redirect to Spring Security's OAuth2 login endpoint for this registrationId
        response.sendRedirect("/oauth2/authorization/" + bankId);
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Return profile information from the principal
        return ResponseEntity.ok(Map.of(
            "name", principal.getAttribute("name"),
            "email", principal.getAttribute("preferred_username") != null ? principal.getAttribute("preferred_username") : (principal.getAttribute("email") != null ? principal.getAttribute("email") : ""),
            "roles", principal.getAttribute("roles"),
            "bank_id", principal.getAttribute("bank_id"),
            "sub", principal.getAttribute("sub")
        ));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> getCsrf() {
        // Just calling this endpoint ensures the CSRF cookie is set by CookieCsrfTokenRepository
        return ResponseEntity.ok().build();
    }
}
