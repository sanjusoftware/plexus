package com.bankengine.common.aspect;

import com.bankengine.auth.security.BankContextHolder;
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

    @Before("execution(* com.bankengine..repository..*(..))")
    public void enableTenantFilter() {
        // This call will throw the exception if a hacker/unauth user hits it
        // But it will return null safely if we are in System Mode.
        String bankId = BankContextHolder.getBankId();

        if (bankId != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter("bankTenantFilter")
                    .setParameter("bankId", bankId);
        }
    }
}