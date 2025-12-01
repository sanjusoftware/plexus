package com.bankengine.config;

import com.bankengine.auth.security.BankContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateFilterConfig {

    public static final String BANK_TENANT_FILTER = "bankTenantFilter";
    public static final String BANK_ID_PARAM = "bankId";

    private final EntityManagerFactory entityManagerFactory;

    public HibernateFilterConfig(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    public void enableFilter() {
        org.hibernate.engine.spi.SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);

        // 1. Get the EventListenerRegistry
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        // 2. Get the specific EventListenerGroup and APPEND the custom listener
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(new PostLoadEventListener() {

            // This is the single, standard method for PostLoadEventListener in modern Hibernate.
            @Override
            public void onPostLoad(PostLoadEvent event) {
                // The event provides access to the Hibernate Session that triggered the load.
                // We must cast the EventSource to a Session object.
                org.hibernate.Session hibernateSession = (org.hibernate.Session) event.getSession();

                if (hibernateSession.isOpen()) {
                    try {
                        String bankId = BankContextHolder.getBankId();
                        // Enable the filter on the current Hibernate Session
                        hibernateSession.enableFilter(BANK_TENANT_FILTER)
                                .setParameter(BANK_ID_PARAM, bankId);
                    } catch (IllegalStateException e) {
                        // Bank ID not set (e.g., public endpoint or system job). Filter remains disabled.
                        // For sensitive data, the absence of bankId should be handled by system security configuration.
                    }
                }
            }
        });
    }
}