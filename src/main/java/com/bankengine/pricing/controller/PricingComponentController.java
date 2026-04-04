package com.bankengine.pricing.controller;

import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.pricing.converter.PricingComponentMapper;
import com.bankengine.pricing.dto.PricingComponentRequest;
import com.bankengine.pricing.dto.PricingComponentResponse;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.web.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Component Management", description = "Operations for managing reusable pricing aggregates, including multi-dimensional Tiers and Price Values.")
@RestController
@RequestMapping("/api/v1/pricing-components")
@RequiredArgsConstructor
public class PricingComponentController {

    private final PricingComponentService pricingComponentService;
    private final PricingComponentMapper pricingComponentMapper;

    @Operation(summary = "Retrieve all pricing components",
            description = "Returns a list of all reusable pricing definitions including nested tiers and price values. Tenant isolation is applied automatically.")
    @ApiResponse(responseCode = "200", description = "List of components successfully retrieved.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PricingComponentResponse.class))))
    @GetMapping
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<List<PricingComponentResponse>> getAllPricingComponents() {
        return ResponseEntity.ok(pricingComponentService.findAllComponents());
    }

    @Operation(summary = "Retrieve a pricing component by ID",
            description = "Fetches the full aggregate of a pricing component, including its tier configuration and associated conditions.")
    @ApiResponse(responseCode = "200", description = "Component successfully retrieved.")
    @ApiResponse(responseCode = "404", description = "Pricing component not found.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<PricingComponentResponse> getPricingComponentById(
            @Parameter(description = "ID of the pricing component to retrieve", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(pricingComponentService.getComponentById(id));
    }

    @Operation(summary = "Retrieve a pricing component by code",
            description = "Fetches a pricing component using its business code. If `version` is provided, that exact version is returned. " +
                    "If `version` is omitted, the latest available version for that code is returned within the current tenant.")
    @ApiResponse(responseCode = "200", description = "Pricing component successfully retrieved.",
            content = @Content(
                    schema = @Schema(implementation = PricingComponentResponse.class),
                    examples = @ExampleObject(
                            name = "Pricing component by code",
                            value = """
                                    {
                                      "id": 100,
                                      "code": "MONTHLY_MAIN_FEE",
                                      "name": "Monthly maintenance fee",
                                      "type": "FEE",
                                      "description": "Standard monthly fee with segment-based tiers",
                                      "status": "ACTIVE",
                                      "version": 2,
                                      "proRataApplicable": false,
                                      "pricingTiers": [
                                        {
                                          "id": 1001,
                                          "name": "Premium Segment",
                                          "code": "PREMIUM_SEGMENT",
                                          "priority": 10,
                                          "priceValues": [
                                            {
                                              "componentCode": "MONTHLY_MAIN_FEE",
                                              "rawValue": 5.00,
                                              "valueType": "FEE_ABSOLUTE",
                                              "sourceType": "CATALOG"
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                    """)))
    @ApiResponse(responseCode = "400", description = "Invalid request parameter (for example, malformed version value).",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Invalid version parameter",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T10:10:00",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "message": "Failed to convert value of type 'java.lang.String' to required type 'java.lang.Integer'",
                                      "path": "/api/v1/pricing-components/code/MONTHLY_MAIN_FEE"
                                    }
                                    """)))
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to read pricing components.")
    @ApiResponse(responseCode = "404", description = "No pricing component found for the supplied code/version in the current tenant.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Component code not found",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T10:11:00",
                                      "status": 404,
                                      "error": "Not Found",
                                      "message": "Pricing Component not found with code: MONTHLY_MAIN_FEE and version: 99",
                                      "path": "/api/v1/pricing-components/code/MONTHLY_MAIN_FEE"
                                    }
                                    """)))
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('pricing:component:read')")
    public ResponseEntity<PricingComponentResponse> getPricingComponentByCode(
            @Parameter(description = "Business code of the pricing component. Input is normalized to internal code format.", required = true, example = "MONTHLY_MAIN_FEE")
            @PathVariable String code,
            @Parameter(description = "Optional component version. If omitted, the latest version for this code is returned.", example = "2")
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(pricingComponentMapper.toResponseDto(
                pricingComponentService.getPricingComponentByCode(code, version)));
    }

    @Operation(summary = "Create pricing component (aggregate)",
            description = "Creates a new pricing component in DRAFT status. You may provide a full list of Tiers and Price Values in the initial request to create the aggregate at once.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Pricing component aggregate to create. `type` must be one of FEE, INTEREST_RATE, WAIVER, BENEFIT, DISCOUNT, PACKAGE_FEE, or TAX. " +
                    "Higher tier `priority` values are evaluated first; if omitted, the tier is assigned the lowest priority. Tiers with the same priority are treated at the same salience level. " +
                    "For FEE / INTEREST_RATE / PACKAGE_FEE / TAX components use fee value types (`FEE_ABSOLUTE`, `FEE_PERCENTAGE`). " +
                    "For WAIVER / BENEFIT / DISCOUNT components use discount value types (`DISCOUNT_PERCENTAGE`, `DISCOUNT_ABSOLUTE`, `FREE_COUNT`).",
            content = @Content(
                    schema = @Schema(implementation = PricingComponentRequest.class),
                    examples = @ExampleObject(
                            name = "Tiered fee component",
                            value = """
                                    {
                                      "code": "MONTHLY_MAIN_FEE",
                                      "name": "Monthly maintenance fee",
                                      "type": "FEE",
                                      "description": "Standard monthly fee with segment-based tiers",
                                      "proRataApplicable": false,
                                      "pricingTiers": [
                                        {
                                          "name": "Premium Segment",
                                          "code": "PREMIUM_SEGMENT",
                                          "priority": 10,
                                          "minThreshold": 0,
                                          "maxThreshold": 100000,
                                          "applyChargeOnFullBreach": false,
                                          "conditions": [
                                            {
                                              "attributeName": "customerSegment",
                                              "operator": "EQ",
                                              "attributeValue": "PREMIUM",
                                              "connector": "AND"
                                            }
                                          ],
                                          "priceValue": {
                                            "priceAmount": 5.00,
                                            "valueType": "FEE_ABSOLUTE"
                                          }
                                        },
                                        {
                                          "name": "Fallback Tier",
                                          "code": "FALLBACK_TIER",
                                          "applyChargeOnFullBreach": false,
                                          "conditions": [
                                            {
                                              "attributeName": "customerSegment",
                                              "operator": "EQ",
                                              "attributeValue": "RETAIL"
                                            }
                                          ],
                                          "priceValue": {
                                            "priceAmount": 15.00,
                                            "valueType": "FEE_ABSOLUTE"
                                          }
                                        }
                                      ]
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(responseCode = "201", description = "Pricing Component successfully created.",
            content = @Content(schema = @Schema(implementation = PricingComponentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error or name conflict.")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to create pricing.")
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:component:create')")
    public ResponseEntity<PricingComponentResponse> createComponent(@Valid @RequestBody PricingComponentRequest requestDto) {
        return new ResponseEntity<>(pricingComponentService.createComponent(requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Partial update of a DRAFT component (Aggregate Sync)",
            description = "Updates an existing DRAFT. Send only the fields to change. " +
                    "Updating the 'pricingTiers' list will synchronize all nested tiers, conditions, and prices. " +
                    "Only allowed while status is DRAFT.")
    @ApiResponse(responseCode = "200", description = "Pricing component aggregate successfully updated.")
    @ApiResponse(responseCode = "400", description = "Validation error or invalid tier logic.")
    @ApiResponse(responseCode = "403", description = "Modification blocked: Component is not in DRAFT status.")
    @ApiResponse(responseCode = "404", description = "Component not found.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:component:update')")
    public ResponseEntity<PricingComponentResponse> patchPricingComponent(
            @Parameter(description = "ID of the pricing component to update", required = true)
            @PathVariable Long id,
            @RequestBody PricingComponentRequest requestDto) {
        return ResponseEntity.ok(pricingComponentService.updateComponent(id, requestDto));
    }

    @Operation(summary = "Create a new version of an existing pricing component",
            description = "Creates a new DRAFT version from an existing pricing component. This allows evolving pricing (e.g., updating rates) without affecting products linked to historical versions.")
    @ApiResponse(responseCode = "201", description = "New pricing version successfully created.")
    @ApiResponse(responseCode = "404", description = "Source component not found.")
    @PostMapping("/{id}/create-new-version")
    @PreAuthorize("hasAuthority('pricing:component:create')")
    public ResponseEntity<PricingComponentResponse> versionComponent(
            @Parameter(description = "ID of the component to serve as a template", required = true)
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest requestDto) {
        return new ResponseEntity<>(pricingComponentService.versionComponent(id, requestDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Activate a pricing component",
            description = "Transitions a DRAFT to ACTIVE. Once active, the pricing component can be linked to products and becomes immutable for direct updates.")
    @ApiResponse(responseCode = "200", description = "Component successfully activated.")
    @ApiResponse(responseCode = "400", description = "Activation failed (e.g., status is already ACTIVE).")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('pricing:component:activate')")
    public ResponseEntity<PricingComponentResponse> activateComponent(@PathVariable Long id) {
        return ResponseEntity.ok(pricingComponentService.activateComponent(id));
    }

    @Operation(summary = "Delete a pricing component (Dependency Checked)",
            description = "Deletes a pricing component. Fails with 409 Conflict if linked to products or if it contains active tiers.")
    @ApiResponse(responseCode = "204", description = "Component successfully deleted.")
    @ApiResponse(responseCode = "403", description = "Forbidden: Tenant mismatch.")
    @ApiResponse(responseCode = "404", description = "Not Found: Pricing component ID does not exist.")
    @ApiResponse(responseCode = "409", description = "Conflict: Component is in use by products or tiers.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:component:delete')")
    public void deletePricingComponent(
            @Parameter(description = "ID of the pricing component to delete", required = true)
            @PathVariable Long id) {
        pricingComponentService.deletePricingComponent(id);
    }
}
