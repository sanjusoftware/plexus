package com.bankengine.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@Order(0)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "app.redis", name = "clear-system-caches-on-startup", havingValue = "true")
public class SystemRedisStartupCleaner implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStartupCleanerProperties redisStartupCleanerProperties;

    public SystemRedisStartupCleaner(StringRedisTemplate stringRedisTemplate,
                                     RedisStartupCleanerProperties redisStartupCleanerProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisStartupCleanerProperties = redisStartupCleanerProperties;
    }

    @Override
    public void run(String... args) {
        Set<String> keysToDelete = new LinkedHashSet<>();
        redisStartupCleanerProperties.getSystemCachePatterns()
                .forEach(pattern -> collectKeys(keysToDelete, pattern));

        if (keysToDelete.isEmpty()) {
            log.info("System Redis startup cleaner found no rebuildable system cache keys to delete.");
            return;
        }

        Long deleted = stringRedisTemplate.delete(keysToDelete);
        log.info("System Redis startup cleaner deleted {} Redis keys for system cache patterns {}.",
                deleted,
                redisStartupCleanerProperties.getSystemCachePatterns());
    }

    private void collectKeys(Set<String> keysToDelete, String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            keysToDelete.addAll(keys);
        }
    }
}

