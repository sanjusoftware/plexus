package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.PricingComponentResponse;
import com.bankengine.pricing.dto.PricingTierResponse;
import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.TierCondition;
import com.bankengine.test.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingMappersIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PricingComponentMapper componentMapper;
    @Autowired
    private PricingInputMetadataMapper metadataMapper;
    @Autowired
    private PricingTierMapper tierMapper;
    @Autowired
    private TierConditionMapper conditionMapper;

    @Test
    void testComponentMapper() {
        PricingComponent component = new PricingComponent();
        component.setName("Test Comp");
        component.setType(PricingComponent.ComponentType.FEE);

        PricingComponentResponse response = componentMapper.toResponseDto(component);
        assertNotNull(response);
        assertEquals("Test Comp", response.getName());
    }

    @Test
    void testComponentMapperList() {
        PricingComponent component = new PricingComponent();
        component.setName("Test Comp");
        List<PricingComponentResponse> responses = componentMapper.toResponseDtoList(List.of(component));
        assertEquals(1, responses.size());
    }

    @Test
    void testTierMapper() {
        PricingTier tier = new PricingTier();
        tier.setTierName("Tier 1");
        tier.setMinThreshold(BigDecimal.TEN);

        PricingTierResponse response = tierMapper.toResponse(tier);
        assertNotNull(response);
        assertEquals("Tier 1", response.getTierName());
    }

    @Test
    void testConditionMapper() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("age");
        condition.setOperator(TierCondition.Operator.GT);
        condition.setAttributeValue("18");

        TierConditionDto dto = conditionMapper.toDto(condition);
        assertNotNull(dto);
        assertEquals("age", dto.getAttributeName());

        TierCondition entity = conditionMapper.toEntity(dto);
        assertEquals("age", entity.getAttributeName());
    }
}