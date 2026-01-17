package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductRuleBuilderService extends AbstractRuleBuilderService {

    public ProductRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        super(componentRepository, metadataService, droolsExpressionBuilder);
    }

    @Override protected String getFactType() { return "com.bankengine.rules.model.PricingInput"; }
    @Override protected String getPackageSubPath() { return "pricing"; }

    @Override
    protected List<PricingComponent> fetchComponents() {
        return componentRepository.findAll();
    }

    @Override
    protected String buildRHSAction(PricingComponent component, PricingTier tier) {
        if (tier.getPriceValues() == null || tier.getPriceValues().isEmpty()) {
            return "        // No action defined";
        }

        PriceValue pv = tier.getPriceValues().iterator().next();

        // Ensure we have a default value type if one isn't specified,
        // though our validation should catch this earlier.
        String valueTypeStr = pv.getValueType() != null ? pv.getValueType().name() : "FEE_ABSOLUTE";

        return String.format("""
                    PriceValue priceValueFact = new PriceValue();
                    priceValueFact.setMatchedTierId(%dL);
                    priceValueFact.setRawValue(new BigDecimal("%s"));
                    priceValueFact.setValueType(PriceValue.ValueType.%s);
                    priceValueFact.setComponentCode("%s");
                    priceValueFact.setBankId("%s");
                    insert(priceValueFact);""",
                tier.getId(),
                pv.getRawValue(),
                valueTypeStr,
                component.getName().replaceAll("\\s", "_"),
                getSafeBankIdForDrl());
    }
}