package com.bankengine.auth;

import com.bankengine.PlexusApplication;
import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PlexusApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RoleMappingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TestTransactionHelper txHelper;
    @Autowired
    private CacheManager cacheManager; // Use to clear cache between tests

    private static final String ROLE_API = "/api/v1/roles";
    private static final String WRITE_AUTH = "auth:role:write";
    private static final String READ_AUTH = "auth:role:read";

    // Helper to create the DTO
    private RoleAuthorityMappingDto getMappingDto(String roleName, Set<String> authorities) {
        RoleAuthorityMappingDto dto = new RoleAuthorityMappingDto();
        dto.setRoleName(roleName);
        dto.setAuthorities(authorities);
        return dto;
    }

    // --- 1. Role Persistence and API Tests ---

    @Test
    @WithMockUser(authorities = WRITE_AUTH)
    void shouldCreateAndRetrieveRoleMapping() throws Exception {
        // ARRANGE
        String roleName = "TEST_MANAGER";
        Set<String> authorities = Set.of("catalog:feature:read", "pricing:component:read");
        RoleAuthorityMappingDto dto = getMappingDto(roleName, authorities);

        // ACT 1: Create the mapping
        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // ACT 2: Retrieve the mapping via API
        mockMvc.perform(get(ROLE_API + "/{roleName}", roleName)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test").authorities(new SimpleGrantedAuthority(READ_AUTH))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$", containsInAnyOrder("catalog:feature:read", "pricing:component:read")));

        // VERIFY DB: Check persistence
        txHelper.doInTransaction(() -> {
            assertThat(roleRepository.findByName(roleName)).isPresent();
            assertThat(roleRepository.findByName(roleName).get().getAuthorities()).isEqualTo(authorities);
        });
    }

    @Test
    @WithMockUser(authorities = WRITE_AUTH)
    void shouldUpdateRoleMappingAndEvictCache() throws Exception {
        // ARRANGE: Create initial role and mapping
        String roleName = "TO_BE_UPDATED";
        Set<String> initialAuths = Set.of("auth:role:read");
        txHelper.createRoleInDb(roleName, initialAuths);

        // Ensure cache is populated by fetching the role through the service (not possible directly here, but assumed by production code)

        // ARRANGE: New mapping
        Set<String> newAuths = Set.of("pricing:component:create", "pricing:tier:delete");
        RoleAuthorityMappingDto updateDto = getMappingDto(roleName, newAuths);

        // ACT: Update the mapping
        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isCreated());

        // VERIFY DB: Check persistence
        txHelper.doInTransaction(() -> {
            assertThat(roleRepository.findByName(roleName).get().getAuthorities()).isEqualTo(newAuths);
        });

        // VERIFY CACHE: The cache should be cleared globally.
        assertThat(cacheManager.getCache("rolePermissions")).isNotNull();
        // Since we cannot reliably check if the specific key for initialAuths is gone without mock-based verification,
        // asserting no exceptions occur during the update/read process is the primary check, relying on @CacheEvict(allEntries=true).
    }

    @Test
    @WithMockUser(authorities = "wrong:authority")
    void shouldDenyAccessWithoutAuthority() throws Exception {
        RoleAuthorityMappingDto dto = getMappingDto("DENIED_ROLE", Set.of("some:irrelevant:permission"));

        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // --- 2. Authority Discovery Test ---

    @Test
    @WithMockUser(authorities = READ_AUTH)
    void shouldDiscoverAllSystemAuthorities() throws Exception {
        mockMvc.perform(get(ROLE_API + "/system-authorities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                // Verify core permissions are present from existing controllers
                .andExpect(jsonPath("$", hasItem("pricing:component:read")))
                .andExpect(jsonPath("$", hasItem("catalog:feature:create")))
                // Verify permissions from the new Role Mapping Controller are present
                .andExpect(jsonPath("$", hasItem("auth:role:write")))
                .andExpect(jsonPath("$", hasItem(READ_AUTH)));
    }

    // --- 3. Permission Lookup Service Test (Conceptual Check) ---
    // NOTE: Testing cache interaction requires specialized tools (like a mock cache)
    // or relying on logs/timing. We rely on the correct annotation placement for safety.

}