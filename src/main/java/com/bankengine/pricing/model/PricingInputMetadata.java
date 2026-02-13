package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines the structure and type of attributes used for condition building.
 * This table acts as a registry for all possible keys in the PricingInput.customAttributes map.
 */
@Entity
@Table(name = "pricing_input_metadata", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "attribute_key"})
})
@Getter
@Setter
@TenantEntity
public class PricingInputMetadata extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "display_name")
    private String displayName;

    public String getFqnType() {
        return PricingDataType.fromString(dataType).getFqn();
    }

    public boolean needsQuotes() {
        return PricingDataType.fromString(dataType).isQuoted();
    }
}