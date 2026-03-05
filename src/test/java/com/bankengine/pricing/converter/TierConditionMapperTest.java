package com.bankengine.pricing.converter;

import com.bankengine.pricing.dto.TierConditionDto;
import com.bankengine.pricing.model.TierCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TierConditionMapperTest {
    private final TierConditionMapper mapper = new TierConditionMapperImpl();

    @Test
    void testConditionMapper() {
        TierCondition condition = new TierCondition();
        condition.setAttributeName("age");
        condition.setOperator(TierCondition.Operator.GT);
        condition.setAttributeValue("18");

        TierConditionDto dto = mapper.toDto(condition);
        assertNotNull(dto);
        assertEquals("age", dto.getAttributeName());

        TierCondition entity = mapper.toEntity(dto);
        assertEquals("age", entity.getAttributeName());
    }
}