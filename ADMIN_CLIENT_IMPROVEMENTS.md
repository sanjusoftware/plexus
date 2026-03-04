# Proposed Improvements for Thin Admin Client

To facilitate the development of a highly efficient and user-friendly thin client for system and bank administrators, the following architectural and operational enhancements are proposed for the Plexus platform.

## 1. Architectural Enhancements: Metadata-Driven UI

### Dynamic Form Discovery
- **Endpoint**: Implement `GET /api/v1/metadata/schema/{entity}` (e.g., `product`, `pricing-component`).
- **Purpose**: Return a JSON schema or a custom metadata object that describes the fields, their data types, validation rules (regex, min/max), and UI hints (e.g., `widget: "date-picker"`, `options: ["RETAIL", "WEALTH"]`).
- **Benefit**: Allows the thin client to generate forms dynamically, ensuring that UI updates are automatically in sync with backend model changes without requiring client-side code deployments.

### Feature and Pricing Component Lookup with Search
- **Improvement**: Enhance feature and pricing component endpoints with robust filtering and search capabilities.
- **Benefit**: Enables administrators to easily find and link existing components within the FAT DTO creation flow, supporting "type-ahead" search in the UI.

## 2. Operational Efficiency: Bulk and Batch Operations

### Bulk Status Transitions
- **Endpoint**: `POST /api/v1/products/bulk-activate`, `POST /api/v1/products/bulk-archive`.
- **Purpose**: Allow administrators to select multiple products or bundles and perform lifecycle transitions in a single request.
- **Benefit**: Reduces operational overhead when managing large catalogs, especially during seasonal product launches.

### Template-Based Creation
- **Feature**: Provide a "Clone as Template" capability.
- **Benefit**: Administrators can create standard product templates (e.g., "Standard Savings Template") and create new products by simply overriding specific values, further leveraging the FAT DTO pattern.

## 3. Improved Validation and Error Handling

### Granular Business Rule Feedback
- **Improvement**: Instead of generic `400 Bad Request`, return a structured list of business rule violations.
- **Format**:
  ```json
  {
    "code": "BUSINESS_RULE_VIOLATION",
    "message": "Product creation failed due to multiple conflicts.",
    "errors": [
      { "field": "pricing[0].fixedValue", "reason": "Value exceeds the maximum allowed for this component type.", "severity": "ERROR" },
      { "field": "category", "reason": "Category 'RETAIL' is approaching its quota for this bank.", "severity": "WARNING" }
    ]
  }
  ```
- **Benefit**: Enables the thin client to highlight specific fields in the UI with meaningful error messages, significantly improving the user experience.

### Pre-flight Validation
- **Endpoint**: `POST /api/v1/products/validate`.
- **Purpose**: A "dry-run" endpoint that performs all validations (tenancy, schema, business rules) without persisting the data.
- **Benefit**: Allows the UI to provide real-time validation feedback to the user before they submit a large aggregate DTO.

## 4. Multi-Tenant Administration Improvements

### Impersonation Mode for System Admins
- **Feature**: Allow `SYSTEM_ADMIN` to view (read-only) a bank's configuration with an `X-Impersonate-Bank-Id` header, subject to strict auditing.
- **Benefit**: Greatly assists in support and troubleshooting for bank administrators without compromising the "shared-schema, row-level" security model for write operations.
