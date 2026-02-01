package com.bankengine.auth;

import com.bankengine.auth.dto.RoleAuthorityMappingDto;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.pricing.TestTransactionHelper;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockRole(roles = {"ADMIN"}, bankId = AbstractIntegrationTest.TEST_BANK_ID)
public class RoleMappingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TestTransactionHelper txHelper;
    @Autowired private CacheManager cacheManager;

    private static final String ROLE_API = "/api/v1/roles";
    private static final String WRITE_AUTH = "auth:role:write";
    private static final String READ_AUTH = "auth:role:read";

    @BeforeAll
    static void init(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            "ADMIN", Set.of(WRITE_AUTH, READ_AUTH),
            "GUEST", Set.of("some:useless:permission")
        ));
    }

    private RoleAuthorityMappingDto getMappingDto(String roleName, Set<String> authorities) {
        RoleAuthorityMappingDto dto = new RoleAuthorityMappingDto();
        dto.setRoleName(roleName);
        dto.setAuthorities(authorities);
        return dto;
    }

    @Test
    void shouldCreateAndRetrieveRoleMapping() throws Exception {
        String roleName = "TEST_MANAGER";
        Set<String> authorities = Set.of("catalog:feature:read", "pricing:component:read");
        RoleAuthorityMappingDto dto = getMappingDto(roleName, authorities);

        // POST: Create
        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // GET: Retrieve
        mockMvc.perform(get(ROLE_API + "/{roleName}", roleName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$", containsInAnyOrder("catalog:feature:read", "pricing:component:read")));

        // VERIFY DB
        txHelper.doInTransaction(() -> {
            var role = roleRepository.findByName(roleName).orElseThrow();
            assertThat(role.getAuthorities()).isEqualTo(authorities);
            assertThat(role.getBankId()).isEqualTo(TEST_BANK_ID);
        });
    }

    @Test
    @WithMockRole(roles = "ADMIN")
    void shouldUpdateRoleMappingAndEvictCache() throws Exception {
        String roleName = "TO_BE_UPDATED";
        Set<String> initialAuths = Set.of("auth:role:read");

        txHelper.doInTransaction(() -> {
            txHelper.getOrCreateRoleInDb(roleName, initialAuths);
        });

        Set<String> newAuths = Set.of("pricing:component:create", "pricing:tier:delete");
        RoleAuthorityMappingDto updateDto = getMappingDto(roleName, newAuths);

        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isCreated());

        txHelper.doInTransaction(() -> {
            assertThat(roleRepository.findByName(roleName).get().getAuthorities()).isEqualTo(newAuths);
        });

        assertThat(cacheManager.getCache("rolePermissions")).isNotNull();
    }

    @Test
    @WithMockRole(roles = {"GUEST"})
    void shouldDenyAccessWithoutAuthority() throws Exception {
        RoleAuthorityMappingDto dto = getMappingDto("DENIED_ROLE", Set.of("catalog:feature:read"));

        mockMvc.perform(post(ROLE_API + "/mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {"ADMIN"})
    void shouldDiscoverAllSystemAuthorities() throws Exception {
        mockMvc.perform(get(ROLE_API + "/system-authorities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$", hasItem("pricing:component:read")))
                .andExpect(jsonPath("$", hasItem(READ_AUTH)));
    }
}