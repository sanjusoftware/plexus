package com.bankengine.auth.repository;

import com.bankengine.auth.model.Role;
import com.bankengine.common.repository.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends TenantRepository<Role, Long> {
    List<Role> findByNameIn(Collection<String> names);
    Optional<Role> findByName(String name);
}