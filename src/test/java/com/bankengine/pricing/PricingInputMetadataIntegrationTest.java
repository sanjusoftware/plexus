package com.bankengine.pricing;

import com.bankengine.pricing.dto.CreateMetadataRequestDto;
import com.bankengine.pricing.dto.UpdateMetadataRequestDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingInputMetadataIntegrationTest {

    private static final String API_PATH = "/api/v1/pricing-metadata";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingInputMetadataRepository metadataRepository;

    @Autowired
    private TestTransactionHelper txHelper;

    // Mock the rule engine side effect ---
    @MockBean
    private KieContainerReloadService reloadService;

    // --- Test Setup ---

    @BeforeEach
    void setup() {
        seedRequiredMetadata();
        // Prevent the real DRL compilation and validation from running during tests.
        // This stops the 'Invalid rule attribute' errors.
        Mockito.when(reloadService.reloadKieContainer()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        // Clean up data created by tests.
        metadataRepository.deleteAll();
    }

    // --- HELPERS ---

    private void seedRequiredMetadata() {
        if (metadataRepository.findByAttributeKey("customerSegment").isEmpty()) {
            createSeededMetadata("customerSegment", "Client Segment", "STRING");
        }
        if (metadataRepository.findByAttributeKey("transactionAmount").isEmpty()) {
            createSeededMetadata("transactionAmount", "Transaction Amount", "DECIMAL");
        }
    }

    private PricingInputMetadata createSeededMetadata(String key, String displayName, String dataType) {
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey(key);
        metadata.setDataType(dataType);
        metadata.setDisplayName(displayName);
        return metadataRepository.save(metadata);
    }

    private PricingInputMetadata createTestMetadata(String key) {
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey(key);
        metadata.setDataType("STRING");
        metadata.setDisplayName(key + " Display");
        return metadataRepository.save(metadata);
    }

    // --- READ OPERATIONS ---

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"})
    void shouldReturn200AndMetadataList() throws Exception {
        // ARRANGE: Assuming 2 seeded items + 2 created in test = 4 total
        createTestMetadata("Segment");
        createTestMetadata("Region");

        mockMvc.perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[2].attributeKey").value("Segment"));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"})
    void shouldReturn200AndSingleMetadataByKey() throws Exception {
        createTestMetadata("SpecificKey");

        mockMvc.perform(get(API_PATH + "/SpecificKey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeKey").value("SpecificKey"))
                .andExpect(jsonPath("$.dataType").value("STRING"));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"})
    void shouldReturn404WhenMetadataNotFound() throws Exception {
        mockMvc.perform(get(API_PATH + "/NonExistentKey"))
                .andExpect(status().isNotFound());
    }

    // --- CREATE OPERATIONS ---

    @Test
    @WithMockUser(authorities = {"pricing:metadata:create"})
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
    @WithMockUser(authorities = {"pricing:metadata:create"})
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
    @WithMockUser(authorities = {"pricing:metadata:update"})
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
    @WithMockUser(authorities = {"pricing:metadata:update"})
    void shouldReturn404OnUpdateIfNotFound() throws Exception {
        UpdateMetadataRequestDto requestDto = new UpdateMetadataRequestDto("Test", "STRING");

        mockMvc.perform(put(API_PATH + "/NotFoundKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    // --- DELETE OPERATIONS ---

    @Test
    @WithMockUser(authorities = {"pricing:metadata:delete", "pricing:metadata:read"})
    void shouldDeleteMetadataAndReturn204() throws Exception {
        createTestMetadata("DeletableKey");

        mockMvc.perform(delete(API_PATH + "/DeletableKey"))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get(API_PATH + "/DeletableKey"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:delete"})
    void shouldReturn409WhenDeletingMetadataWithDependencies() throws Exception {
        txHelper.setupCommittedMetadata();
        txHelper.createLinkedTierAndValue("ComponentForConflict", "TierForConflict");

        mockMvc.perform(delete(API_PATH + "/customerSegment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete Pricing Input Metadata 'customerSegment': It is used in one or more active tier conditions."));
    }

    // --- SECURITY TESTS ---

    @Test
    @WithMockUser(authorities = {}) // User with no authorities
    void shouldReturn403ForUnauthorizedRead() throws Exception {
        mockMvc.perform(get(API_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"}) // Only read permission
    void shouldReturn403ForUnauthorizedDelete() throws Exception {
        createTestMetadata("TestSecurity");
        mockMvc.perform(delete(API_PATH + "/TestSecurity"))
                .andExpect(status().isForbidden());
    }
}