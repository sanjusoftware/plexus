# Plexus
Configurable Banking Product & Pricing Engine. A flexible microservice built on Spring Boot for defining, managing, and calculating complex global banking products, features, and pricing rules.

Plexus is a scalable banking platform built with Java and Spring Boot. It implements a component-based architecture to provide complete flexibility for product managers.

## Key Capabilities:
1.  **Product Catalog Service (PCS):** Define any banking product using flexible, reusable feature components (e.g., tenure, collateral type, network).
2.  **Pricing Engine Service (PES):** Manage tiered pricing, fees, and interest rates, accessible via a dedicated calculation endpoint.
3.  **Role-Based Access Control (RBAC):** Implemented using **JWTs** and a custom **Resource Server** architecture, enabling **granular permission checks** on all API endpoints.
4.  **Rule Management Service (RMS) Integration:** Supports integration with external rules engines for dynamic calculation of discounts, waivers, and eligibility.
5.  **Multi-Bank & Multi-Currency Support:** Designed for global deployment.

---

# Product Catalog and Pricing Model
This system employs a decoupled, highly reusable architecture where Products are assembled by linking to reusable Components. This design prevents data duplication and centralizes maintenance for both feature configurations and complex pricing rules.

***

## 1. Core Concept: Decoupling Definitions from Products
The key to understanding the model is the distinction between a Component (the reusable master definition) and a Link (the product-specific value or context).

| Domain       | Master Component (Definition) | Link Entity (Product-Specific Usage) | Purpose of the Link                                                                                    |
|:-------------|:------------------------------|:-------------------------------------|:-------------------------------------------------------------------------------------------------------|
| **Features** | **`FeatureComponent`**        | **`ProductFeatureLink`**             | Holds the specific **VALUE** (e.g., '120') for a feature on a Product.                                 |
| **Pricing**  | **`PricingComponent`**        | **`ProductPricingLink`**             | Establishes the **BINDING** and **CONTEXT** (e.g., 'CORE\_RATE') for a pricing component on a Product. |

***

## 2. Entity Relationship Overview
The core entities and their roles in defining a complete product offering:

| Entity Name              | Location        | Role in the Model                                                       | Key Data Stored                                             |
|:-------------------------|:----------------|:------------------------------------------------------------------------|:------------------------------------------------------------|
| **`Product`**            | Catalog Service | The primary, effective-dated product entity (e.g., "Premier Checking"). | `name`, `bankId`, `effectiveDate`, `status`                 |
| **`ProductType`**        | Catalog Service | High-level category for a Product (e.g., `CASA`, `Loan`).               | `name`                                                      |
| **`FeatureComponent`**   | Catalog Service | The global **definition** of a product attribute.                       | `name` (e.g., "Max\_Tenor"), `dataType`                     |
| **`ProductFeatureLink`** | Catalog Service | **M:N link** between a `Product` and a `FeatureComponent`.              | **`featureValue`** (The concrete value, e.g., `"120"`)      |
| **`PricingComponent`**   | Pricing Service | The global **definition** of a reusable rate or fee structure.          | `name` (e.g., "Monthly Fee"), `type` (e.g., `FEE`, `RATE`)  |
| **`PricingTier`**        | Pricing Service | A specific rule or segment **within** a `PricingComponent`.             | `tierName`, `minThreshold`, `conditionValue`                |
| **`PriceValue`**         | Pricing Service | The actual monetary/rate value associated with a `PricingTier`.         | `priceAmount`, `valueType` (e.g., `ABSOLUTE`, `PERCENTAGE`) |
| **`ProductPricingLink`** | Pricing Service | **M:N link** between a `Product` and a `PricingComponent`.              | **`context`** (The purpose, e.g., `"CORE_FEE"`)             |
| **`Role`**               | Auth Service    | Defines a set of permissions (authorities).                             | `name`, linked `authorities` (permissions)                  |

***

## 3. Security and Authorization Model (RBAC)

Plexus enforces security via an internally managed Role-Based Access Control (RBAC) layer integrated with Spring Security's OAuth2 Resource Server.

### A. JWT Structure and Authority Extraction
* **JWT Validation:** Tokens are validated against configurable claims (`iss`, `aud`, `exp`).
* **Custom Authority Converter (`JwtAuthConverter`):** This component reads the custom **`roles`** claim (which supports an array of roles) from the JWT.
* **Permission Mapping:** It uses the `PermissionMappingService` to fetch the complete set of unique, aggregated **Authorities** (`<domain>:<resource>:<action>`, e.g., `catalog:product:read`) from the internal database based on the roles present in the token.

### B. Access Control
Access is granted using method-level security with **`@PreAuthorize`**.

* **Example:** `@PreAuthorize("hasAuthority('catalog:product:read')")`
* **Role Mapping Endpoint:** The `/api/v1/roles/mapping` endpoint is used to maintain the relationship between role names (e.g., `PRICING_ENGINEER`) and the actual system authorities. **Validation** is enforced to prevent mapping invalid permissions.

### C. System Permissions Discovery
To ensure consistency and performance, the application discovers all available authorities by scanning **`@PreAuthorize`** annotations via reflection during startup. This master list is then **cached** in memory using Spring's `@Cacheable` to optimize subsequent authorization lookups and validation checks, avoiding repeated reflection overhead.

***

## 4. Real-World Example: "Premier Checking Account"
Let's trace how the **"Premier Checking Account"** is fully configured using this decoupled model.

### A. Step 1: Defining the Reusable Components (Masters)
These entities are generic and defined once in their respective services:

| Entity                 | ID      | Name                    | Role/Type                     | Notes                                     |
|:-----------------------|:--------|:------------------------|:------------------------------|:------------------------------------------|
| **`FeatureComponent`** | 101     | `MaxFreeATMWithdrawals` | `INTEGER`                     | Defines the attribute: up to what number? |
| **`PricingComponent`** | 201     | `MonthlyServiceFee`     | `FEE`                         | Defines the concept of a monthly fee.     |
| **`PricingTier`**      | 201-1   | `Standard`              | Condition: `SEGMENT=STANDARD` | Rule for standard customers.              |
| **`PriceValue`**       | 201-1-V | `10.00`                 | `ABSOLUTE`, `USD`             | The actual price for the Standard rule.   |
| **`PricingTier`**      | 201-2   | `Premium`               | Condition: `SEGMENT=PREMIUM`  | Rule for premium customers.               |
| **`PriceValue`**       | 201-2-V | `0.00`                  | `WAIVED`                      | The price for the Premium rule (waived).  |

### B. Step 2: Configuring the Product (Linking)
The `Product` entity (ID 50) is created and then **linked** to the reusable components to create its unique definition:

| Entity                   | Product ID | Component ID                  | Key Value        | Description                                                                |
|:-------------------------|:-----------|:------------------------------|:-----------------|:---------------------------------------------------------------------------|
| **`ProductFeatureLink`** | 50         | 101 (`MaxFreeATMWithdrawals`) | **`"5"`**        | The Premier Account sets the value of the feature to **5**.                |
| **`ProductPricingLink`** | 50         | 201 (`MonthlyServiceFee`)     | **`"CORE_FEE"`** | The Product links to the Monthly Fee structure, calling it the `CORE_FEE`. |

***

## 5. The Calculation and Retrieval Flow
The goal is for a consuming application (e.g., a customer onboarding system) to retrieve the correct feature value or calculate the correct price without knowing the internal structure of the components.

### Feature Retrieval
* **Client Request**: Retrieve `MaxFreeATMWithdrawals` for Product ID `50`.
* **Action**: The Catalog Service reads `Product` 50, traverses the `ProductFeatureLink` pointing to `FeatureComponent` 101, and returns the stored `featureValue`.
* **Result**: `"5"` (The service is responsible for validating and casting this String value to an `INTEGER` based on the `FeatureComponent.dataType`).

### Pricing Calculation
* **Client Request**: What is the `MonthlyServiceFee` for Product ID `50` for a **STANDARD** customer?
* **Step 1 (Catalog)**: The client system (or a proxy) looks up `Product` 50 and finds the `ProductPricingLink` with `context="CORE_FEE"`. This link points to `PricingComponent` **201** (`MonthlyServiceFee`).
* **Step 2 (Pricing)**: The client calls the dedicated Pricing Calculation Endpoint with the required parameters: `GET /api/v1/pricing/calculate/{componentId}?segment=STANDARD&amount=0`
* **Action (Rule Engine)**: The Pricing Service loads `PricingComponent` **201** and all its associated `PricingTier` rules. It executes the rules against the inputs (`segment=STANDARD`).
    * Rule 201-1 fires (`conditionValue` matches `STANDARD`).
* **Result**: The Price Service returns the associated `PriceValue`: `$10.00` (with `valueType`: `ABSOLUTE`).

### Benefit of this Architecture
* **Centralized Pricing Updates**: If the rule for the `STANDARD` segment changes globally from $10.00 to $12.00, only **one** `PriceValue` needs to be updated. All products linked to `PricingComponent` 201 instantly inherit the change.
* **Product-Specific Overrides**: Product features are isolated. If a product needs a limit of **10** free withdrawals instead of 5, only its specific `ProductFeatureLink` needs updating, leaving the `FeatureComponent` master definition intact.