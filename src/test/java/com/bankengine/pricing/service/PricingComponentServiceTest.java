package com.bankengine.pricing.service;

import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.pricing.converter.PriceValueMapper;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.converter.PricingTierMapper;
import com.bankengine.pricing.converter.TierConditionMapper;
import com.bankengine.pricing.dto.*;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.PricingTierRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingComponentServiceTest extends BaseServiceTest {

    @Mock private PricingComponentRepository componentRepository;
    @Mock private PricingTierRepository tierRepository;
    @Mock private ProductPricingLinkRepository productPricingLinkRepository;
    @Mock private PricingComponentMapper pricingComponentMapper;
    @Mock private PricingTierMapper pricingTierMapper;
    @Mock private PriceValueMapper priceValueMapper;
    @Mock private TierConditionMapper tierConditionMapper;
    @Mock private KieContainerReloadService reloadService;

    @InjectMocks
    private PricingComponentService componentService;

    private static final String TEST_CODE = "FEE-001";

    // --- HELPER METHODS ---

    private PricingComponentRequest newPricingComponentRequest(String name) {
        PricingComponentRequest request = new PricingComponentRequest();
        request.setName(name);
        request.setCode(TEST_CODE); // Restored mandatory field
        request.setType("FEE");
        return request;
    }

    private PricingComponent getValidPricingComponent(VersionableEntity.EntityStatus status) {
        PricingComponent component = new PricingComponent();
        component.setBankId(TEST_BANK_ID);
        component.setStatus(status);
        component.setCode(TEST_CODE);
        component.setType(PricingComponent.ComponentType.FEE);
        return component;
    }

    // --- COMPONENT OPERATIONS ---

    @Test
    @DisplayName("Create Component - Success Flow")
    void createComponent_ShouldSucceed() {
        PricingComponentRequest request = new PricingComponentRequest();
        request.setName("Monthly Fee");
        request.setCode("MTH-FEE"); // Mandatory code
        request.setType("FEE");

        PricingComponent entity = new PricingComponent();
        entity.setBankId(TEST_BANK_ID);

        when(componentRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(componentRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(pricingComponentMapper.toEntity(request)).thenReturn(entity);
        when(componentRepository.save(any())).thenReturn(entity);

        componentService.createComponent(request);

        verify(componentRepository).save(entity);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    @DisplayName("Create Component - Throw exception for invalid enum type")
    void createComponent_ShouldThrow_OnInvalidType() {
        PricingComponentRequest request = newPricingComponentRequest("Test");
        request.setType("INVALID_TYPE");

        when(componentRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(componentRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> componentService.createComponent(request));
        assertTrue(ex.getMessage().contains("Invalid component type provided"),
            "Exception message should indicate the type error. Found: " + ex.getMessage());
    }

    // --- TIER & VALUE OPERATIONS ---

    @Test
    @DisplayName("Update Tier - Success with existing component check")
    void updateTierAndValue_ShouldSucceed() {
        Long cId = 1L, tId = 2L;
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        component.setId(cId);

        PricingTier tier = new PricingTier();
        tier.setId(tId);
        tier.setPricingComponent(component);
        tier.setConditions(new HashSet<>());
        tier.setPriceValues(new HashSet<>(List.of(new PriceValue())));

        when(componentRepository.findById(cId)).thenReturn(Optional.of(component));
        when(tierRepository.findById(tId)).thenReturn(Optional.of(tier));
        when(tierRepository.save(any(PricingTier.class))).thenReturn(tier);

        componentService.updateTierAndValue(cId, tId, new PricingTierRequest());

        assertTrue(tier.getConditions().isEmpty());
        verify(tierRepository).save(tier);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    @DisplayName("Update Tier - Throw 404 when component does not exist")
    void updateTierAndValue_ShouldThrowNotFound_WhenComponentMissing() {
        Long cId = 1L, tId = 2L;
        when(componentRepository.findById(cId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> componentService.updateTierAndValue(cId, tId, new PricingTierRequest()));
    }

    @Test
    @DisplayName("Add Tier - Success flow")
    void addTierAndValue_ShouldSucceed() {
        Long cId = 1L;
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        component.setId(cId);

        PricingTierRequest tierReq = new PricingTierRequest();
        PriceValueRequest valueReq = new PriceValueRequest();
        valueReq.setValueType("FEE_ABSOLUTE");

        PricingTier tier = new PricingTier();
        PriceValue value = new PriceValue();

        when(componentRepository.findById(cId)).thenReturn(Optional.of(component));
        when(pricingTierMapper.toEntity(tierReq)).thenReturn(tier);
        when(priceValueMapper.toEntity(valueReq)).thenReturn(value);
        when(tierRepository.save(any())).thenReturn(tier);
        when(priceValueMapper.toDetailDto(any())).thenReturn(mock(ProductPricingCalculationResult.PriceComponentDetail.class));

        componentService.addTierAndValue(cId, tierReq, valueReq);

        verify(tierRepository).save(tier);
    }

    // --- CONFLICT & ERROR HANDLING ---

    @Test
    @DisplayName("Delete Component - Throw 409 when dependencies exist")
    void deletePricingComponent_ShouldHandleConflict() {
        Long id = 1L;
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.ACTIVE);

        when(componentRepository.findById(id)).thenReturn(Optional.of(component));
        when(productPricingLinkRepository.countByPricingComponentId(id)).thenReturn(1L);

        DependencyViolationException ex = assertThrows(DependencyViolationException.class,
                () -> componentService.deletePricingComponent(id));

        assertTrue(ex.getMessage().contains("linked to 1 products"));
    }

    @Test
    @DisplayName("Update Component - Throw IllegalStateException if not DRAFT")
    void updateComponent_ShouldCheckStatus() {
        Long id = 1L;
        PricingComponent active = getValidPricingComponent(VersionableEntity.EntityStatus.ACTIVE);

        when(componentRepository.findById(id)).thenReturn(Optional.of(active));

        assertThrows(IllegalStateException.class,
            () -> componentService.updateComponent(id, newPricingComponentRequest("Fail")));
    }

    @Test
    @DisplayName("Deep Clone - Should recursively clone Tiers, PriceValues, and Conditions")
    void testCloneTiersInternal_deepCloneWorkflow() {
        // 1. ARRANGE: Create a complex source structure
        Long oldId = 1L;
        PricingComponent source = getValidPricingComponent(VersionableEntity.EntityStatus.ACTIVE);
        source.setId(oldId);

        PricingTier oldTier = new PricingTier();
        oldTier.setBankId(TEST_BANK_ID);

        PriceValue oldVal = new PriceValue();
        oldTier.setPriceValues(new HashSet<>(Set.of(oldVal)));

        TierCondition oldCond = new TierCondition();
        oldTier.setConditions(new HashSet<>(Set.of(oldCond)));

        source.setPricingTiers(List.of(oldTier));

        // Prepare the target (new version)
        PricingComponent target = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        target.setVersion(2);

        // 2. MOCK: Set up the recursive cloning calls
        PricingTier clonedTier = new PricingTier();
        PriceValue clonedValue = new PriceValue();

        when(pricingComponentMapper.clone(source)).thenReturn(target);
        when(pricingTierMapper.clone(oldTier)).thenReturn(clonedTier);
        when(priceValueMapper.clone(oldVal)).thenReturn(clonedValue);
        // Note: TierCondition uses toDto/toEntity mapping in your service logic
        when(tierConditionMapper.toDto(oldCond)).thenReturn(new TierConditionDto());
        when(tierConditionMapper.toEntity(any())).thenReturn(new TierCondition());

        when(componentRepository.findById(oldId)).thenReturn(Optional.of(source));
        when(componentRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(componentRepository.save(any())).thenReturn(target);

        // 3. ACT
        componentService.versionComponent(oldId, new VersionRequest("V2", null, null));

        // 4. ASSERT: Verify the "Deep" part of the clone
        verify(pricingTierMapper).clone(oldTier);
        verify(priceValueMapper).clone(oldVal);
        verify(tierConditionMapper).toDto(oldCond);

        // Check that the parent-child relationships were rebuilt on the new objects
        assertNotNull(target.getPricingTiers());
        assertEquals(1, target.getPricingTiers().size());

        PricingTier resultTier = target.getPricingTiers().get(0);
        assertEquals(target, resultTier.getPricingComponent(), "Tier must point to the NEW component");
        assertEquals(1, resultTier.getPriceValues().size(), "Value must be cloned");
        assertEquals(1, resultTier.getConditions().size(), "Condition must be cloned");
    }

    // --- SECURITY & LIFECYCLE BRANCHES ---

    @Test
    @DisplayName("Security - Should throw AccessDeniedException if bankId mismatches")
    void testGetPricingComponentById_securityBreach() {
        Long id = 1L;
        PricingComponent entity = getValidPricingComponent(VersionableEntity.EntityStatus.ACTIVE);
        entity.setBankId("ATTACKER_BANK");

        when(componentRepository.findById(id)).thenReturn(Optional.of(entity));
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> componentService.getPricingComponentById(id));
        assertEquals("You do not have permission to access this Pricing Component", ex.getMessage());
    }

    @Test
    @DisplayName("Version Component - Should handle deep clone of tiers and reload container")
    void testVersionComponent_workflow() {
        // Arrange
        Long oldId = 1L;
        PricingComponent source = getValidPricingComponent(VersionableEntity.EntityStatus.ACTIVE);
        source.setId(oldId);
        source.setCode("PRC-100");

        PricingTier oldTier = new PricingTier();
        source.setPricingTiers(List.of(oldTier));

        PricingComponent newVersion = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        newVersion.setVersion(2);

        VersionRequest request = new VersionRequest("New Version Name", null, null);

        when(componentRepository.findById(oldId)).thenReturn(Optional.of(source));
        when(pricingComponentMapper.clone(source)).thenReturn(newVersion);
        when(pricingTierMapper.clone(any(PricingTier.class))).thenReturn(new PricingTier());
        when(componentRepository.existsByBankIdAndCodeAndVersion(eq(TEST_BANK_ID), eq("PRC-100"), eq(2))).thenReturn(false);
        when(componentRepository.save(any(PricingComponent.class))).thenReturn(newVersion);

        componentService.versionComponent(oldId, request);

        verify(pricingComponentMapper).clone(source);
        verify(pricingTierMapper, atLeastOnce()).clone(any(PricingTier.class));
        verify(componentRepository).save(newVersion);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    @DisplayName("Activation - Should transition status and reload rules")
    void testActivateComponent_workflow() {
        // Arrange
        Long id = 1L;
        PricingComponent draft = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        when(componentRepository.findById(id)).thenReturn(Optional.of(draft));

        // Act
        componentService.activateComponent(id);

        // Assert
        assertEquals(VersionableEntity.EntityStatus.ACTIVE, draft.getStatus());
        verify(componentRepository).save(draft);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    @DisplayName("Branch: Should handle tiers with conditions and values in attachTiersToComponent")
    void testAttachTiersToComponent_complex() {
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);

        PricingTierRequest tierReq = new PricingTierRequest();
        tierReq.setConditions(List.of(new TierConditionDto()));
        tierReq.setPriceValue(new PriceValueRequest());

        when(pricingTierMapper.toEntity(any())).thenReturn(new PricingTier());
        when(tierConditionMapper.toEntity(any())).thenReturn(new TierCondition());
        when(priceValueMapper.toEntity(any())).thenReturn(new PriceValue());

        // This is called inside createComponent
        PricingComponentRequest request = newPricingComponentRequest("Complex");
        request.setPricingTiers(List.of(tierReq));

        when(componentRepository.existsByNameAndBankId(any(), any())).thenReturn(false);
        when(componentRepository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(pricingComponentMapper.toEntity(any())).thenReturn(component);
        when(componentRepository.save(any())).thenReturn(component);

        componentService.createComponent(request);

        assertNotNull(component.getPricingTiers());
        assertEquals(1, component.getPricingTiers().size());
        PricingTier tier = component.getPricingTiers().get(0);
        assertEquals(1, tier.getConditions().size());
        assertEquals(1, tier.getPriceValues().size());
    }

    @Test
    @DisplayName("Branch: Should handle updateTierAndValue with new conditions")
    void testUpdateTierAndValue_withConditions() {
        Long cId = 1L, tId = 2L;
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        component.setId(cId);

        PricingTier tier = new PricingTier();
        tier.setId(tId);
        tier.setPricingComponent(component);
        tier.setConditions(new HashSet<>());
        tier.setPriceValues(new HashSet<>(List.of(new PriceValue())));

        when(componentRepository.findById(cId)).thenReturn(Optional.of(component));
        when(tierRepository.findById(tId)).thenReturn(Optional.of(tier));
        when(tierRepository.save(any())).thenReturn(tier);
        when(tierConditionMapper.toEntity(any())).thenReturn(new TierCondition());

        PricingTierRequest request = new PricingTierRequest();
        request.setConditions(List.of(new TierConditionDto()));

        componentService.updateTierAndValue(cId, tId, request);

        assertEquals(1, tier.getConditions().size());
    }

    @Test
    @DisplayName("Branch: Should throw NotFound when tier does not belong to component")
    void testUpdateTierAndValue_mismatch() {
        Long cId = 1L, tId = 2L;
        PricingComponent component = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        component.setId(cId);

        PricingComponent otherComponent = getValidPricingComponent(VersionableEntity.EntityStatus.DRAFT);
        otherComponent.setId(99L);

        PricingTier tier = new PricingTier();
        tier.setId(tId);
        tier.setPricingComponent(otherComponent);

        when(componentRepository.findById(cId)).thenReturn(Optional.of(component));
        when(tierRepository.findById(tId)).thenReturn(Optional.of(tier));

        assertThrows(NotFoundException.class, () -> componentService.updateTierAndValue(cId, tId, new PricingTierRequest()));
    }
}