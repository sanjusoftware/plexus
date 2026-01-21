package com.bankengine.common.aspect;

import com.bankengine.auth.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    public static final String FILTER_NAME = "bankTenantFilter";
    public static final String PARAM_NAME = "bankId";

    @Before("execution(* com.bankengine..repository..*(..)) || execution(* org.springframework.data.repository.Repository+.*(..))")
    public void enableTenantFilter() {
        // This call will throw the exception if a hacker/unauth user hits it
        // But it will return null safely if we are in System Mode.
        String bankId = TenantContextHolder.getBankId();

        // System admin (SYSTEM bank) should not be filtered, allowing them to see all banks/tenants
        if (bankId != null && !"SYSTEM".equals(bankId)) {
            entityManager.unwrap(Session.class)
                    .enableFilter("bankTenantFilter")
                    .setParameter("bankId", bankId);
        } else {
            entityManager.unwrap(Session.class).disableFilter("bankTenantFilter");
        }
    }
}