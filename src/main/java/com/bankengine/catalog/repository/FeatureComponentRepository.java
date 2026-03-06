package com.bankengine.catalog.repository;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.common.repository.VersionableRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureComponentRepository extends VersionableRepository<FeatureComponent> {
}