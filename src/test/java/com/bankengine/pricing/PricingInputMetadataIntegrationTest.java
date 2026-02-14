package com.bankengine.pricing;

import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingInputMetadataIntegrationTest extends AbstractIntegrationTest {

    private static final String API_PATH = "/api/v1/pricing-metadata";
    private static final int SEEDED_METADATA_COUNT = 2; // customerSegment, transactionAmount

    private static final String ROLE_PREFIX = "PIMT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "TEST_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "TEST_READER";
    private static final String CREATOR_ROLE = ROLE_PREFIX + "TEST_CREATOR";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PricingInputMetadataRepository metadataRepository;
    @Autowired private TestTransactionHelper txHelper;

    @MockBean
    private KieContainerReloadService reloadService;

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of("pricing:metadata:create", "pricing:metadata:read", "pricing:metadata:update", "pricing:metadata:delete"),
            READER_ROLE, Set.of("pricing:metadata:read"),
            CREATOR_ROLE, Set.of("pricing:metadata:create", "pricing:metadata:update")
        ));
    }

    @BeforeEach
    void setup() {
        txHelper.doInTransaction(() -> {
            txHelper.setupCommittedMetadata();
        });

        // Updated for void return type: mock successful reload
        doNothing().when(reloadService).reloadKieContainer();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        txHelper.doInTransaction(() -> {
            txHelper.cleanupCommittedMetadata();
            metadataRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    // --- HELPERS ---

    private PricingInputMetadata createTestMetadata(String key) {
        return txHelper.doInTransaction(() -> {
            PricingInputMetadata metadata = new PricingInputMetadata();
            metadata.setAttributeKey(key);
            metadata.setDataType("STRING");
            metadata.setDisplayName(key + " Display");
            return metadataRepository.save(metadata);
        });
    }

    // --- READ OPERATIONS ---

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndMetadataList() throws Exception {
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
        PricingMetadataDto requestDto = PricingMetadataDto.builder()
                .attributeKey("NewAttribute")
                .displayName("New Attribute Display")
                .dataType("DECIMAL").build();

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
        PricingMetadataDto requestDto = PricingMetadataDto.builder()
                .attributeKey("ExistingKey")
                .displayName("Duplicate Display")
                .dataType("INTEGER").build();

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

        PricingMetadataDto requestDto = PricingMetadataDto.builder()
                .attributeKey("NewAttribute")
                .displayName("Updated Display Name")
                .dataType("BOOLEAN").build();

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
        PricingMetadataDto requestDto = PricingMetadataDto.builder()
                .attributeKey("NewAttribute")
                .displayName("Test")
                .dataType("STRING").build();

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

        mockMvc.perform(get(API_PATH + "/DeletableKey"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingMetadataWithDependencies() throws Exception {
        Long componentId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("ComponentForConflict", "TierForConflict"));

        mockMvc.perform(delete(API_PATH + "/customerSegment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete Pricing Input Metadata 'customerSegment': It is used in one or more active tier conditions."));

        txHelper.doInTransaction(() -> {
            txHelper.deleteComponentGraphById(componentId);
        });
    }

    // --- SECURITY TESTS ---

    @Test
    @WithMockUser(authorities = {})
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