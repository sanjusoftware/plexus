package com.bankengine.data.seeding;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.model.PricingInputMetadata.AttributeSourceType;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
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
                createMetadata("customerSegment", "Customer Segment", "STRING", AttributeSourceType.CUSTOM_ATTRIBUTE, "customerSegment", bankId),
                createMetadata("transactionAmount", "Transaction Amount", "DECIMAL", AttributeSourceType.CUSTOM_ATTRIBUTE, "transactionAmount", bankId),
                createMetadata("effectiveDate", "Effective Date", "DATE", AttributeSourceType.CUSTOM_ATTRIBUTE, "effectiveDate", bankId),
                createMetadata("productId", "Product ID", "LONG", AttributeSourceType.CUSTOM_ATTRIBUTE, "productId", bankId),
                createMetadata("productBundleId", "Product Bundle ID", "LONG", AttributeSourceType.CUSTOM_ATTRIBUTE, "productBundleId", bankId),
                createMetadata("grossTotalAmount", "Gross Total Amount", "DECIMAL", AttributeSourceType.CUSTOM_ATTRIBUTE, "grossTotalAmount", bankId),
                createMetadata("bankId", "Bank ID", "STRING", AttributeSourceType.CUSTOM_ATTRIBUTE, "bankId", bankId)
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