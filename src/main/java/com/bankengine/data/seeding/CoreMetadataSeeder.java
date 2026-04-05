package com.bankengine.data.seeding;

import com.bankengine.pricing.model.PricingInputMetadata;
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
                createMetadata("customerSegment", "Client Segment", "STRING", bankId),
                createMetadata("transactionAmount", "Transaction Amount", "DECIMAL", bankId),
                createMetadata("productId", "Product ID", "LONG", bankId),
                createMetadata("bankId", "Bank ID", "STRING", bankId)
        );

        for (PricingInputMetadata metadata : metadataList) {
            pricingInputMetadataRepository.findByAttributeKey(metadata.getAttributeKey())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setDisplayName(metadata.getDisplayName());
                                existing.setDataType(metadata.getDataType());
                                pricingInputMetadataRepository.save(existing);
                            },
                            () -> pricingInputMetadataRepository.save(metadata)
                    );
        }
    }

    private PricingInputMetadata createMetadata(String key, String displayName, String dataType, String bankId) {
        return PricingInputMetadata.builder()
                .attributeKey(key).displayName(displayName).dataType(dataType).bankId(bankId).build();
    }

}