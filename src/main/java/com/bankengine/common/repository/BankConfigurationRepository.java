package com.bankengine.common.repository;

import com.bankengine.common.model.BankConfiguration;
import org.springframework.stereotype.Repository;

@Repository
public interface BankConfigurationRepository extends TenantRepository<BankConfiguration, Long> {
    boolean existsByIssuerUrl(String issuerUrl);
}
