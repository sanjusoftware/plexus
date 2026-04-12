package com.bankengine.data.seeding;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.PricingInputMetadata.AttributeSourceType;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.service.PricingAttributeKeys;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CoreMetadataSeeder {

    private final PricingInputMetadataRepository pricingInputMetadataRepository;

    public CoreMetadataSeeder(PricingInputMetadataRepository pricingInputMetadataRepository) {
        this.pricingInputMetadataRepository = pricingInputMetadataRepository;
    }

    /**
     * Seeds the minimum required core input metadata for DRL compilation for a specific bank.
     *
     * @param bankId the tenant identifier
     */
    @Transactional
    public void seedCorePricingInputMetadata(String bankId) {
        System.out.println("Seeding Core Pricing Input Metadata for bank: " + bankId);
        List<PricingInputMetadata> metadataList = List.of(
                createMetadata(PricingAttributeKeys.CUSTOMER_SEGMENT, "Customer Segment", "STRING", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.CUSTOMER_SEGMENT, bankId),
                createMetadata(PricingAttributeKeys.TRANSACTION_AMOUNT, "Transaction Amount", "DECIMAL", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.TRANSACTION_AMOUNT, bankId),
                createMetadata(PricingAttributeKeys.EFFECTIVE_DATE, "Effective Date", "DATE", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.EFFECTIVE_DATE, bankId),
                createMetadata(PricingAttributeKeys.PRODUCT_ID, "Product ID", "LONG", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.PRODUCT_ID, bankId),
                createMetadata(PricingAttributeKeys.PRODUCT_BUNDLE_ID, "Product Bundle ID", "LONG", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.PRODUCT_BUNDLE_ID, bankId),
                createMetadata(PricingAttributeKeys.GROSS_TOTAL_AMOUNT, "Gross Total Amount", "DECIMAL", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.GROSS_TOTAL_AMOUNT, bankId),
                createMetadata(PricingAttributeKeys.BANK_ID, "Bank ID", "STRING", AttributeSourceType.CUSTOM_ATTRIBUTE, PricingAttributeKeys.BANK_ID, bankId)
        );

        for (PricingInputMetadata metadata : metadataList) {
            pricingInputMetadataRepository.findByBankIdAndAttributeKey(bankId, metadata.getAttributeKey())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setDisplayName(metadata.getDisplayName());
                                existing.setDataType(metadata.getDataType());
                                existing.setSourceType(metadata.getSourceType());
                                existing.setSourceField(metadata.getSourceField());
                                pricingInputMetadataRepository.save(existing);
                            },
                            () -> pricingInputMetadataRepository.save(metadata)
                    );
        }
    }

    private PricingInputMetadata createMetadata(String key, String displayName, String dataType,
                                                AttributeSourceType sourceType, String sourceField, String bankId) {
        return PricingInputMetadata.builder()
                .attributeKey(key)
                .displayName(displayName)
                .dataType(dataType)
                .sourceType(sourceType)
                .sourceField(sourceField)
                .bankId(bankId)
                .build();
    }

}