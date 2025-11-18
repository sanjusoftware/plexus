package com.bankengine.pricing;

import com.bankengine.pricing.dto.PricingInputMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PricingInputMetadataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingInputMetadataRepository repository;

    @Autowired
    private TierConditionRepository tierConditionRepository;

    @Autowired
    private TestTransactionHelper txHelper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        tierConditionRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        tierConditionRepository.deleteAll();
    }

    private PricingInputMetadataDto createDto(String key, String type, String displayName) {
        PricingInputMetadataDto dto = new PricingInputMetadataDto();
        dto.setAttributeKey(key);
        dto.setDataType(type);
        dto.setDisplayName(displayName);
        return dto;
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:create"})
    void shouldCreateMetadataAndReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/pricing-metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto("testKey", "STRING", "Test Key"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributeKey", is("testKey")));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"})
    void shouldReturnAllMetadata() throws Exception {
        txHelper.createAndSaveMetadata("key1", "STRING");
        txHelper.createAndSaveMetadata("key2", "DECIMAL");
        long count = repository.count();

        mockMvc.perform(get("/api/v1/pricing-metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is((int) count)));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:read"})
    void shouldReturnMetadataById() throws Exception {
        PricingInputMetadata metadata = txHelper.createAndSaveMetadata("key1", "STRING");

        mockMvc.perform(get("/api/v1/pricing-metadata/{id}", metadata.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeKey", is("key1")));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:update"})
    void shouldUpdateMetadata() throws Exception {
        PricingInputMetadata metadata = txHelper.createAndSaveMetadata("oldKey", "STRING");
        PricingInputMetadataDto updateDto = createDto("newKey", "DECIMAL", "New Key");

        mockMvc.perform(put("/api/v1/pricing-metadata/{id}", metadata.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeKey", is("newKey")))
                .andExpect(jsonPath("$.dataType", is("DECIMAL")));
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:delete"})
    void shouldDeleteMetadata() throws Exception {
        PricingInputMetadata metadata = txHelper.createAndSaveMetadata("deletableKey", "STRING");

        mockMvc.perform(delete("/api/v1/pricing-metadata/{id}", metadata.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = {"pricing:metadata:delete"})
    void shouldReturn409WhenDeletingUsedMetadata() throws Exception {
        PricingInputMetadata metadata = txHelper.createAndSaveMetadata("usedKey", "STRING");
        txHelper.createTierCondition("usedKey");

        mockMvc.perform(delete("/api/v1/pricing-metadata/{id}", metadata.getId()))
                .andExpect(status().isConflict());
    }
}
