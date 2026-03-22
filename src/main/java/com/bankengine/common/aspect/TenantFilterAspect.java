package com.bankengine.common.aspect;

import com.bankengine.auth.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    public static final String FILTER_NAME = "bankTenantFilter";
    public static final String PARAM_NAME = "bankId";

    @Before("execution(* com.bankengine..repository..*(..)) || execution(* org.springframework.data.repository.Repository+.*(..))")
    public void enableTenantFilter() {
        log.debug(">>>> [ASPECT-START] Intercepting repository call. SystemMode: {}, Current BankId: {}",
                TenantContextHolder.isSystemMode(), TenantContextHolder.getBankId());

        if (TenantContextHolder.isSystemMode()) {
            log.debug(">>>> [ASPECT-BYPASS] System Mode active. Bypassing tenant filters.");
            disableFilter();
            return;
        }

        // 2. If not system mode, we REQUIRE a bankId
        String bankId = TenantContextHolder.getBankId();

        if (bankId != null && !bankId.trim().isEmpty()) {
            log.debug(">>>> [ASPECT-FILTER] Applying filter for bank: {}", bankId);
            entityManager.unwrap(Session.class)
                    .enableFilter(FILTER_NAME)
                    .setParameter(PARAM_NAME, bankId);
        } else {
            // This is the safety net for user requests
            log.error(">>>> [SECURITY-CRITICAL] Data access attempted without Bank ID or System Mode!");
            throw new IllegalStateException("Access Denied: No tenant context found.");
        }
    }

    private void disableFilter() {
        Session session = entityManager.unwrap(Session.class);
        if (session != null && session.getEnabledFilter(FILTER_NAME) != null) {
            log.debug(">>>> [ASPECT-CLEANUP] Filter was active, now disabling.");
            session.disableFilter(FILTER_NAME);
        }
    }
}