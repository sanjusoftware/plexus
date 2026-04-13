package com.bankengine.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemRedisStartupCleanerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldDeleteKnownSystemRedisKeysOnStartup() {
        RedisStartupCleanerProperties properties = new RedisStartupCleanerProperties();
        SystemRedisStartupCleaner cleaner = new SystemRedisStartupCleaner(stringRedisTemplate, properties);

        when(stringRedisTemplate.keys("systemAuthorities::*"))
                .thenReturn(Set.of("systemAuthorities::masterList", "systemAuthorities::permissionsMap"));

        cleaner.run();

        verify(stringRedisTemplate).delete(Set.of(
                "systemAuthorities::masterList",
                "systemAuthorities::permissionsMap"
        ));
    }

    @Test
    void shouldSkipDeleteWhenNoSystemKeysExist() {
        RedisStartupCleanerProperties properties = new RedisStartupCleanerProperties();
        SystemRedisStartupCleaner cleaner = new SystemRedisStartupCleaner(stringRedisTemplate, properties);

        when(stringRedisTemplate.keys("systemAuthorities::*")).thenReturn(Set.of());

        cleaner.run();

        verify(stringRedisTemplate, never()).delete(anySet());
    }

    @Test
    void shouldUseConfiguredSystemCachePatterns() {
        RedisStartupCleanerProperties properties = new RedisStartupCleanerProperties();
        properties.setSystemCachePatterns(List.of("systemAuthorities::*", "permissionsMaster::*"));
        SystemRedisStartupCleaner cleaner = new SystemRedisStartupCleaner(stringRedisTemplate, properties);

        when(stringRedisTemplate.keys("systemAuthorities::*"))
                .thenReturn(Set.of("systemAuthorities::masterList"));
        when(stringRedisTemplate.keys("permissionsMaster::*"))
                .thenReturn(Set.of("permissionsMaster::controllerMap"));

        cleaner.run();

        verify(stringRedisTemplate).delete(Set.of(
                "systemAuthorities::masterList",
                "permissionsMaster::controllerMap"
        ));
    }
}

