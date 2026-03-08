package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.common.repository.VersionableRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureComponentRepository extends VersionableRepository<FeatureComponent> {
    List<FeatureComponent> findAllByBankIdAndCode(String bankId, String code);
    List<FeatureComponent> findAllByBankIdAndCodeAndVersion(String bankId, String code, Integer version);
}
