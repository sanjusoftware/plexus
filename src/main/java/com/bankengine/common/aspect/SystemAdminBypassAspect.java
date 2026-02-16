package com.bankengine.common.aspect;

import com.bankengine.auth.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@Order(1) // Runs BEFORE TenantFilterAspect
public class SystemAdminBypassAspect {

    @Around("@annotation(com.bankengine.common.annotation.SystemAdminBypass)")
    public Object manageSystemMode(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean alreadyInSystemMode = TenantContextHolder.isSystemMode();
        try {
            if (!alreadyInSystemMode) {
                log.debug(">>>> [BYPASS] Entering System Mode for: {}", joinPoint.getSignature().getName());
                TenantContextHolder.setSystemMode(true);
            }
            return joinPoint.proceed();
        } finally {
            if (!alreadyInSystemMode) {
                TenantContextHolder.setSystemMode(false);
                log.debug(">>>> [BYPASS] Exited System Mode.");
            }
        }
    }
}