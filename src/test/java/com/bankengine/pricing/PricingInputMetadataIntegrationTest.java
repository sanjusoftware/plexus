package com.bankengine.pricing;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.pricing.dto.CreateMetadataRequestDto;
import com.bankengine.pricing.dto.UpdateMetadataRequestDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingInputMetadataIntegrationTest extends AbstractIntegrationTest {

    private static final String API_PATH = "/api/v1/pricing-metadata";
    private static final int SEEDED_METADATA_COUNT = 2; // customerSegment, transactionAmount

    // Role Constants
    private static final String ROLE_PREFIX = "PIMT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "TEST_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "TEST_READER";
    private static final String CREATOR_ROLE = ROLE_PREFIX + "TEST_CREATOR";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingInputMetadataRepository metadataRepository;

    @Autowired
    private TestTransactionHelper txHelper;

    @Autowired
    private EntityManager entityManager;

    // Mock the rule engine side effect ---
    @MockBean
    private KieContainerReloadService reloadService;

    // --- Static Role Setup ---

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelper) {
        // Define authorities needed for metadata management
        Set<String> adminAuths = Set.of(
            "pricing:metadata:create",
            "pricing:metadata:read",
            "pricing:metadata:update",
            "pricing:metadata:delete"
        );
        Set<String> readerAuths = Set.of("pricing:metadata:read");
        Set<String> creatorAuths = Set.of("pricing:metadata:create", "pricing:metadata:update");

        // Use the transactional helper to commit roles once
        txHelper.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelper.createRoleInDb(READER_ROLE, readerAuths);
        txHelper.createRoleInDb(CREATOR_ROLE, creatorAuths);
        txHelper.flushAndClear();
    }

    // --- Test Setup ---

    @BeforeEach
    void setup() {
        txHelper.setupCommittedMetadata();

        // Mock rule engine service
        Mockito.when(reloadService.reloadKieContainer()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        txHelper.cleanupCommittedMetadata();

        // Clean up data created by the specific test runs (any metadata not part of the seeded keys)
        // Since cleanupCommittedMetadata only deletes customerSegment and transactionAmount,
        // we explicitly delete all remaining test-created metadata.
        metadataRepository.deleteAll();

        txHelper.flushAndClear();
    }

    // --- HELPERS ---

    private PricingInputMetadata createTestMetadata(String key) {
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey(key);
        metadata.setDataType("STRING");
        metadata.setDisplayName(key + " Display");
        return metadataRepository.save(metadata);
    }

    // --- READ OPERATIONS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndMetadataList() throws Exception {
        // ARRANGE: We start with SEEDED_METADATA_COUNT (2) committed items.
        createTestMetadata("Segment");
        createTestMetadata("Region");

        int expectedCount = SEEDED_METADATA_COUNT + 2;

        mockMvc.perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedCount));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndSingleMetadataByKey() throws Exception {
        createTestMetadata("SpecificKey");

        mockMvc.perform(get(API_PATH + "/SpecificKey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeKey").value("SpecificKey"))
                .andExpect(jsonPath("$.dataType").value("STRING"));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenMetadataNotFound() throws Exception {
        mockMvc.perform(get(API_PATH + "/NonExistentKey"))
                .andExpect(status().isNotFound());
    }

    // --- CREATE OPERATIONS ---

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldCreateMetadataAndReturn201() throws Exception {
        CreateMetadataRequestDto requestDto = new CreateMetadataRequestDto(
                "NewAttribute", "New Attribute Display", "DECIMAL");

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributeKey").value("NewAttribute"));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn409OnDuplicateKeyCreation() throws Exception {
        createTestMetadata("ExistingKey");
        CreateMetadataRequestDto requestDto = new CreateMetadataRequestDto(
                "ExistingKey", "Duplicate Display", "INTEGER");

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot create Pricing Input Metadata: An attribute with the key 'ExistingKey' already exists."));
    }

    // --- UPDATE OPERATIONS ---

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldUpdateMetadataAndReturn200() throws Exception {
        createTestMetadata("UpdatableKey");

        UpdateMetadataRequestDto requestDto = new UpdateMetadataRequestDto(
                "Updated Display Name", "BOOLEAN");

        mockMvc.perform(put(API_PATH + "/UpdatableKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Display Name"))
                .andExpect(jsonPath("$.dataType").value("BOOLEAN"));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn404OnUpdateIfNotFound() throws Exception {
        UpdateMetadataRequestDto requestDto = new UpdateMetadataRequestDto("Test", "STRING");

        mockMvc.perform(put(API_PATH + "/NotFoundKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    // --- DELETE OPERATIONS ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteMetadataAndReturn204() throws Exception {
        createTestMetadata("DeletableKey");

        mockMvc.perform(delete(API_PATH + "/DeletableKey"))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get(API_PATH + "/DeletableKey"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingMetadataWithDependencies() throws Exception {
        // ARRANGE: Setup component/tier dependency using the seeded "customerSegment"
        Long componentId = txHelper.createLinkedTierAndValue("ComponentForConflict", "TierForConflict");

        mockMvc.perform(delete(API_PATH + "/customerSegment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete Pricing Input Metadata 'customerSegment': It is used in one or more active tier conditions."));

        // CLEANUP: Delete the complex component graph to prevent test isolation failure
        txHelper.doInTransaction(() -> {
            txHelper.deleteComponentGraphById(componentId);
            entityManager.flush();
        });
    }

    // --- SECURITY TESTS ---

    @Test
    @WithMockUser(authorities = {}) // User with no authorities
    void shouldReturn403ForUnauthorizedRead() throws Exception {
        mockMvc.perform(get(API_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403ForUnauthorizedDelete() throws Exception {
        createTestMetadata("TestSecurity");
        mockMvc.perform(delete(API_PATH + "/TestSecurity"))
                .andExpect(status().isForbidden());
    }
}