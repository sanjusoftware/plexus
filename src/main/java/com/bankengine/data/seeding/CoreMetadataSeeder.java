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

    private PricingInputMetadata createMetadata(String key, String displayName, String dataType, String bankId) {
        PricingInputMetadata metadata = new PricingInputMetadata();
        metadata.setAttributeKey(key);
        metadata.setDisplayName(displayName);
        metadata.setDataType(dataType);
        metadata.setBankId(bankId);
        return metadata;
    }

    /**
     * Seeds the minimum required core input metadata for DRL compilation for a specific bank.
     * @param bankId the tenant identifier
     */
    @Transactional
    public void seedCorePricingInputMetadata(String bankId) {
        if (pricingInputMetadataRepository.count() == 0) {
            System.out.println("Seeding Core Pricing Input Metadata for bank: " + bankId);
            pricingInputMetadataRepository.saveAll(List.of(
                    createMetadata("customerSegment", "Client Segment", "STRING", bankId),
                    createMetadata("transactionAmount", "Transaction Amount", "DECIMAL", bankId),
                    createMetadata("productId", "Product ID", "LONG", bankId),
                    createMetadata("bankId", "Bank ID", "STRING", bankId)
            ));
        }
    }
}