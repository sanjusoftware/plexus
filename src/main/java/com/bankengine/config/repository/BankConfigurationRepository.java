package com.bankengine.config.repository;

import com.bankengine.common.model.BankConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankConfigurationRepository extends JpaRepository<BankConfiguration, String> {
    Optional<BankConfiguration> findByBankId(String bankId);
}