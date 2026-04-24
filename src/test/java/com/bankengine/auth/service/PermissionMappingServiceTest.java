package com.bankengine.auth.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionMappingServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PermissionMappingService service;

    @Test
    void testGetPermissionsForRoles() {
        // Empty roles
        when(roleRepository.findByNameIn(any())).thenReturn(Collections.emptyList());
        assertTrue(service.getPermissionsForRoles(List.of("ADMIN")).isEmpty());

        // With roles
        Role r1 = new Role(); r1.setAuthorities(Set.of("P1", "P2"));
        Role r2 = new Role(); r2.setAuthorities(Set.of("P2", "P3"));
        when(roleRepository.findByNameIn(any())).thenReturn(List.of(r1, r2));

        Set<String> perms = service.getPermissionsForRoles(List.of("ADMIN", "USER"));
        assertEquals(3, perms.size());
        assertTrue(perms.containsAll(Set.of("P1", "P2", "P3")));
    }
}
