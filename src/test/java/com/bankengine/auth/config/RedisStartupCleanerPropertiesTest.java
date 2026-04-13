package com.bankengine.auth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisStartupCleanerPropertiesTest {

    @Test
    void shouldDefaultToSystemAuthoritiesPattern() {
        RedisStartupCleanerProperties properties = new RedisStartupCleanerProperties();

        assertEquals(List.of("systemAuthorities::*"), properties.getSystemCachePatterns());
    }

    @Test
    void shouldRestoreDefaultPatternWhenConfiguredListIsEmpty() {
        RedisStartupCleanerProperties properties = new RedisStartupCleanerProperties();
        properties.setSystemCachePatterns(List.of());

        assertEquals(List.of("systemAuthorities::*"), properties.getSystemCachePatterns());
    }
}

