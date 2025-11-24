package com.bankengine.auth.repository;

import com.bankengine.auth.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Retrieves all Role entities whose names are within the provided collection.
     * This is the critical method used by the PermissionMappingService to look up
     * roles retrieved from the JWT.
     *
     * @param names A collection of role names (e.g., ["ADMIN", "CASHIER"]).
     * @return A List of matching Role entities, eagerly loading their authorities.
     */
    List<Role> findByNameIn(Collection<String> names);

    /**
     * Retrieves a single Role entity by its exact name.
     */
    Optional<Role> findByName(String name);
}