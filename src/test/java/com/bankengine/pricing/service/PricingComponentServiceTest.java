package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PriceValueMapper;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.converter.PricingTierMapper;
import com.bankengine.pricing.converter.TierConditionMapper;
import com.bankengine.pricing.dto.PriceValueRequest;
import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingTierRequest;
import com.bankengine.pricing.dto.TieredPriceRequest;
import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.*;
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

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingComponentServiceTest extends BaseServiceTest {

    @Mock private PricingComponentRepository componentRepository;
    @Mock private PricingTierRepository tierRepository;
    @Mock private PriceValueRepository valueRepository;
    @Mock private TierConditionRepository tierConditionRepository;
    @Mock private ProductPricingLinkRepository productPricingLinkRepository;

    @Mock private PricingComponentMapper pricingComponentMapper;
    @Mock private PricingTierMapper pricingTierMapper;
    @Mock private PriceValueMapper priceValueMapper;
    @Mock private TierConditionMapper tierConditionMapper;
    @Mock private KieContainerReloadService reloadService;

    @InjectMocks
    private PricingComponentService componentService;

    @Test
    @DisplayName("Create Component - Throw exception for invalid enum type (Branch Coverage)")
    void createComponent_ShouldThrow_OnInvalidType() {
        PricingComponentRequest request = new PricingComponentRequest();
        request.setType("INVALID_ENUM_VALUE");

        // Mock the mapper call so it doesn't return null before the enum check
        when(pricingComponentMapper.toEntity(any())).thenReturn(new PricingComponent());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> componentService.createComponent(request));

        assertTrue(ex.getMessage().contains("Invalid component type"));
    }

    @Test
    @DisplayName("Update Tier Conditions - Handle null condition list (Branch Coverage)")
    void updateTierAndValue_ShouldHandleNullConditions() {
        // Arrange
        Long cId = 1L, tId = 2L;
        PricingTier tier = new PricingTier();
        tier.setConditions(new HashSet<>());

        when(componentRepository.findById(cId)).thenReturn(Optional.of(new PricingComponent()));

        PriceValue value = new PriceValue();
        TieredPriceRequest dto = new TieredPriceRequest();
        PricingTierRequest tierDto = new PricingTierRequest();
        tierDto.setConditions(null);
        dto.setTier(tierDto);

        PriceValueRequest valDto = new PriceValueRequest();
        valDto.setValueType("FEE_ABSOLUTE");
        valDto.setPriceAmount(java.math.BigDecimal.TEN);
        dto.setValue(valDto);

        when(tierRepository.findById(tId)).thenReturn(Optional.of(tier));
        when(valueRepository.findByPricingTierId(tId)).thenReturn(Optional.of(value));

        // Act
        componentService.updateTierAndValue(cId, tId, dto);

        // Assert
        assertTrue(tier.getConditions().isEmpty());
        verify(tierRepository).save(tier);
    }

    @Test
    @DisplayName("Delete Component - Throw 409 when linked to products (Branch Coverage)")
    void deletePricingComponent_ShouldThrow_WhenLinkedToProducts() {
        Long id = 1L;
        when(componentRepository.findById(id)).thenReturn(Optional.of(new PricingComponent()));
        when(tierRepository.countByPricingComponentId(id)).thenReturn(0L);

        // This hits the branch logic for product links
        when(productPricingLinkRepository.countByPricingComponentId(id)).thenReturn(1L);

        assertThrows(DependencyViolationException.class, () -> componentService.deletePricingComponent(id));
    }

    @Test
    @DisplayName("Update Component - Throw exception on invalid type (Branch Coverage)")
    void updateComponent_ShouldThrow_OnInvalidType() {
        Long id = 1L;
        PricingComponentRequest request = new PricingComponentRequest();
        request.setType("BAD_TYPE");

        when(componentRepository.findById(id)).thenReturn(Optional.of(new PricingComponent()));

        assertThrows(IllegalArgumentException.class, () -> componentService.updateComponent(id, request));
    }

    @Test
    @DisplayName("Update Tier - Throw 404 when Price Value is missing (Branch Coverage)")
    void updateTierAndValue_ShouldThrowNotFound_WhenValueMissing() {
        Long componentId = 1L, tierId = 2L;

        when(componentRepository.findById(componentId)).thenReturn(Optional.of(new PricingComponent()));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(new PricingTier()));
        when(valueRepository.findByPricingTierId(tierId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                componentService.updateTierAndValue(componentId, tierId, new TieredPriceRequest()));
    }
}