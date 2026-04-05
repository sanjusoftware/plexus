package com.bankengine.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevRedisStartupCleanerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private DevRedisStartupCleaner cleaner;

    @Test
    void shouldDeleteKnownDevRedisKeysOnStartup() {
        ReflectionTestUtils.setField(cleaner, "sessionNamespace", "bank-engine");

        when(stringRedisTemplate.keys("bank-engine:*"))
                .thenReturn(Set.of("bank-engine:sessions:abc", "bank-engine:expirations:1"));
        when(stringRedisTemplate.keys("publicCatalog::*"))
                .thenReturn(Set.of("publicCatalog::catalog-home"));
        when(stringRedisTemplate.keys("productDetails::*"))
                .thenReturn(Set.of("productDetails::42"));
        when(stringRedisTemplate.keys("productPricingLinks::*"))
                .thenReturn(Set.of("productPricingLinks::99"));
        when(stringRedisTemplate.keys("pricingMetadata::*"))
                .thenReturn(Set.of("pricingMetadata::customer_segment"));

        cleaner.run();

        verify(stringRedisTemplate).delete(Set.of(
                "bank-engine:sessions:abc",
                "bank-engine:expirations:1",
                "publicCatalog::catalog-home",
                "productDetails::42",
                "productPricingLinks::99",
                "pricingMetadata::customer_segment"
        ));
    }

    @Test
    void shouldSkipDeleteWhenNoMatchingKeysExist() {
        ReflectionTestUtils.setField(cleaner, "sessionNamespace", "bank-engine");

        when(stringRedisTemplate.keys(anyString())).thenReturn(Set.of());

        cleaner.run();

        verify(stringRedisTemplate, never()).delete(anySet());
    }
}

