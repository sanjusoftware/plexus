package com.bankengine.pricing.service;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.service.drl.DroolsExpressionBuilder;

public abstract class AbstractRuleBuilderService {

    protected final PricingComponentRepository componentRepository;
    protected final PricingInputMetadataService metadataService;
    protected final DroolsExpressionBuilder droolsExpressionBuilder;

    public AbstractRuleBuilderService(
            PricingComponentRepository componentRepository,
            PricingInputMetadataService metadataService,
            DroolsExpressionBuilder droolsExpressionBuilder) {
        this.componentRepository = componentRepository;
        this.metadataService = metadataService;
        this.droolsExpressionBuilder = droolsExpressionBuilder;
    }

    protected PricingInputMetadata getFactAttributeMetadata(String attributeName) {
        return metadataService.getMetadataEntityByKey(attributeName);
    }

    public abstract String buildAllRulesForCompilation();
}