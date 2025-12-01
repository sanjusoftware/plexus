package com.bankengine.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The consolidated request for calculating the total price of a bundle (list of products).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedPriceRequest {

    // Unique identifier for multi-tenancy context
    private String bankId;

    // List of individual product pricing requests (the components of the bundle)
    private List<PriceRequest> productRequests;

    // Custom attributes for rule evaluation (e.g., customerSegment)
    private Map<String, String> clientAttributes;
}