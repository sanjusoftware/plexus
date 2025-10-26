package com.bankengine.pricing;

import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PriceValueRepository;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private Long componentIdForTierTests;
    private Long tierIdForTests;
    private Long valueIdForTests;

    // Setup a full Component -> Tier -> Value chain
    private void createLinkedTierAndValue(String componentName, String tierName) {
        // 1. Create Component
        PricingComponent component = new PricingComponent();
        component.setName(componentName);
        component.setType(PricingComponent.ComponentType.RATE);
        PricingComponent savedComponent = componentRepository.save(component);
        componentIdForTierTests = savedComponent.getId();

        // 2. Create Tier
        PricingTier tier = new PricingTier();
        tier.setPricingComponent(savedComponent);
        tier.setTierName(tierName);
        tier.setMinThreshold(new BigDecimal("0.00"));
        PricingTier savedTier = tierRepository.save(tier);
        tierIdForTests = savedTier.getId();

        // 3. Create Value
        PriceValue value = new PriceValue();
        value.setPricingTier(savedTier);
        value.setPriceAmount(new BigDecimal("10.00"));
        value.setCurrency("USD");
        value.setValueType(PriceValue.ValueType.ABSOLUTE);
        PriceValue savedValue = valueRepository.save(value);
        valueIdForTests = savedValue.getId();
    }

    // Helper method to create a valid DTO for POST/PUT requests
    private CreatePricingComponentRequestDto getCreateDto(String name) {
        CreatePricingComponentRequestDto dto = new CreatePricingComponentRequestDto();
        dto.setName(name);
        dto.setType("FEE"); // Assuming FEE is a valid ComponentType enum value
        return dto;
    }

    // Helper method to create an entity directly in the DB
    private PricingComponent createPricingComponentInDb(String name) {
        PricingComponent component = new PricingComponent();
        component.setName(name);
        component.setType(PricingComponent.ComponentType.RATE); // Use a different type for variety
        return componentRepository.save(component);
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
        createPricingComponentInDb("ComponentA");
        createPricingComponentInDb("ComponentB");
        long expectedCount = initialCount + 2;

        mockMvc.perform(get("/api/v1/pricing-components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", is((int)expectedCount)));
    }

    @Test
    @WithMockUser
    void shouldReturn200AndComponentById() throws Exception {
        PricingComponent savedComponent = createPricingComponentInDb("MortgageRate");

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
        PricingComponent savedComponent = createPricingComponentInDb("OldRate");
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
        PricingComponent component = createPricingComponentInDb("DeletableComponent");
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
        // ARRANGE: Create component
        PricingComponent component = createPricingComponentInDb("ComponentWithTiers");

        // ARRANGE: Manually create a linked PricingTier (simulating dependency)
        PricingTier tier = new PricingTier();
        tier.setPricingComponent(component);
        tier.setTierName("Tier 1");
        tierRepository.save(tier);

        // ACT: Attempt to delete the component
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
        PricingComponent component = createPricingComponentInDb("TieredComponent");

        TierValueDto requestDto = getValidTierValueDto();

        mockMvc.perform(post("/api/v1/pricing-components/{componentId}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceAmount", is(5.00)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.pricingTierId").isNumber());
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
        createLinkedTierAndValue("ComponentToUpdate", "InitialTier");

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
                        componentIdForTierTests, tierIdForTests)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                // ASSERT: Check the updated PriceValue fields
                .andExpect(jsonPath("$.priceAmount", is(15.50)))
                .andExpect(jsonPath("$.currency", is("EUR")))
                // ASSERT: Check that the tier entity was updated (requires retrieval or checking DB)
                // For simplicity, we check the PriceValue DTO, which confirms the service ran.
                .andExpect(jsonPath("$.pricingTierId", is(tierIdForTests.intValue())));

        // VERIFY: Manually check the Tier entity in the DB
        PricingTier updatedTier = tierRepository.findById(tierIdForTests).get();
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
        createLinkedTierAndValue("ComponentToDeleteFrom", "TierToDelete");
        Long finalTierId = tierIdForTests;
        Long finalValueId = valueIdForTests;

        // ACT: Call DELETE /{componentId}/tiers/{tierId}
        mockMvc.perform(delete("/api/v1/pricing-components/{componentId}/tiers/{tierId}",
                        componentIdForTierTests, finalTierId))
                .andExpect(status().isNoContent()); // Expect 204 No Content

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
        createLinkedTierAndValue("ComponentFor404Test", "ValidTier");
        Long existingComponentId = componentIdForTierTests;
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
                        nonExistentId, tierIdForTests)
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