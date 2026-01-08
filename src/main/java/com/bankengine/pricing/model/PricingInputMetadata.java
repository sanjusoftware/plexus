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

    // The key used in the PricingInput.customAttributes map and TierCondition.attributeName
    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    // The type required for correct DRL generation and runtime casting.
    // Must be a valid Java class name or a known alias: STRING, DECIMAL, INTEGER, BOOLEAN
    @Column(name = "data_type", nullable = false)
    private String dataType;

    // Friendly name for UI display
    @Column(name = "display_name")
    private String displayName;

    // Helper to get the fully qualified type name for DRL casting
    public String getFqnType() {
        return switch (dataType.toUpperCase()) {
            case "DECIMAL" -> "java.math.BigDecimal";
            case "INTEGER" -> "java.lang.Long"; // Use Long for integer types in the map
            case "BOOLEAN" -> "java.lang.Boolean";
            case "DATE"    -> "java.time.LocalDate";
            default -> "java.lang.String"; // Default to String (no casting needed for Map.get)
        };
    }
}