# Plexus Frontend - Banking Product Management System

A modern React TypeScript application for managing bank product catalogs, features, pricing configurations, and customer onboarding. Built with responsive Tailwind CSS styling and real-time pricing calculations.

## 📋 Table of Contents

- [Project Overview](#project-overview)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Key Features](#key-features)
- [Component Architecture](#component-architecture)
- [API Integration](#api-integration)
- [Development Workflow](#development-workflow)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

---

## Project Overview

This frontend application provides a comprehensive UI for managing banking products with:
- **Product Management**: Create, edit, activate, and lifecycle manage products
- **Feature Components**: Link reusable feature definitions to products
- **Pricing Configuration**: Set up static pricing or dynamic rules-based pricing tiers
- **Price Calculation**: Real-time pricing preview and simulation tools
- **Role-Based Access Control**: Permission-based UI elements and API interactions
- **Multi-Tenant Support**: Bank-scoped operations with secure context isolation

### Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Framework** | React | 19.2.4 |
| **Language** | TypeScript | 4.9.5 |
| **Routing** | React Router | 7.13.2 |
| **HTTP Client** | Axios | 1.13.6 |
| **Styling** | Tailwind CSS | 3.4.19 |
| **Icons** | Lucide React | 0.577.0 |
| **Auth** | OIDC Client TS | 3.4.1 |

---

## Quick Start

### Prerequisites

- **Node.js** 16+ and npm 8+
- **Backend API** running on http://localhost:8080
- **OIDC Provider** credentials for authentication

### Installation

```bash
cd src/main/frontend
npm install
```

### Environment Variables

Create a `.env` file with:

```env
REACT_APP_API_BASE_URL=http://localhost:8080
REACT_APP_AUTH_AUTHORITY=https://your-auth-provider
REACT_APP_AUTH_CLIENT_ID=your-client-id
```

### Development

```bash
npm start       # Start dev server (http://localhost:3000)
npm test        # Run tests
npm run build   # Production build
```

---

## Project Structure

```
src/
├── components/
│   ├── LivePricePreview.tsx         # Real-time pricing preview
│   ├── PriceSimulationTool.tsx      # Multi-scenario pricing tester
│   ├── PricingTierVisualization.tsx # Tier structure visualization
│   ├── PlexusSelect.tsx             # Custom select component
│   └── ... (other components)
├── pages/
│   ├── admin/
│   │   ├── ProductFormPage.tsx      # Product creation/editing
│   │   ├── ProductManagementPage.tsx
│   │   └── ... (other admin pages)
│   └── ... (public pages)
├── services/
│   └── PricingService.ts            # Pricing API wrapper
├── context/
│   ├── AuthContext.tsx
│   └── BreadcrumbContext.tsx
└── hooks/
    └── ... (custom hooks)
```

---

## Key Features

### 🎯 Live Price Preview
Real-time pricing calculation during product creation
- Adjustable transaction amount and customer segment
- Component breakdown display
- Pro-rata calculations

### 🧪 Price Simulation Tool
Multi-scenario pricing tester with:
- Create multiple test scenarios
- Component breakdown table
- Export results as JSON

### 📊 Pricing Tier Visualization
Visual display of tier structure:
- Expandable tier details
- Condition matching logic
- Rules engine flow diagram

### 📦 Product Management
Complete lifecycle management:
- Create products with features and pricing
- Inline feature component creation
- Static and dynamic pricing binding
- Activation and archival workflows

### 🔐 Role-Based Access Control
Permission-based UI rendering via `HasPermission` component

---

## Component Architecture

### New Components

#### **LivePricePreview**
```typescript
<LivePricePreview
  productId={123}
  currentFormData={formData}
/>
```
- Real-time price calculation
- Input controls for testing
- Component breakdown display

#### **PriceSimulationTool**
```typescript
<PriceSimulationTool
  isOpen={true}
  onClose={() => {}}
  defaultProductId={123}
/>
```
- Multi-scenario testing
- JSON export
- Scenario comparison

#### **PricingTierVisualization**
```typescript
<PricingTierVisualization
  tiers={pricingTiers}
  componentCode="MAINT_FEE"
  isRulesEngine={true}
/>
```
- Visual tier display
- Condition visualization
- Flow diagrams

---

## API Integration

### PricingService

Centralized pricing API wrapper:

```typescript
import { PricingService } from './services/PricingService';

// Calculate price
const result = await PricingService.calculateProductPrice({
  productId: 123,
  transactionAmount: 1000,
  customerSegment: 'PREMIUM',
  effectiveDate: '2026-04-04'
});

// Format utilities
PricingService.formatCurrency(12.50);      // "$12.50"
PricingService.getValueTypeLabel('FEE_ABSOLUTE');
```

### Backend Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/products` | GET/POST | List/create products |
| `/api/v1/products/{id}` | GET/PATCH | Get/update product |
| `/api/v1/products/{id}/activate` | POST | Activate product |
| `/api/v1/pricing-components` | GET/POST | Manage pricing |
| `/api/v1/pricing/calculate/product` | POST | Calculate price |
| `/api/v1/features` | GET/POST | Manage features |

---

## Development Workflow

### Adding Features

1. Create component: `touch src/components/NewComponent.tsx`
2. Define TypeScript interfaces
3. Implement with React hooks
4. Integrate into page
5. Test with backend API

### Code Style

- **Components**: PascalCase (`ProductFormPage.tsx`)
- **Files**: Consistent naming with feature domain
- **Types**: Interfaces for all props and state
- **Imports**: Group by external, icons, internal

### Git Workflow

```bash
git checkout -b feature/pricing-enhancement
# ... make changes ...
git commit -m "feat: add pricing enhancement"
git push origin feature/pricing-enhancement
```

---

## Deployment

### Build

```bash
npm run build    # Creates src/main/frontend/build/
```

### Docker Example

```dockerfile
FROM node:16-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install && npm run build

FROM nginx:alpine
COPY --from=builder /app/build /usr/share/nginx/html
EXPOSE 80
```

### Environment Configuration

- **Dev**: `REACT_APP_API_BASE_URL=http://localhost:8080`
- **Prod**: `REACT_APP_API_BASE_URL=https://api.example.com`

---

## Troubleshooting

### API Calls Failing
- Check `REACT_APP_API_BASE_URL` in `.env`
- Verify backend is running
- Check network tab in DevTools

### Pricing Calculation Error
- Ensure product is ACTIVE (not DRAFT)
- Verify pricing components are linked and ACTIVE
- Check valid customer segment

### Styling Issues
- Rebuild Tailwind: `npm run build`
- Clear browser cache
- Verify `tailwind.config.js` paths

---

## Resources

- [React Documentation](https://react.dev)
- [TypeScript Handbook](https://www.typescriptlang.org/docs)
- [Tailwind CSS](https://tailwindcss.com)
- [React Router](https://reactrouter.com)
- [Lucide Icons](https://lucide.dev)

---

## License

Proprietary - All rights reserved
