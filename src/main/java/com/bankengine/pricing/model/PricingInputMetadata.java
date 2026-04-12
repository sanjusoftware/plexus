package com.bankengine.pricing.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
@JsonIgnoreProperties(ignoreUnknown = true)
public class PricingInputMetadata extends AuditableEntity {

    public enum AttributeSourceType {
        FACT_FIELD,
        CUSTOM_ATTRIBUTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private AttributeSourceType sourceType = AttributeSourceType.CUSTOM_ATTRIBUTE;

    @Column(name = "source_field", nullable = false)
    private String sourceField;

    @JsonIgnore
    public String getResolvedSourceField() {
        return (sourceField == null || sourceField.isBlank()) ? attributeKey : sourceField;
    }

    @JsonIgnore
    public String getFqnType() {
        return PricingDataType.fromString(dataType).getFqn();
    }

    @JsonIgnore
    public boolean needsQuotes() {
        return PricingDataType.fromString(dataType).isQuoted();
    }
}