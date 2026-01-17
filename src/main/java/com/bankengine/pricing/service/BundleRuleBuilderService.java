package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PriceValue;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BundleRuleBuilderService extends AbstractRuleBuilderService {

    public BundleRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        super(componentRepository, metadataService, droolsExpressionBuilder);
    }

    @Override protected String getFactType() { return "com.bankengine.rules.model.BundlePricingInput"; }
    @Override protected String getPackageSubPath() { return "bundle"; }

    @Override
    protected List<PricingComponent> fetchComponents() {
        return componentRepository.findByTypeIn(
                List.of(PricingComponent.ComponentType.WAIVER, PricingComponent.ComponentType.DISCOUNT)
        );
    }

    @Override
    protected String buildRHSAction(PricingComponent component, PricingTier tier) {
        if (tier.getPriceValues() == null || tier.getPriceValues().isEmpty()) {
            return "        // No adjustment defined";
        }
        PriceValue pv = tier.getPriceValues().iterator().next();
        return String.format("""
                    $input.addAdjustment("%s_Tier%d", new BigDecimal("%s"), "%s");
                    update($input);""",
                component.getName().replaceAll("\\s", "_"), tier.getId(),
                pv.getRawValue().toPlainString(), pv.getValueType().name());
    }
}