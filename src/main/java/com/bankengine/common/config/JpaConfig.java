package com.bankengine.common.config;

import com.bankengine.common.repository.TenantRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "userAuditorAware")
@EnableJpaRepositories(
        basePackages = "com.bankengine",
        repositoryBaseClass = TenantRepositoryImpl.class
)
public class JpaConfig {

}