package com.bankengine.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration to define common behavior across all mappers.
 * Setting ReportingPolicy.IGNORE resolves all warnings for unmapped target fields (e.g., bankId).
 */
@MapperConfig(
        // This tells MapStruct to IGNORE any field in the target entity (like 'bankId')
        // that doesn't exist in the source DTO.
        unmappedTargetPolicy = ReportingPolicy.IGNORE,

        componentModel = "spring"
)
public interface MapStructConfig {
}