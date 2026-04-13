package com.bankengine.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.redis")
public class RedisStartupCleanerProperties {

    private List<String> systemCachePatterns = new ArrayList<>(List.of("systemAuthorities::*"));

    public List<String> getSystemCachePatterns() {
        return systemCachePatterns;
    }

    public void setSystemCachePatterns(List<String> systemCachePatterns) {
        this.systemCachePatterns = (systemCachePatterns == null || systemCachePatterns.isEmpty())
                ? new ArrayList<>(List.of("systemAuthorities::*"))
                : new ArrayList<>(systemCachePatterns);
    }
}

