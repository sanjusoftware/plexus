package com.bankengine.pricing;

import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.*;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.bankengine.test.config.WithMockRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PricingComponentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PricingComponentRepository componentRepository;
    @Autowired private PricingTierRepository tierRepository;
    @Autowired private PriceValueRepository valueRepository;
    @Autowired private TierConditionRepository tierConditionRepository;
    @Autowired private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private TestTransactionHelper txHelper;

    public static final String ROLE_PREFIX = "PCIT_";
    private static final String ADMIN_ROLE = ROLE_PREFIX + "TEST_ADMIN";
    private static final String READER_ROLE = ROLE_PREFIX + "TEST_READER";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
            ADMIN_ROLE, Set.of(
                "pricing:component:create", "pricing:component:read", "pricing:component:update", "pricing:component:delete",
                "pricing:tier:create", "pricing:tier:update", "pricing:tier:delete"),
            READER_ROLE, Set.of("pricing:component:read")
        ));
    }

    @BeforeEach
    void setupMetadata() {
        txHelper.doInTransaction(() -> {
            txHelper.setupCommittedMetadata();
        });
        entityManager.clear();
    }

    @AfterEach
    void cleanUp() {
        txHelper.doInTransaction(() -> {
            productPricingLinkRepository.deleteAllInBatch(); // 1. Links
            tierConditionRepository.deleteAllInBatch();      // 2. Conditions
            valueRepository.deleteAllInBatch();              // 3. Values
            tierRepository.deleteAllInBatch();               // 4. Tiers
            pricingComponentRepository.deleteAllInBatch();   // 5. Components
        });
        txHelper.flushAndClear();
    }

    private PricingComponent createComponent(String name) {
        return txHelper.doInTransaction(() -> {
            return txHelper.createPricingComponentInDb(name);
        });
    }

    private PricingTier getTierFromComponentId(Long componentId) {
        return txHelper.doInTransaction(() -> {
            PricingComponent component = componentRepository.findById(componentId)
                    .orElseThrow(() -> new IllegalStateException("Component not found"));
            return component.getPricingTiers().iterator().next();
        });
    }

    private PricingComponentRequest getCreateDto(String name) {
        PricingComponentRequest req = new PricingComponentRequest();
        req.setName(name);
        req.setType("FEE");
        return req;
    }

    private TieredPriceRequest getValidTierValueDto() {
        TierConditionDto cond = new TierConditionDto();
        cond.setAttributeName("customerSegment");
        cond.setOperator(TierCondition.Operator.EQ);
        cond.setAttributeValue("DEFAULT_SEGMENT");

        PricingTierRequest tier = new PricingTierRequest();
        tier.setTierName("Default Tier");
        tier.setMinThreshold(BigDecimal.ZERO);
        tier.setEffectiveDate(LocalDate.now());
        tier.setConditions(List.of(cond));

        PriceValueRequest val = new PriceValueRequest();
        val.setPriceAmount(new BigDecimal("5.00"));
        val.setValueType("FEE_ABSOLUTE");

        TieredPriceRequest req = new TieredPriceRequest();
        req.setTier(tier);
        req.setValue(val);

        return req;
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
        PricingComponentRequest dto = getCreateDto("BadType");
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

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndListComponent() throws Exception {
        createComponent("ComponentA");
        createComponent("ComponentB");

        mockMvc.perform(get("/api/v1/pricing-components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn200AndComponentById() throws Exception {
        PricingComponent saved = createComponent("MortgageRate");

        mockMvc.perform(get("/api/v1/pricing-components/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MortgageRate")));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturn404WhenGettingNonExistentComponent() throws Exception {
        mockMvc.perform(get("/api/v1/pricing-components/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateComponentAndReturn200() throws Exception {
        PricingComponent saved = createComponent("OldRate");
        PricingComponentRequest updateDto = getCreateDto("NewRate");

        mockMvc.perform(put("/api/v1/pricing-components/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NewRate")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteComponentAndReturn204() throws Exception {
        PricingComponent saved = createComponent("Deletable");

        mockMvc.perform(delete("/api/v1/pricing-components/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingComponentWithTiers() throws Exception {
        PricingComponent component = createComponent("ComponentWithTiers");
        txHelper.doInTransaction(() -> {
            txHelper.createCommittedTierDependency(component.getId(), "Tier 1");
        });

        mockMvc.perform(delete("/api/v1/pricing-components/{id}", component.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Cannot delete component")))
                .andExpect(jsonPath("$.message", containsString("association with 1 tiers exists")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldAddTierAndValueToComponentAndReturn201() throws Exception {
        PricingComponent component = createComponent("TieredComponent");

        mockMvc.perform(post("/api/v1/pricing-components/{id}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getValidTierValueDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawValue", is(5.00)))
                .andExpect(jsonPath("$.componentCode").value("TieredComponent"))
                .andExpect(jsonPath("$.valueType").value("FEE_ABSOLUTE"));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404WhenAddingTierToNonExistentComponent() throws Exception {
        mockMvc.perform(post("/api/v1/pricing-components/99999/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(getValidTierValueDto())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldUpdateTierAndValueAndReturn200() throws Exception {
        Long componentId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("ComponentToUpdate", "InitialTier"));
        Long tierId = getTierFromComponentId(componentId).getId();

        // ARRANGE: Set the valueType to PERCENTAGE to match the test assertion
        TieredPriceRequest updateReq = getValidTierValueDto();
        updateReq.getTier().setTierName("UpdatedTierName");
        updateReq.getValue().setPriceAmount(new BigDecimal("15.50"));
        updateReq.getValue().setValueType("FEE_PERCENTAGE");

        // ACT
        mockMvc.perform(put("/api/v1/pricing-components/{cId}/tiers/{tId}", componentId, tierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawValue", is(15.50)))
                .andExpect(jsonPath("$.componentCode").value("ComponentToUpdate"))
                .andExpect(jsonPath("$.valueType").value("FEE_PERCENTAGE"));

        // VERIFY
        txHelper.doInTransaction(() -> {
            assertThat(tierRepository.findById(tierId).get().getTierName()).isEqualTo("UpdatedTierName");
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldDeleteTierAndValueAndReturn204() throws Exception {
        Long componentId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("CompDelete", "TierDelete"));
        Long tierId = getTierFromComponentId(componentId).getId();

        mockMvc.perform(delete("/api/v1/pricing-components/{cId}/tiers/{tId}", componentId, tierId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404ForNonExistentTierOrComponent() throws Exception {
        Long componentId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("Comp404", "Tier404"));
        Long tierId = getTierFromComponentId(componentId).getId();
        TieredPriceRequest dto = getValidTierValueDto();

        // 1. Bad Component
        mockMvc.perform(put("/api/v1/pricing-components/99999/tiers/{tId}", tierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Component not found")));

        // 2. Bad Tier
        mockMvc.perform(put("/api/v1/pricing-components/{cId}/tiers/99999", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Tier not found")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldClearConditionsWhenUpdatingWithEmptyList() throws Exception {
        Long componentId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("ClearCondComp", "InitialTier"));
        Long tierId = getTierFromComponentId(componentId).getId();

        TieredPriceRequest updateReq = getValidTierValueDto();
        updateReq.getTier().setConditions(List.of());

        mockMvc.perform(put("/api/v1/pricing-components/{cId}/tiers/{tId}", componentId, tierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        txHelper.doInTransaction(() -> {
            PricingTier tier = tierRepository.findById(tierId).get();
            assertThat(tier.getConditions()).isEmpty();
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenAddingTierWithInvalidValueType() throws Exception {
        PricingComponent component = createComponent("ValueTypeFail");
        TieredPriceRequest req = getValidTierValueDto();
        req.getValue().setValueType("NOT_A_VALID_TYPE");

        mockMvc.perform(post("/api/v1/pricing-components/{id}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid value type provided")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn409WhenDeletingComponentWithMultipleDependencies() throws Exception {
        PricingComponent component = createComponent("MultiDep");

        txHelper.doInTransaction(() -> {
            txHelper.createCommittedTierDependency(component.getId(), "CheckTier");
            ProductType type = txHelper.getOrCreateProductType("SAVINGS");
            Product product = txHelper.getOrCreateProduct("CheckProduct", type, "RETAIL");
            txHelper.linkProductToPricingComponent(product.getId(), component.getId(), BigDecimal.ZERO);
        });

        // 3. The service hits the Tier check first and exits
        mockMvc.perform(delete("/api/v1/pricing-components/{id}", component.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("association with 1 tiers exists")));
    }
}