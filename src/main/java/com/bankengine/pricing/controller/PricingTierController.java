package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.PricingTierRequest;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.service.PricingComponentService;
import com.bankengine.web.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Pricing Tier Management", description = "Manages nested tiers and their persisted values for pricing components. Tier retrieval is exposed via parent pricing-component read endpoints.")
@RestController
@RequestMapping("/api/v1/pricing-components/{componentId}/tiers")
public class PricingTierController {

    private final PricingComponentService pricingComponentService;

    public PricingTierController(PricingComponentService pricingComponentService) {
        this.pricingComponentService = pricingComponentService;
    }

    @Operation(summary = "Add a pricing tier to an existing component",
            description = "Creates a new tier beneath the specified pricing component while the component is still in DRAFT status. " +
                    "The request captures thresholds, optional rule conditions, and one persisted value definition for that tier. " +
                    "Higher `priority` values are evaluated first; if omitted, the tier is assigned the lowest priority. Tiers with the same priority are treated at the same salience level. " +
                    "Use fee value types (`FEE_ABSOLUTE`, `FEE_PERCENTAGE`) for fee/rate-style components and discount/free value types for waiver, benefit, or discount components. " +
                    "Use parent read endpoints (`GET /api/v1/pricing-components` or `GET /api/v1/pricing-components/{id}`) to view tiers after creation.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Tier definition to append to the target pricing component. Use fee value types (`FEE_ABSOLUTE`, `FEE_PERCENTAGE`) for fee/rate-style components and discount/free value types for waiver, benefit, or discount components.",
            content = @Content(
                    schema = @Schema(implementation = PricingTierRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "Segment-based fee tier",
                                    value = """
                                            {
                                              "name": "Premium Segment",
                                              "code": "PREMIUM_SEGMENT",
                                              "priority": 10,
                                              "minThreshold": 0,
                                              "maxThreshold": 100000,
                                              "effectiveDate": "2026-03-01",
                                              "expiryDate": "2026-12-31",
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
                                            }
                                            """),
                            @ExampleObject(
                                    name = "Discount tier",
                                    value = """
                                            {
                                              "name": "Salary Waiver",
                                              "code": "SALARY_WAIVER",
                                              "priority": 20,
                                              "minThreshold": 5000,
                                              "applyChargeOnFullBreach": false,
                                              "conditions": [
                                                {
                                                  "attributeName": "salaryCredit",
                                                  "operator": "GTE",
                                                  "attributeValue": "5000"
                                                }
                                              ],
                                              "priceValue": {
                                                "priceAmount": 100,
                                                "valueType": "DISCOUNT_PERCENTAGE"
                                              }
                                            }
                                            """)
                    }
            )
    )
    @ApiResponse(responseCode = "201", description = "Pricing tier and price value successfully created and linked.",
            content = @Content(
                    schema = @Schema(implementation = ProductPricingCalculationResult.PriceComponentDetail.class),
                    examples = @ExampleObject(
                            name = "Created tier value detail",
                            value = """
                                    {
                                      "componentCode": "MONTHLY_MAIN_FEE",
                                      "targetComponentCode": "MONTHLY_MAIN_FEE",
                                      "rawValue": 5.00,
                                      "valueType": "FEE_ABSOLUTE",
                                      "proRataApplicable": false,
                                      "applyChargeOnFullBreach": false,
                                      "calculatedAmount": 5.00,
                                      "sourceType": "CATALOG",
                                      "matchedTierCode": "PREMIUM_SEGMENT",
                                      "matchedTierId": 1001
                                    }
                                    """)))
    @ApiResponse(responseCode = "400", description = "Request validation failed, an enum value was invalid, or the supplied value type does not match the parent component type.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = {
                            @ExampleObject(
                                    name = "Bean validation failure",
                                    value = """
                                            {
                                              "timestamp": "2026-04-04T09:15:30",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "message": "Input validation failed: One or more fields contain invalid data.",
                                              "details": {
                                                "code": "Tier code is required.",
                                                "priceValue": "Price value is required for the tier."
                                              }
                                            }
                                            """),
                            @ExampleObject(
                                    name = "Fee or discount type mismatch",
                                    value = """
                                            {
                                              "timestamp": "2026-04-04T09:16:00",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "message": "Component type FEE must have fee-related value types (FEE_ABSOLUTE, FEE_PERCENTAGE). Found: DISCOUNT_PERCENTAGE"
                                            }
                                            """)
                    }))
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to create pricing tiers.")
    @ApiResponse(responseCode = "404", description = "Pricing component not found for the supplied componentId.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Component not found",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:17:00",
                                      "status": 404,
                                      "error": "Not Found",
                                      "message": "Pricing Component not found with ID: 100"
                                    }
                                    """)))
    @ApiResponse(responseCode = "409", description = "The target component is not in DRAFT status, so nested tier changes are blocked.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Component not draft",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:18:00",
                                      "status": 409,
                                      "error": "Illegal State",
                                      "message": "Operation allowed only on DRAFT status."
                                    }
                                    """)))
    @PostMapping
    @PreAuthorize("hasAuthority('pricing:tier:create')")
    public ResponseEntity<ProductPricingCalculationResult.PriceComponentDetail> addTieredPricing(
            @Parameter(description = "ID of the DRAFT pricing component that will receive the new tier.", required = true, example = "100")
            @PathVariable Long componentId,
            @Valid @RequestBody PricingTierRequest dto) {

        ProductPricingCalculationResult.PriceComponentDetail responseDto = pricingComponentService.addTierAndValue(
                componentId,
                dto,
                dto.getPriceValue());

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @Operation(summary = "Update a nested pricing tier",
            description = "Updates an existing tier beneath a pricing component while the parent component remains in DRAFT status. " +
                    "Use this endpoint to revise thresholds, rule conditions, `applyChargeOnFullBreach`, or the persisted value definition without replacing the full component aggregate. " +
                    "If `conditions` is provided as an empty list, existing conditions are cleared. " +
                    "`priority` follows the same salience behavior as create operations.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Complete tier payload to persist for the specified tier. The endpoint updates the tier core fields, replaces conditions when a list is supplied, and updates the existing stored price value.",
            content = @Content(
                    schema = @Schema(implementation = PricingTierRequest.class),
                    examples = @ExampleObject(
                            name = "Adjusted premium fee tier",
                            value = """
                                    {
                                      "name": "Premium Segment",
                                      "code": "PREMIUM_SEGMENT",
                                      "priority": 15,
                                      "minThreshold": 0,
                                      "maxThreshold": 150000,
                                      "effectiveDate": "2026-04-01",
                                      "expiryDate": "2027-03-31",
                                      "applyChargeOnFullBreach": false,
                                      "conditions": [
                                        {
                                          "attributeName": "customerSegment",
                                          "operator": "EQ",
                                          "attributeValue": "PREMIUM"
                                        }
                                      ],
                                      "priceValue": {
                                        "priceAmount": 4.50,
                                        "valueType": "FEE_ABSOLUTE"
                                      }
                                    }
                                    """))
    )
    @ApiResponse(responseCode = "200", description = "Pricing tier and price value successfully updated.",
            content = @Content(
                    schema = @Schema(implementation = ProductPricingCalculationResult.PriceComponentDetail.class),
                    examples = @ExampleObject(
                            name = "Updated tier value detail",
                            value = """
                                    {
                                      "componentCode": "MONTHLY_MAIN_FEE",
                                      "targetComponentCode": "MONTHLY_MAIN_FEE",
                                      "rawValue": 4.50,
                                      "valueType": "FEE_ABSOLUTE",
                                      "proRataApplicable": false,
                                      "applyChargeOnFullBreach": false,
                                      "calculatedAmount": 4.50,
                                      "sourceType": "CATALOG",
                                      "matchedTierCode": "PREMIUM_SEGMENT",
                                      "matchedTierId": 1001
                                    }
                                    """)))
    @ApiResponse(responseCode = "400", description = "Request validation failed, an enum value was invalid, or the supplied value type does not match the parent component type.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Update validation failure",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:19:30",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "message": "Input validation failed: One or more fields contain invalid data.",
                                      "path": "/api/v1/pricing-components/100/tiers/1001",
                                      "details": {
                                        "name": "Tier name is required.",
                                        "priceValue.valueType": "Value Type is required (e.g., ABSOLUTE, PERCENTAGE)."
                                      }
                                    }
                                    """)))
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to update pricing tiers.")
    @ApiResponse(responseCode = "404", description = "The pricing component or tier was not found, or the tier does not belong to the supplied component.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = {
                            @ExampleObject(
                                    name = "Tier not found",
                                    value = """
                                            {
                                              "timestamp": "2026-04-04T09:20:00",
                                              "status": 404,
                                              "error": "Not Found",
                                              "message": "Tier not found with ID: 1001"
                                            }
                                            """),
                            @ExampleObject(
                                    name = "Tier does not belong to component",
                                    value = """
                                            {
                                              "timestamp": "2026-04-04T09:20:30",
                                              "status": 404,
                                              "error": "Not Found",
                                              "message": "Tier 1001 does not belong to component 100"
                                            }
                                            """)
                    }))
    @ApiResponse(responseCode = "409", description = "The target component is not in DRAFT status, so nested tier changes are blocked.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Update blocked by lifecycle",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:21:00",
                                      "status": 409,
                                      "error": "Illegal State",
                                      "message": "Operation allowed only on DRAFT status.",
                                      "path": "/api/v1/pricing-components/100/tiers/1001"
                                    }
                                    """)))
    @PutMapping("/{tierId}")
    @PreAuthorize("hasAuthority('pricing:tier:update')")
    public ResponseEntity<ProductPricingCalculationResult.PriceComponentDetail> updateTieredPricing(
            @Parameter(description = "ID of the DRAFT pricing component that owns the tier.", required = true, example = "100")
            @PathVariable Long componentId,
            @Parameter(description = "ID of the pricing tier to update.", required = true, example = "1001")
            @PathVariable Long tierId,
            @Valid @RequestBody PricingTierRequest dto) {

        ProductPricingCalculationResult.PriceComponentDetail responseDto = pricingComponentService.updateTierAndValue(
                componentId,
                tierId,
                dto);

        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    @Operation(summary = "Delete a nested pricing tier",
            description = "Deletes a tier and its associated stored value from the specified pricing component. " +
                    "This operation is only permitted while the parent component is in DRAFT status. " +
                    "After deletion, retrieve the component aggregate through parent read endpoints to verify remaining tiers.")
    @ApiResponse(responseCode = "204", description = "Pricing Tier and Value successfully deleted (No Content).")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions to delete pricing tiers.")
    @ApiResponse(responseCode = "404", description = "The pricing component or tier was not found for the supplied path variables.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Tier not found for component",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:22:00",
                                      "status": 404,
                                      "error": "Not Found",
                                      "message": "Tier 1001 not found for component 100"
                                    }
                                    """)))
    @ApiResponse(responseCode = "409", description = "The target component is not in DRAFT status, so nested tier changes are blocked.",
            content = @Content(
                    schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(
                            name = "Delete blocked by lifecycle",
                            value = """
                                    {
                                      "timestamp": "2026-04-04T09:23:00",
                                      "status": 409,
                                      "error": "Illegal State",
                                      "message": "Operation allowed only on DRAFT status.",
                                      "path": "/api/v1/pricing-components/100/tiers/1001"
                                    }
                                    """)))
    @DeleteMapping("/{tierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:tier:delete')")
    public void deleteTieredPricing(
            @Parameter(description = "ID of the DRAFT pricing component that owns the tier.", required = true, example = "100")
            @PathVariable Long componentId,
            @Parameter(description = "ID of the pricing tier to delete.", required = true, example = "1001")
            @PathVariable Long tierId) {

        pricingComponentService.deleteTierAndValue(componentId, tierId);
    }
}