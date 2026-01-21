package com.bankengine.common.repository;

import com.bankengine.common.model.BankConfiguration;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankConfigurationRepository extends TenantRepository<BankConfiguration, Long> {
}
