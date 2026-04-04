package com.bankengine.pricing;

import com.bankengine.catalog.model.Product;
import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingTierRequest;
import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.PriceValue;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bankengine.common.util.CodeGeneratorUtil.generateValidCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PricingComponentIntegrationTest extends AbstractIntegrationTest {

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
    private TierConditionRepository tierConditionRepository;
    @Autowired
    private ProductPricingLinkRepository productPricingLinkRepository;
    @Autowired
    private TestTransactionHelper txHelper;

    private static final String ADMIN_ROLE = "PCIT_TEST_ADMIN";
    private static final String READER_ROLE = "PCIT_TEST_READER";
    private static final String BASE_URL = "/api/v1/pricing-components";

    @BeforeAll
    static void setupRoles(@Autowired TestTransactionHelper txHelperStatic) {
        seedBaseRoles(txHelperStatic, Map.of(
                ADMIN_ROLE, Set.of(
                        "pricing:component:create", "pricing:component:read",
                        "pricing:component:update", "pricing:component:delete",
                        "pricing:tier:create", "pricing:tier:update", "pricing:tier:delete"),
                READER_ROLE, Set.of("pricing:component:read")
        ));
    }

    @BeforeEach
    void setupMetadata() {
        txHelper.doInTransaction(txHelper::setupCommittedMetadata);
        entityManager.clear();
    }

    @AfterEach
    void cleanUp() {
        txHelper.doInTransaction(() -> {
            productPricingLinkRepository.deleteAllInBatch();
            tierConditionRepository.deleteAllInBatch();
            valueRepository.deleteAllInBatch();
            tierRepository.deleteAllInBatch();
            componentRepository.deleteAllInBatch();
        });
        txHelper.flushAndClear();
    }

    // --- HELPER METHODS ---

    private PricingComponent createComponent(String name) {
        return txHelper.doInTransaction(() -> txHelper.createPricingComponentInDb(name));
    }

    private PricingComponentRequest newPricingComponentRequest(String name) {
        PricingComponentRequest req = new PricingComponentRequest();
        req.setName(name);
        req.setCode(generateValidCode(name));
        req.setType("FEE");
        return req;
    }

    private PricingTierRequest getValidTierDto() {
        TierConditionDto cond = new TierConditionDto();
        cond.setAttributeName("customerSegment");
        cond.setOperator(TierCondition.Operator.EQ);
        cond.setAttributeValue("DEFAULT_SEGMENT");

        PriceValueRequest val = new PriceValueRequest();
        val.setPriceAmount(new BigDecimal("5.00"));
        val.setValueType("FEE_ABSOLUTE");

        return PricingTierRequest.builder()
                .name("Default Tier")
                .code("DEFAULT-TIER")
                .minThreshold(BigDecimal.ZERO)
                .effectiveDate(LocalDate.now())
                .conditions(List.of(cond))
                .priceValue(val)
                .build();
    }

    // --- COMPONENT LEVEL TESTS ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldHandleComponentCrudOperations() throws Exception {
        // CREATE
        String createJson = mockMvc.perform(postWithCsrf(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPricingComponentRequest("CrudFee"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(objectMapper.readTree(createJson).get("id").asText());

        // UPDATE
        PricingComponentRequest update = newPricingComponentRequest("UpdatedCrudFee");
        mockMvc.perform(patchWithCsrf(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("UpdatedCrudFee")));

        // READ
        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())));

        // DELETE
        mockMvc.perform(deleteWithCsrf(BASE_URL + "/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400OnInvalidComponentType() throws Exception {
        PricingComponentRequest dto = newPricingComponentRequest("BadType");
        dto.setType("INVALID_ENUM_VALUE");

        String expectedMessage = "Invalid value for pricing component type. Valid values are " +
                Arrays.stream(PricingComponent.ComponentType.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));

        mockMvc.perform(postWithCsrf(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(expectedMessage)));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldReturnForbiddenWhenCreatingWithoutPermission() throws Exception {
        mockMvc.perform(postWithCsrf(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPricingComponentRequest("NoAccess"))))
                .andExpect(status().isForbidden());
    }

    // --- TIER & VALUE OPERATIONS ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldManageTierLifecycleCorrectly() throws Exception {
        PricingComponent component = createComponent("TierLifecycleComp");
        PricingTierRequest tierReq = getValidTierDto();

        // 1. ADD TIER
        String addJson = mockMvc.perform(postWithCsrf(BASE_URL + "/{id}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawValue", is(5.00)))
                .andReturn().getResponse().getContentAsString();

        Long tierId = Long.valueOf(objectMapper.readTree(addJson).get("matchedTierId").asText());

        // 2. UPDATE TIER & VALUE
        tierReq.getPriceValue().setPriceAmount(new BigDecimal("12.50"));
        tierReq.getPriceValue().setValueType("FEE_PERCENTAGE");

        mockMvc.perform(putWithCsrf(BASE_URL + "/{cId}/tiers/{tId}", component.getId(), tierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawValue", is(12.50)))
                .andExpect(jsonPath("$.valueType", is("FEE_PERCENTAGE")));

        // 3. DELETE TIER
        mockMvc.perform(deleteWithCsrf(BASE_URL + "/{cId}/tiers/{tId}", component.getId(), tierId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldExposeRichSwaggerDocsForNestedTierEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/pricing-components/{componentId}/tiers")))
                .andExpect(content().string(containsString("Higher `priority` values are evaluated first; if omitted, the tier is assigned the lowest priority.")))
                .andExpect(content().string(containsString("Use parent read endpoints (`GET /api/v1/pricing-components` or `GET /api/v1/pricing-components/{id}`) to view tiers after creation.")))
                .andExpect(content().string(containsString("Segment-based fee tier")))
                .andExpect(content().string(containsString("Update validation failure")))
                .andExpect(content().string(containsString("Delete blocked by lifecycle")));
    }

    @Test
    @WithMockRole(roles = {READER_ROLE})
    void shouldExposeRichSwaggerDocsForPricingComponentGetByCodeEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/pricing-components/code/{code}")))
                .andExpect(content().string(containsString("Retrieve a pricing component by code")))
                .andExpect(content().string(containsString("If `version` is omitted, the latest available version for that code is returned within the current tenant.")))
                .andExpect(content().string(containsString("Pricing component by code")))
                .andExpect(content().string(containsString("Invalid version parameter")))
                .andExpect(content().string(containsString("Component code not found")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldClearConditionsWhenUpdatingWithEmptyList() throws Exception {
        Long compId = txHelper.doInTransaction(() -> txHelper.createLinkedTierAndValue("ClearCond", "Tier1"));

        // Fetch the generated Tier ID
        Long tierId = txHelper.doInTransaction(() ->
                componentRepository.findById(compId).get().getPricingTiers().get(0).getId()
        );

        PricingTierRequest updateReq = getValidTierDto();
        updateReq.setConditions(List.of()); // Explicitly empty

        mockMvc.perform(putWithCsrf(BASE_URL + "/{cId}/tiers/{tId}", compId, tierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        txHelper.flushAndClear();
        txHelper.doInTransaction(() -> {
            PricingTier tier = tierRepository.findById(tierId).orElseThrow();
            assertThat(tier.getConditions()).isEmpty();
        });
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn400WhenAddingTierWithInvalidPriceValueType() throws Exception {
        PricingComponent component = createComponent("ValueTypeValidation");
        PricingTierRequest req = getValidTierDto();
        req.getPriceValue().setValueType("INVALID_PRICE_TYPE");

        String expectedMessage = "Invalid value for price value type. Valid values are " +
                Arrays.stream(PriceValue.ValueType.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));

        mockMvc.perform(postWithCsrf(BASE_URL + "/{id}/tiers", component.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(expectedMessage)));
    }

    // --- CONFLICT & ERROR HANDLING ---

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturnConflictWhenDependenciesExist() throws Exception {
        PricingComponent component = createComponent("DependencyTest");
        Long compId = component.getId();

        // 1. PRODUCT LINK DEPENDENCY (Checked first)
        txHelper.doInTransaction(() -> {
            Product product = txHelper.createValidProduct("DepProd", "SAVINGS", com.bankengine.common.model.VersionableEntity.EntityStatus.ACTIVE);
            txHelper.linkProductToPricingComponent(product.getId(), compId, BigDecimal.ZERO);
        });

        mockMvc.perform(deleteWithCsrf(BASE_URL + "/{id}", compId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("linked to 1 products")));

        // 2. TIER DEPENDENCY (Checked second)
        txHelper.doInTransaction(() -> productPricingLinkRepository.deleteAllInBatch());
        txHelper.doInTransaction(() -> txHelper.createCommittedTierDependency(compId, "StandaloneTier"));

        mockMvc.perform(deleteWithCsrf(BASE_URL + "/{id}", compId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("association with 2 tiers exists")));
    }

    @Test
    @WithMockRole(roles = {ADMIN_ROLE})
    void shouldReturn404ForNonExistentComponentOrMismatchedTier() throws Exception {
        PricingTierRequest dto = getValidTierDto();

        // Non-existent component ID
        mockMvc.perform(putWithCsrf(BASE_URL + "/99999/tiers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Component not found")));
    }
}