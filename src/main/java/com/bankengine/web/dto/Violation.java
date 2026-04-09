package com.bankengine.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Represents a single business rule violation or validation error.")
public class Violation {

    @Schema(description = "The name of the field that caused the violation. Optional.", example = "pricing[0].fixedValue")
    private final String field;

    @Schema(description = "Human-readable reason for the violation.", example = "Value exceeds the maximum allowed for this component type.")
    private final String reason;

    @Schema(description = "Severity of the violation.", example = "ERROR")
    private final Severity severity;

    public enum Severity {
        ERROR, WARNING
    }
}
