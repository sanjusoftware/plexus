package com.bankengine.common.repository;

import com.bankengine.common.model.BankConfiguration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankConfigurationRepository extends TenantRepository<BankConfiguration, Long> {
    // Native queries bypass Hibernate @Filter entirely
    @Query(value = "SELECT * FROM bank_configuration WHERE bank_id = :bankId", nativeQuery = true)
    Optional<BankConfiguration> findByBankIdUnfiltered(@Param("bankId") String bankId);

    @Query(value = "SELECT * FROM bank_configuration WHERE issuer_url = :issuerUrl AND client_id = :clientId", nativeQuery = true)
    Optional<BankConfiguration> findByIssuerUrlAndClientIdUnfiltered(@Param("issuerUrl") String issuerUrl, @Param("clientId") String clientId);

    // FOR BUSINESS LOGIC: Let the filter do the work
    // Since only ONE config exists per bank, and the filter limits to ONE bank...
    default Optional<BankConfiguration> findCurrent() {
        return findAll().stream().findFirst();
    }
    boolean existsByIssuerUrl(String issuerUrl);
}
