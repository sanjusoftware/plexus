package com.bankengine.pricing;

import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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

    // --- ADDED: Helper method to retrieve Tier/Value IDs after component is created and committed ---
    private PricingTier getTierFromComponentId(Long componentId) {
        // Find the committed component and load its tiers. Assumes a single tier exists.
        Optional<PricingComponent> componentOpt = componentRepository.findById(componentId);
        if (componentOpt.isEmpty() || componentOpt.get().getPricingTiers().isEmpty()) {
            throw new IllegalStateException("Setup failed: Component or Tier not found in DB after creation.");
        }
        return componentOpt.get().getPricingTiers().stream().findFirst().get();
    }
    // ------------------------------------------------------------------------------------------------

    private CreatePricingComponentRequestDto getCreateDto(String name) {
        CreatePricingComponentRequestDto dto = new CreatePricingComponentRequestDto();
        dto.setName(name);
        dto.setType("FEE");
        return dto;
    }

    // =================================================================
    // 1. CREATE (POST) TESTS
    // =================================================================

    @Test
    @WithMockUser
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
    @WithMockUser
    void shouldReturn400OnCreateWithInvalidType() throws Exception {
        CreatePricingComponentRequestDto dto = getCreateDto("BadType");
        dto.setType("INVALID_TYPE");

        mockMvc.perform(post("/api/v1/pricing-components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid component type provided: INVALID_TYPE")));
    }

    // =================================================================
    // 2. RETRIEVE (GET) TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldReturn200AndListComponent() throws Exception {
        long initialCount = componentRepository.count();
        txHelper.createPricingComponentInDb("ComponentA");
        txHelper.createPricingComponentInDb("ComponentB");
        long expectedCount = initialCount + 2;

        mockMvc.perform(get("/api/v1/pricing-components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", is((int)expectedCount)));
    }

    @Test
    @WithMockUser
    void shouldReturn200AndComponentById() throws Exception {
        PricingComponent savedComponent = txHelper.createPricingComponentInDb("MortgageRate");

        mockMvc.perform(get("/api/v1/pricing-components/{id}", savedComponent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MortgageRate")))
                .andExpect(jsonPath("$.type", is("RATE")));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenGettingNonExistentComponent() throws Exception {
        mockMvc.perform(get("/api/v1/pricing-components/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // =================================================================
    // 3. UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockUser
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
                .andExpect(jsonPath("$.updatedAt").exists()); // Check Auditing
    }

    // =================================================================
    // 4. DELETE (DELETE) TESTS - DEPENDENCY CHECKS
    // =================================================================

    @Test
    @WithMockUser
    void shouldDeleteComponentAndReturn204() throws Exception {
        PricingComponent component = txHelper.createPricingComponentInDb("DeletableComponent");
        Long idToDelete = component.getId();

        mockMvc.perform(delete("/api/v1/pricing-components/{id}", idToDelete))
                .andExpect(status().isNoContent());

        // Verify deletion
        mockMvc.perform(get("/api/v1/pricing-components/{id}", idToDelete))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldReturn409WhenDeletingComponentWithTiers() throws Exception {
        // ARRANGE: 1. Create and COMMIT the component and COMMIT the dependency
        PricingComponent component = txHelper.createPricingComponentInDb("ComponentWithTiers");
        txHelper.createCommittedTierDependency(component.getId(), "Tier 1");

        // ACT: Attempt to delete the component.
        mockMvc.perform(delete("/api/v1/pricing-components/{id}", component.getId()))
                .andExpect(status().isConflict()) // 🚨 Expect 409 Conflict
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot delete Pricing Component ID")));
    }

    // =================================================================
    // 5. BUSINESS LOGIC (POST /tiers) TESTS
    // =================================================================

    @Test
    @WithMockUser
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
    @WithMockUser
    void shouldReturn404WhenAddingTierToNonExistentComponent() throws Exception {
        TierValueDto requestDto = getValidTierValueDto();

        mockMvc.perform(post("/api/v1/pricing-components/99999/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // --- Helper Method to create a valid TierValueDto ---
    private TierValueDto getValidTierValueDto() {
        CreatePricingTierRequestDto tierDto = new CreatePricingTierRequestDto();
        tierDto.setTierName("Default Tier");
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

    // =================================================================
    // 6. TIER/VALUE UPDATE (PUT) TESTS
    // =================================================================

    @Test
    @WithMockUser
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

        // VERIFY: Manually check the Tier entity in the DB
        PricingTier updatedTier = tierRepository.findById(tierId).get();
        assertThat(updatedTier.getTierName()).isEqualTo("UpdatedTierName");
        assertThat(updatedTier.getMaxThreshold()).isEqualTo(new BigDecimal("999.99"));
    }

    // =================================================================
    // 7. TIER/VALUE DELETE (DELETE) TESTS
    // =================================================================

    @Test
    @WithMockUser
    void shouldDeleteTierAndValueAndReturn204() throws Exception {
        // ARRANGE: Setup the records to be deleted
        Long componentId = txHelper.createLinkedTierAndValue("ComponentToDeleteFrom", "TierToDelete");

        // Fetch the IDs from the committed component
        PricingTier initialTier = getTierFromComponentId(componentId);
        Long finalTierId = initialTier.getId();
        // Assumes PriceValue has a 1-to-1 relationship with PricingTier and is cascaded or easily found
        Long finalValueId = initialTier.getPriceValues().stream().findFirst().get().getId();

        // ACT: Call DELETE /{componentId}/tiers/{tierId}
        mockMvc.perform(delete("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        componentId, finalTierId))
                .andExpect(status().isNoContent());

        entityManager.clear();
        // VERIFY 1: Tier entity is gone
        assertThat(tierRepository.findById(finalTierId)).isEmpty();

        // VERIFY 2: PriceValue entity is gone
        assertThat(valueRepository.findById(finalValueId)).isEmpty();
    }

    // =================================================================
    // 8. TIER/VALUE NOT FOUND TESTS
    // =================================================================

    @Test
    @WithMockUser
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