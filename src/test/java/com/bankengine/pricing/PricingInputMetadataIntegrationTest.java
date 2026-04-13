package com.bankengine.pricing;

import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.model.*;
import com.bankengine.pricing.repository.*;
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

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingInputMetadataIntegrationTest extends AbstractIntegrationTest {

    private static final String API_PATH = "/api/v1/pricing-metadata";
    private static final int SEEDED_METADATA_COUNT = 2; // CUSTOMER_SEGMENT, TRANSACTION_AMOUNT

    private static final String ROLE_PREFIX = "PIMT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "TEST_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "TEST_READER";
    private static final String CREATOR_ROLE = ROLE_PREFIX + "TEST_CREATOR";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PricingInputMetadataRepository metadataRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private PricingTierRepository pricingTierRepository;
    @Autowired private TierConditionRepository tierConditionRepository;
    @Autowired private PriceValueRepository priceValueRepository;
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
            metadata.setSourceType(PricingInputMetadata.AttributeSourceType.CUSTOM_ATTRIBUTE);
            metadata.setSourceField(key);
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
                .andExpect(jsonPath("$.length()").value(expectedCount))
                .andExpect(jsonPath("$[?(@.attributeKey == 'CUSTOMER_SEGMENT')].system").value(true))
                .andExpect(jsonPath("$[?(@.attributeKey == 'Segment')].system").value(false));
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
        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("NewAttribute")
                .displayName("New Attribute Display")
                .dataType("decimal").build();

        mockMvc.perform(postWithCsrf(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributeKey").value("NewAttribute"))
                .andExpect(jsonPath("$.dataType").value("DECIMAL"));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn409OnDuplicateKeyCreation() throws Exception {
        createTestMetadata("ExistingKey");
        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("ExistingKey")
                .displayName("Duplicate Display")
                .dataType("INTEGER").build();

        mockMvc.perform(postWithCsrf(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot create Pricing Input Metadata: An attribute with the key 'ExistingKey' already exists."));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenCreatingMetadataWithInvalidDataType() throws Exception {
        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("InvalidTypeAttribute")
                .displayName("Invalid Type Attribute")
                .dataType("FLOAT")
                .build();

        mockMvc.perform(postWithCsrf(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Input validation failed: One or more fields contain invalid data."))
                .andExpect(jsonPath("$.errors[*].reason", hasItem("Data type must be one of: STRING, DECIMAL, INTEGER, LONG, BOOLEAN, DATE.")));
    }

    // --- UPDATE OPERATIONS ---

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn400WhenUpdatingMetadataWithInvalidDataType() throws Exception {
        createTestMetadata("UpdateInvalidType");

        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("UpdateInvalidType")
                .displayName("Invalid Type Update")
                .dataType("FLOAT")
                .build();

        mockMvc.perform(putWithCsrf(API_PATH + "/UpdateInvalidType")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Input validation failed: One or more fields contain invalid data."))
                .andExpect(jsonPath("$.errors[*].reason", hasItem("Data type must be one of: STRING, DECIMAL, INTEGER, LONG, BOOLEAN, DATE.")));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldUpdateMetadataAndReturn200() throws Exception {
        createTestMetadata("UpdatableKey");

        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("NewAttribute")
                .displayName("Updated Display Name")
                .dataType("BOOLEAN").build();

        mockMvc.perform(putWithCsrf(API_PATH + "/UpdatableKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Display Name"))
                .andExpect(jsonPath("$.dataType").value("BOOLEAN"));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn422WhenUpdatingSystemMetadata() throws Exception {
        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("CUSTOMER_SEGMENT")
                .displayName("Illegal Update")
                .dataType("STRING").build();

        mockMvc.perform(putWithCsrf(API_PATH + "/CUSTOMER_SEGMENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot update protected system pricing attribute: CUSTOMER_SEGMENT"));
    }

    @Test
    @WithMockRole(roles = {CREATOR_ROLE})
    void shouldReturn404OnUpdateIfNotFound() throws Exception {
        PricingMetadataRequest requestDto = PricingMetadataRequest.builder()
                .attributeKey("NewAttribute")
                .displayName("Test")
                .dataType("STRING").build();

        mockMvc.perform(putWithCsrf(API_PATH + "/NotFoundKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    // --- DELETE OPERATIONS ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteMetadataAndReturn204() throws Exception {
        createTestMetadata("DeletableKey");

        mockMvc.perform(deleteWithCsrf(API_PATH + "/DeletableKey"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API_PATH + "/DeletableKey"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingMetadataWithDependencies() throws Exception {
        String dependentKey = "DEPENDENCY_KEY";
        Long componentId = txHelper.doInTransaction(() -> {
            metadataRepository.save(PricingInputMetadata.builder()
                    .attributeKey(dependentKey)
                    .dataType("STRING")
                    .displayName(dependentKey + " Display")
                    .sourceType(PricingInputMetadata.AttributeSourceType.CUSTOM_ATTRIBUTE)
                    .sourceField(dependentKey)
                    .bankId(TEST_BANK_ID)
                    .build());

            PricingComponent component = txHelper.createPricingComponentInDb("ComponentForConflict");
            PricingTier tier = PricingTier.builder()
                    .pricingComponent(component)
                    .name("TierForConflict")
                    .code("TIER_FOR_CONFLICT")
                    .minThreshold(java.math.BigDecimal.ZERO)
                    .bankId(TEST_BANK_ID)
                    .build();
            PricingTier savedTier = pricingTierRepository.save(tier);

            tierConditionRepository.save(TierCondition.builder()
                    .pricingTier(savedTier)
                    .attributeName(dependentKey)
                    .operator(TierCondition.Operator.EQ)
                    .attributeValue("Y")
                    .bankId(TEST_BANK_ID)
                    .build());

            priceValueRepository.save(PriceValue.builder()
                    .pricingTier(savedTier)
                    .rawValue(java.math.BigDecimal.ONE)
                    .valueType(PriceValue.ValueType.FEE_ABSOLUTE)
                    .bankId(TEST_BANK_ID)
                    .build());

            return component.getId();
        });

        mockMvc.perform(deleteWithCsrf(API_PATH + "/" + dependentKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete Pricing Input Metadata '" + dependentKey + "': It is used in one or more active tier conditions."));

        txHelper.doInTransaction(() -> {
            txHelper.deleteComponentGraphById(componentId);
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn422WhenDeletingSystemMetadata() throws Exception {
        mockMvc.perform(deleteWithCsrf(API_PATH + "/CUSTOMER_SEGMENT"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot delete protected system pricing attribute: CUSTOMER_SEGMENT"));
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
        mockMvc.perform(deleteWithCsrf(API_PATH + "/TestSecurity"))
                .andExpect(status().isForbidden());
    }
}
