package com.bankengine.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Profile("dev")
@Order(0)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "app.redis", name = "flush-on-startup", havingValue = "true")
public class DevRedisStartupCleaner implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final String sessionNamespace;

    public DevRedisStartupCleaner(
            StringRedisTemplate stringRedisTemplate,
            @org.springframework.beans.factory.annotation.Value("${spring.session.redis.namespace:bank-engine}") String sessionNamespace) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.sessionNamespace = sessionNamespace;
    }

    private static final List<String> CACHE_PREFIX_PATTERNS = List.of(
            "publicCatalog::*",
            "productDetails::*",
            "productPricingLinks::*",
            "pricingMetadata::*"
    );

    @Override
    public void run(String... args) {
        Set<String> keysToDelete = new LinkedHashSet<>();
        collectKeys(keysToDelete, sessionNamespace + ":*");
        CACHE_PREFIX_PATTERNS.forEach(pattern -> collectKeys(keysToDelete, pattern));

        if (keysToDelete.isEmpty()) {
            log.info("Dev Redis startup cleaner found no Plexus-owned Redis keys to delete.");
            return;
        }

        Long deleted = stringRedisTemplate.delete(keysToDelete);
        log.info("Dev Redis startup cleaner deleted {} Redis keys for namespace '{}' and known cache prefixes.",
                deleted,
                sessionNamespace);
    }

    private void collectKeys(Set<String> keysToDelete, String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            keysToDelete.addAll(keys);
        }
    }
}

