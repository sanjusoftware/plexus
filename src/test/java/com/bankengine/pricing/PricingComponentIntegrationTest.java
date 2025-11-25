package com.bankengine.pricing;

import com.bankengine.auth.config.test.WithMockRole;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PricingComponentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingComponentRepository componentRepository;

    @Autowired
    private PricingTierRepository tierRepository;

    @Autowired
    private PriceValueRepository valueRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestTransactionHelper txHelper;

    @Autowired
    private TierConditionRepository tierConditionRepository;

    public static final String ROLE_PREFIX = "PCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "TEST_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "TEST_READER";

    // =================================================================
    // SETUP AND TEARDOWN (Committed Data Lifecycle)
    // =================================================================

    /**
     * Set up all required roles and permissions once before all tests run.
     */
    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelper) {
        // ADMIN has all permissions needed for CRUD on components and tiers
        Set<String> adminAuths = Set.of(
            "pricing:component:create", 
            "pricing:component:read", 
            "pricing:component:update", 
            "pricing:component:delete",
            "pricing:tier:create",
            "pricing:tier:update",
            "pricing:tier:delete"
        );
        // READER has only read permission
        Set<String> readerAuths = Set.of("pricing:component:read");
        
        // Commit the roles in separate transactions
        txHelper.createRoleInDb(ADMIN_ROLE, adminAuths);
        txHelper.createRoleInDb(READER_ROLE, readerAuths);
    }

    @BeforeEach
    void setupMetadata() {
        // Ensure metadata is committed in a separate transaction.
        txHelper.setupCommittedMetadata();
        entityManager.clear();
    }

    /**
     * Cleans up ALL committed PricingComponent, PricingTier, and PriceValue entities
     * created by the helper methods or API calls within the test methods.
     */
    @AfterEach
    void cleanUpCommittedData() {
        txHelper.doInTransaction(() -> {
            // 1. Delete TierCondition (Child of PricingTier)
            tierConditionRepository.deleteAllInBatch();
            // 2. Delete PriceValue (Child of PricingTier)
            valueRepository.deleteAllInBatch();
            // 3. Delete PricingTier (Child of PricingComponent)
            tierRepository.deleteAllInBatch();
            // 4. Delete PricingComponent (Parent)
            componentRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    /**
     * Cleans up the shared, committed metadata (e.g., 'customerSegment') after all tests.
     */
    @AfterAll
    static void tearDownMetadata(@Autowired TestTransactionHelper txHelperStatic) {
        txHelperStatic.cleanupCommittedMetadata();
        txHelperStatic.flushAndClear();
    }


    // =================================================================
    // HELPER METHODS
    // =================================================================

    // Helper method to retrieve Tier/Value IDs after component is created and committed ---
    private PricingTier getTierFromComponentId(Long componentId) {
        return txHelper.doInTransaction(() -> {
            Optional<PricingComponent> componentOpt = componentRepository.findById(componentId);

            if (componentOpt.isEmpty() || componentOpt.get().getPricingTiers().isEmpty()) {
                throw new IllegalStateException("Setup failed: Component or Tier not found in DB after creation.");
            }
            // Use stream().findFirst() because we expect only one tier in the context of these helpers
            return componentOpt.get().getPricingTiers().stream().findFirst().get();
        });
    }
    // ------------------------------------------------------------------------------------------------

    private CreatePricingComponentRequestDto getCreateDto(String name) {
        CreatePricingComponentRequestDto dto = new CreatePricingComponentRequestDto();
        dto.setName(name);
        dto.setType("FEE");
        return dto;
    }

    // --- Helper Method to create a valid TierValueDto ---
    private TierValueDto getValidTierValueDto() {
        CreatePricingTierRequestDto tierDto = new CreatePricingTierRequestDto();
        tierDto.setTierName("Default Tier");
        // This condition uses "customerSegment", which is now guaranteed to exist via txHelper.
        tierDto.setConditions(Set.of(getDummyConditionDto()));

        // Ensure all mandatory fields are set to pass validation
        tierDto.setMinThreshold(new BigDecimal("0.00"));

        CreatePriceValueRequestDto valueDto = new CreatePriceValueRequestDto();
        valueDto.setPriceAmount(new BigDecimal("5.00"));
        valueDto.setCurrency("USD");
        valueDto.setValueType("ABSOLUTE"); // Must be a valid PriceValue.ValueType

        TierValueDto requestDto = new TierValueDto();
        requestDto.setTier(tierDto);
        requestDto.setValue(valueDto);
        return requestDto;
    }

    private TierConditionDto getDummyConditionDto() {
        TierConditionDto dto = new TierConditionDto();
        dto.setAttributeName("customerSegment");
        dto.setOperator(TierCondition.Operator.EQ);
        dto.setAttributeValue("DEFAULT_SEGMENT");
        return dto;
    }

    // =================================================================
    // 1. CREATE (POST) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldCreateComponentAndReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/pricing-components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("MonthlyServiceFee"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("MonthlyServiceFee")))
                .andExpect(jsonPath("$.type", is("FEE")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400OnCreateWithInvalidType() throws Exception {
        CreatePricingComponentRequestDto dto = getCreateDto("BadType");
        dto.setType("INVALID_TYPE");

        mockMvc.perform(post("/api/v1/pricing-components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid component type provided: INVALID_TYPE")));
    }
    
    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn403WhenCreatingComponentWithReaderRole() throws Exception {
        mockMvc.perform(post("/api/v1/pricing-components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getCreateDto("DeniedFee"))))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 2. RETRIEVE (GET) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndListComponent() throws Exception {
        // ARRANGE: Ensure we start with a clean slate due to @AfterEach
        txHelper.createPricingComponentInDb("ComponentA");
        txHelper.createPricingComponentInDb("ComponentB");

        // Count should be exactly 2 for this test
        long expectedCount = 2;

        mockMvc.perform(get("/api/v1/pricing-components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // Asserting against a fixed count (2) is safer than asserting against initialCount + 2
                // when relying on committed data cleanup.
                .andExpect(jsonPath("$.length()", is((int)expectedCount)));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndComponentById() throws Exception {
        PricingComponent savedComponent = txHelper.createPricingComponentInDb("MortgageRate");

        mockMvc.perform(get("/api/v1/pricing-components/{id}", savedComponent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MortgageRate")))
                .andExpect(jsonPath("$.type", is("RATE")));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentComponent() throws Exception {
        mockMvc.perform(get("/api/v1/pricing-components/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // =================================================================
    // 3. UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateComponentAndReturn200() throws Exception {
        PricingComponent savedComponent = txHelper.createPricingComponentInDb("OldRate");
        UpdatePricingComponentRequestDto updateDto = new UpdatePricingComponentRequestDto();
        updateDto.setName("NewRate");
        updateDto.setType("FEE"); // Change type

        mockMvc.perform(put("/api/v1/pricing-components/{id}", savedComponent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewRate")))
                .andExpect(jsonPath("$.type", is("FEE")))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    // =================================================================
    // 4. DELETE (DELETE) TESTS - DEPENDENCY CHECKS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteComponentAndReturn204() throws Exception {
        // NOTE: Component must exist and trigger DRL rebuild on delete, hence we use a component with a linked tier/condition.
        Long idToDelete = txHelper.createPricingComponentInDb("DeletableComponent").getId();

        mockMvc.perform(delete("/api/v1/pricing-components/{id}", idToDelete))
                .andExpect(status().isNoContent());

        // Verify deletion (requires read permission, which ADMIN has)
        mockMvc.perform(get("/api/v1/pricing-components/{id}", idToDelete))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingComponentWithTiers() throws Exception {
        // ARRANGE: 1. Create and COMMIT the component and COMMIT the dependency
        PricingComponent component = txHelper.createPricingComponentInDb("ComponentWithTiers");
        // We use createCommittedTierDependency which now includes a TierCondition
        txHelper.createCommittedTierDependency(component.getId(), "Tier 1");

        // ACT: Attempt to delete the component.
        mockMvc.perform(delete("/api/v1/pricing-components/{id}", component.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Cannot delete Pricing Component ID")));
    }

    // =================================================================
    // 5. BUSINESS LOGIC (POST /tiers) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldAddTierAndValueToComponentAndReturn201() throws Exception {
        PricingComponent component = txHelper.createPricingComponentInDb("TieredComponent");

        TierValueDto requestDto = getValidTierValueDto();

        mockMvc.perform(post("/api/v1/pricing-components/{componentId}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceAmount", is(5.00)))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404WhenAddingTierToNonExistentComponent() throws Exception {
        TierValueDto requestDto = getValidTierValueDto();

        mockMvc.perform(post("/api/v1/pricing-components/99999/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // =================================================================
    // 6. TIER/VALUE UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateTierAndValueAndReturn200() throws Exception {
        // ARRANGE: Setup the records to be updated
        Long componentId = txHelper.createLinkedTierAndValue("ComponentToUpdate", "InitialTier");

        // Fetch the created Tier ID from the committed Component
        PricingTier initialTier = getTierFromComponentId(componentId);
        Long tierId = initialTier.getId();

        // ARRANGE: Create the update DTOs
        UpdatePricingTierRequestDto tierDto = new UpdatePricingTierRequestDto();
        tierDto.setTierName("UpdatedTierName");
        tierDto.setMinThreshold(new BigDecimal("100.00"));
        tierDto.setMaxThreshold(new BigDecimal("999.99"));
        tierDto.setConditions(List.of(getDummyConditionDto()));

        UpdatePriceValueRequestDto valueDto = new UpdatePriceValueRequestDto();
        valueDto.setPriceAmount(new BigDecimal("15.50"));
        valueDto.setCurrency("EUR");
        valueDto.setValueType("PERCENTAGE"); // Use another valid type

        UpdateTierValueDto requestDto = new UpdateTierValueDto();
        requestDto.setTier(tierDto);
        requestDto.setValue(valueDto);

        // ACT: Call PUT /{componentId}/tiers/{tierId}
        mockMvc.perform(put("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        componentId, tierId) // <--- USED LOCAL VARIABLES
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                // ASSERT: Check the updated PriceValue fields
                .andExpect(jsonPath("$.priceAmount", is(15.50)))
                .andExpect(jsonPath("$.currency", is("EUR")));

        txHelper.flushAndClear();

        // VERIFY: Manually check the Tier entity in the DB
        PricingTier updatedTier = txHelper.doInTransaction(() -> tierRepository.findById(tierId)).get();
        assertThat(updatedTier.getTierName()).isEqualTo("UpdatedTierName");
        assertThat(updatedTier.getMaxThreshold()).isEqualTo(new BigDecimal("999.99"));
    }

    // =================================================================
    // 7. TIER/VALUE DELETE (DELETE) TESTS
    // =================================================================

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteTierAndValueAndReturn204() throws Exception {
        // ARRANGE: Setup the records to be deleted
        Long componentId = txHelper.createLinkedTierAndValue("ComponentToDeleteFrom", "TierToDelete");

        // Fetch the IDs from the committed component
        PricingTier initialTier = getTierFromComponentId(componentId);
        Long finalTierId = initialTier.getId();
        // Get the PriceValue ID before deleting the Tier
        Long finalValueId = initialTier.getPriceValues().stream().findFirst().get().getId();

        // ACT: Call DELETE /{componentId}/tiers/{tierId}
        mockMvc.perform(delete("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        componentId, finalTierId))
                .andExpect(status().isNoContent());

        entityManager.clear();

        // VERIFY: Check for deletion in a separate transaction
        txHelper.doInTransaction(() -> {
            // VERIFY 1: Tier entity is gone
            assertThat(tierRepository.findById(finalTierId)).isEmpty();

            // VERIFY 2: PriceValue entity is gone
            assertThat(valueRepository.findById(finalValueId)).isEmpty();
        });
    }

    // =================================================================
    // 8. TIER/VALUE NOT FOUND TESTS
    // =================================================================

    @Test
    // ADMIN role covers both pricing:tier:update and pricing:tier:delete
    @WithMockRole(roles = {ADMIN_ROLE}) 
    void shouldReturn404ForNonExistentTierOrComponent() throws Exception {
        // ARRANGE: Setup a valid component ID
        Long componentId = txHelper.createLinkedTierAndValue("ComponentFor404Test", "ValidTier");
        PricingTier existingTier = getTierFromComponentId(componentId);
        Long existingComponentId = componentId;
        Long existingTierId = existingTier.getId();
        Long nonExistentId = 99999L;

        UpdateTierValueDto updateDto = new UpdateTierValueDto();
        // Populate DTO with valid data to pass initial validation
        updateDto.setTier(new UpdatePricingTierRequestDto());
        updateDto.setValue(new UpdatePriceValueRequestDto());
        updateDto.getTier().setTierName("Placeholder");
        updateDto.getValue().setPriceAmount(new BigDecimal("1"));
        updateDto.getValue().setCurrency("USD");
        updateDto.getValue().setValueType("ABSOLUTE");
        updateDto.getTier().setConditions(List.of(getDummyConditionDto()));

        // Test 1: PUT with non-existent Component ID
        mockMvc.perform(put("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        nonExistentId, existingTierId) // <--- USED LOCAL VARIABLE
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Pricing Component not found")));

        // Test 2: PUT with non-existent Tier ID
        mockMvc.perform(put("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        existingComponentId, nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Pricing Tier not found")));

        // Test 3: DELETE with non-existent Tier ID
        mockMvc.perform(delete("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        existingComponentId, nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Pricing Tier not found")));
    }
}