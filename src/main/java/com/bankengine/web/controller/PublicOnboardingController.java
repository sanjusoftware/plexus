package com.bankengine.web.controller;

import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.service.BankConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/onboarding")
@RequiredArgsConstructor
@Tag(name = "Public Onboarding", description = "Public endpoints for new bank registration requests.")
public class PublicOnboardingController {

    private final BankConfigurationService bankConfigurationService;

    @GetMapping("/captcha")
    @Operation(summary = "Get a new math captcha")
    public ResponseEntity<CaptchaResponse> getCaptcha(HttpSession session) {
        Random random = new Random();
        int a = random.nextInt(10);
        int b = random.nextInt(10);
        int result = a + b;

        String captchaId = UUID.randomUUID().toString();
        session.setAttribute("CAPTCHA_" + captchaId, result);

        return ResponseEntity.ok(new CaptchaResponse(captchaId, "What is " + a + " + " + b + "?"));
    }

    @PostMapping
    @Operation(summary = "Submit a new bank onboarding request")
    public ResponseEntity<?> submitOnboarding(@RequestBody OnboardingRequest request, HttpSession session) {
        Integer expected = (Integer) session.getAttribute("CAPTCHA_" + request.getCaptchaId());

        if (expected == null || !expected.toString().equals(request.getCaptchaAnswer())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired captcha.");
        }

        session.removeAttribute("CAPTCHA_" + request.getCaptchaId());
        BankConfigurationResponse response = bankConfigurationService.submitOnboarding(request.getBankDetails());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CaptchaResponse {
        private String id;
        private String question;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OnboardingRequest {
        private BankConfigurationRequest bankDetails;
        private String captchaId;
        private String captchaAnswer;
    }
}
