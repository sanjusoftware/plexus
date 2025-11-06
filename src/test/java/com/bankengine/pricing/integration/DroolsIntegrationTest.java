package com.bankengine.pricing.integration;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PriceValue.ValueType;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.model.TierCondition.Operator;
import com.bankengine.pricing.repository.*;
import com.bankengine.pricing.service.PricingCalculationService;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DroolsIntegrationTest {

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
    private KieContainerReloadService kieContainerReloadService;
    @Autowired
    private PricingComponentService pricingComponentService;
    @Autowired
    private PricingCalculationService pricingCalculationService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_COMPONENT_NAME = "FeeComponent";
    private static final String TEST_SEGMENT = "PREMIUM";
    private static final String TEST_SEGMENT_OTHER = "STANDARD";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal EXPECTED_PRICE_INITIAL = new BigDecimal("10.00");
    private static final BigDecimal EXPECTED_PRICE_NEW = new BigDecimal("50.00");

    private void safeDeleteAllPricingData() {
        // MUST DELETE IN DEPENDENCY ORDER: Child before Parent
        tierConditionRepository.deleteAllInBatch();
        valueRepository.deleteAllInBatch();
        tierRepository.deleteAllInBatch();
        productPricingLinkRepository.deleteAllInBatch();
        componentRepository.deleteAllInBatch();
    }
    // --------------------------------

    @BeforeEach
    void setUp() {
        // 💡 CRITICAL FIX 1: Wrap all setup (cleanup + seeding) in a single transaction.
        transactionTemplate.executeWithoutResult(status -> {

            // CRITICAL FIX 2: Execute safe sequential cleanup FIRST
            safeDeleteAllPricingData();

            // H2 FIX: Reset ID sequences for clean component creation (keep this)
            entityManager.createNativeQuery("ALTER TABLE PRICING_COMPONENT ALTER COLUMN ID RESTART WITH 1").executeUpdate();

            // Seed initial rule (PREMIUM: 10.00)
            seedDatabaseWithTestRule(TEST_COMPONENT_NAME, TEST_SEGMENT, EXPECTED_PRICE_INITIAL);

            // Flush and clear the EntityManager after setup to ensure data is committed
            // before the transaction ends and the KieContainer reload happens.
            entityManager.flush();
            entityManager.clear();
        });

        // Reload the KieContainer AFTER the data is committed.
        assertTrue(kieContainerReloadService.reloadKieContainer(),
                "KieContainer must reload successfully after data seeding.");
    }

    // --- NEW: Explicit Cleanup After Each Test ---
    @AfterEach
    void tearDown() {
        // Ensures any uncommitted data from the test method (if @Transactional was used)
        // or any lingering state is cleared safely before the next @BeforeEach runs.
        transactionTemplate.executeWithoutResult(status -> {
            safeDeleteAllPricingData();
        });
    }

    private void seedDatabaseWithTestRule(String name, String segment, BigDecimal price) {
        // 1. Price Value
        PriceValue priceValue = new PriceValue();
        priceValue.setPriceAmount(price);
        priceValue.setValueType(ValueType.ABSOLUTE);
        priceValue.setCurrency("USD");

        // 2. Tier Condition: customerSegment == segment
        TierCondition condition = new TierCondition();
        condition.setAttributeName("customerSegment");
        condition.setOperator(Operator.EQ);
        condition.setAttributeValue(segment);

        // 3. Pricing Component (save first to get ID)
        PricingComponent component = new PricingComponent();
        component.setName(name);
        component.setType(PricingComponent.ComponentType.FEE);
        componentRepository.save(component);

        // 4. Pricing Tier
        PricingTier tier = new PricingTier();
        tier.setTierName("Tier for " + segment);

        // Set the bidirectional link *before* using Set.of()
        // to prevent Hibernate from saving the 'condition' with a NULL foreign key.
        condition.setPricingTier(tier); // Link Condition (child) to Tier (parent)
        priceValue.setPricingTier(tier); // Link PriceValue (child) to Tier (parent)

        // Set collections on the Tier
        tier.setConditions(Set.of(condition));
        tier.setPriceValues(Set.of(priceValue));

        // Set the crucial @ManyToOne link
        tier.setPricingComponent(component); // Links Tier (child) to Component (parent)

        // 5. Save the Tier (This persists the Tier and generates its ID)
        tierRepository.save(tier);

        // 6. Save children of the Tier. The links were already set in step 4.
        // NOTE: This is necessary because the Tier does not have Cascade.ALL.
        tierConditionRepository.save(condition);
        valueRepository.save(priceValue);

        // 7. Optional: Update component's in-memory collection (for immediate use)
        component.getPricingTiers().add(tier);
    }

    /**
     * Test the full pipeline: DB Data -> DRL Generation -> KieContainer Compilation -> Rule Firing. (Your original test)
     */
    @Test
    void testInitialDbRuleFiresSuccessfully() {
        PriceValue result = pricingCalculationService.getCalculatedPrice(TEST_SEGMENT, TEST_AMOUNT);
        assertEquals(EXPECTED_PRICE_INITIAL, result.getPriceAmount(), "Initial rule execution should return 10.00.");
    }

    // --- New Lifecycle Tests ---

//    /**
//     * 1. Test Rule Reload After Creation (Lifecycle Test)
//     * Verifies that the automatic reload trigger in PricingComponentService works.
//     */
//    @Test
//    @WithMockUser(roles = {"ADMIN"}) // Must be authenticated to access the service method
//    void testRuleIsLiveImmediatelyAfterCreation() {
//        // ARRANGE: Get the ID of the component we seeded in setUp (PREMIUM: 10.00)
//        // The existing component is not used but kept for context.
//        PricingComponent component = componentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow();
//
//        // --- Setup for the NEW component and rule (STANDARD: 50.00) ---
//
//        // 1. Create a NEW Pricing Component
//        PricingComponent newComponent = new PricingComponent();
//        newComponent.setName("NewFeeComponent");
//        newComponent.setType(PricingComponent.ComponentType.FEE);
//        newComponent = componentRepository.save(newComponent);
//        Long newComponentId = newComponent.getId();
//
//        // 2. Setup the PRICE VALUE DTO
//        CreatePriceValueRequestDto valueDto = new CreatePriceValueRequestDto();
//        valueDto.setPriceAmount(EXPECTED_PRICE_NEW); // 50.00
//        valueDto.setValueType("ABSOLUTE");
//        valueDto.setCurrency("USD");
//
//        // 3. Setup the TIER DTO
//        CreatePricingTierRequestDto tierDto = new CreatePricingTierRequestDto();
//        tierDto.setTierName("Standard Tier");
//
//        // Define the required rule condition: customerSegment == "STANDARD"
//        TierConditionDto conditionDto = new TierConditionDto();
//        conditionDto.setAttributeName("customerSegment");
//        conditionDto.setOperator(TierCondition.Operator.EQ);
//        conditionDto.setAttributeValue(TEST_SEGMENT_OTHER); // "STANDARD"
//
//        tierDto.setConditions(List.of(conditionDto));
//
//        // ACT 2: Call the service method, triggering the automatic rule reload
//        // This creates a rule: Rule_NewFeeComponent_Tier_X (customerSegment == "STANDARD" -> 50.00)
//        // The service method is assumed to handle the DTOs and call the KieContainerReloadService.
//        pricingComponentService.addTierAndValue(
//                newComponentId,
//                tierDto, // DTO now includes the required condition
//                valueDto);
//
//        // ASSERT: The new rule should be immediately active and fire for the new segment
//        // We test the calculation service with the new segment ("STANDARD")
//        PriceValue result = pricingCalculationService.getCalculatedPrice(TEST_SEGMENT_OTHER, TEST_AMOUNT);
//
//        assertEquals(EXPECTED_PRICE_NEW, result.getPriceAmount(),
//                "The new rule for the 'STANDARD' segment should be active immediately after creation and return 50.00.");
//    }

    /**
     * 2. Test Rule Deletion and Fallback (Safety Test)
     */
    @Test
    void testRuleReloadAfterFullComponentDeletion() {
        // ARRANGE: Get the committed component's ID
        final PricingComponent[] componentRef = new PricingComponent[1];

        // CRITICAL FIX: Wrap initial data fetching in a read-only transaction
        transactionTemplate.execute(status -> {
            // ARRANGE: Get the committed component's ID
            PricingComponent component = componentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow(
                    () -> new RuntimeException("Setup component not found.")
            );
            componentRef[0] = component;
            return null;
        });

        // Extract the component and IDs from the reference
        Long componentId = componentRef[0].getId();

        // Fetch the Tier ID is safe since componentRef[0] is initialized
        Long tierId = componentRepository.findFirstTierIdByComponentId(componentId).orElseThrow(
                () -> new RuntimeException("Tier not found in the database for the component.")
        );

        // ACT 1: Execute baseline check
        PriceValue resultBefore = pricingCalculationService.getCalculatedPrice(TEST_SEGMENT, TEST_AMOUNT);
        assertEquals(EXPECTED_PRICE_INITIAL, resultBefore.getPriceAmount(), "Baseline rule execution must succeed (10.00).");

        // ACT 2: Delete the Tier (Runs in REQUIRES_NEW and COMMITS deletion)
        pricingComponentService.deleteTierAndValue(componentId, tierId);

        Assertions.assertEquals(0L, getTierCountDirectly(componentId),
                "Database tier count must be zero after first deletion transaction commits.");

        // ACT 3: Delete the component (Wrapped in transactionTemplate for cache isolation/fresh connection context)
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                pricingComponentService.deletePricingComponent(componentId);
            }
        });

        // ACT 4: Perform reload (Optional stability check)
        boolean success = kieContainerReloadService.reloadKieContainer();
        assertTrue(success, "KieContainer must reload successfully after component deletion.");

        // ASSERT: Calculation should now fail (rules are deleted)
        assertThrows(NotFoundException.class,
                () -> pricingCalculationService.getCalculatedPrice(TEST_SEGMENT, TEST_AMOUNT),
                "Rule execution must now throw NotFoundException after rule deletion.");
    }

    private long getTierCountDirectly(Long componentId) {
        // This query bypasses ALL JPA/Hibernate layers and hits the DB directly
        String sql = "SELECT COUNT(id) FROM pricing_tier WHERE component_id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, componentId);
    }

    private void assertTierCountIsZero(Long componentId) {
        // CRITICAL: Bypass JPA cache entirely for the final dependency check.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(ID) FROM PRICING_TIER WHERE COMPONENT_ID = ?",
                Integer.class,
                componentId
        );
        if (count != null && count > 0) {
            throw new DependencyViolationException(
                    "FATAL: The tier count is still " + count +
                            " when checked directly via JDBC. This is the root cause."
            );
        }
    }
//    /**
//     * 3. Test Rule Reload After Full Component Deletion (Safety Test)
//     */
//    @Test
//    void testRuleReloadAfterFullComponentDeletion() {
//
//        // ARRANGE: Get the component created by the committed setUp.
//        PricingComponent component = componentRepository.findByName(TEST_COMPONENT_NAME).orElseThrow();
//        Long componentId = component.getId();
//
//        // Find the Tier ID associated with the seeded rule
//        Long tierId = component.getPricingTiers().get(0).getId();
//
//        // ACT 1: Delete the tier (Uses REQUIRES_NEW, commits deletion).
//        pricingComponentService.deleteTierAndValue(componentId, tierId);
//
//
//        // ACT 2: Delete the component (Uses REQUIRES_NEW, commits deletion).
//        pricingComponentService.deletePricingComponent(componentId);
//
//        // ACT 3: Perform an *independent* manual reload
//        boolean success = kieContainerReloadService.reloadKieContainer();
//
//        // ASSERT: ... (rest of the assertions) ...
//        assertTrue(success, "KieContainer must successfully reload even after a major component and its rules are deleted.");
//
//        // FINAL CHECK: Ensure the original segment now fails (no rules left)
//        assertThrows(NotFoundException.class,
//                () -> pricingCalculationService.getCalculatedPrice(TEST_SEGMENT, TEST_AMOUNT),
//                "Calculation must still fail after full cleanup and reload.");
//    }

//    /**
//     * 4. Test Management Reload Endpoint Integration Test
//     */
//    @Test
//    @WithMockUser(roles = {"ADMIN"})
//    void testManagementReloadEndpoint() throws Exception {
//        // ARRANGE: URL for the Rule Management Controller
//        String reloadUrl = "/api/v1/rules/reload";
//
//        // ACT & ASSERT: Make a POST request, simulating an authenticated user with authority
//        mockMvc.perform(post(reloadUrl))
//                .andExpect(status().isOk());
//    }

}