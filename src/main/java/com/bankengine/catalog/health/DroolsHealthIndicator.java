package com.bankengine.catalog.health;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DroolsHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            KieServices ks = KieServices.Factory.get();
            KieContainer kieContainer = ks.getKieClasspathContainer();

            if (kieContainer != null) {
                return Health.up()
                        .withDetail("engine", "Drools KIE")
                        .withDetail("version", ks.getClass().getPackage().getImplementationVersion())
                        .build();
            }
            return Health.down().withDetail("reason", "KieContainer not initialized").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}