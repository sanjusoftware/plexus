import axios from 'axios';

export interface ProductPriceRequest {
  productId: number;
  enrollmentDate?: string;
  customAttributes?: Record<string, any>;
}

export interface PricingMetadata {
  id?: number;
  attributeKey: string;
  displayName?: string;
  dataType: string;
  sourceType?: string;
  sourceField?: string;
}

export interface PriceComponentDetail {
  componentCode: string;
  targetComponentCode?: string;
  rawValue: number;
  valueType: 'FEE_ABSOLUTE' | 'FEE_PERCENTAGE' | 'RATE_ABSOLUTE' | 'DISCOUNT_ABSOLUTE' | 'DISCOUNT_PERCENTAGE' | 'FREE_COUNT';
  proRataApplicable: boolean;
  applyChargeOnFullBreach: boolean;
  calculatedAmount: number;
  sourceType: 'FIXED_VALUE' | 'RULES_ENGINE' | 'CATALOG';
  matchedTierCode?: string;
  matchedTierId?: number;
  activeDays?: number;
  billingCycleDays?: number;
  effectiveDate?: string | null;
}

export interface ProductPricingCalculationResult {
  finalChargeablePrice: number;
  componentBreakdown: PriceComponentDetail[];
}

export interface BundlePriceRequest {
  productBundleId: number;
  enrollmentDate?: string;
  products: Array<{ productId: number; transactionAmount: number }>;
  customAttributes?: Record<string, any>;
}

export interface BundlePriceResponse {
  totalPrice: number;
  productsBreakdown: ProductPricingCalculationResult[];
}

export interface TierCondition {
  attributeName: string;
  operator: string;
  attributeValue: string;
  connector: string;
}

export interface PricingTier {
  id: number;
  name: string;
  code: string;
  priority: number;
  minThreshold?: number;
  maxThreshold?: number;
  conditions: TierCondition[];
  priceValues: PriceComponentDetail[];
}

export interface PricingComponent {
  id: number;
  name: string;
  code: string;
  version: number;
  status: string;
  type: string;
  description: string;
  proRataApplicable: boolean;
  pricingTiers: PricingTier[];
}

export interface FeatureComponent {
  id: number;
  code: string;
  name: string;
  dataType: string;
  status: string;
  version: number;
  description?: string;
}

class PricingServiceClass {
  private systemKeysCache: string[] | null = null;
  private systemKeysInFlight: Promise<string[]> | null = null;

  private componentCache: Record<string, PricingComponent> = {};
  private featureCache: Record<string, FeatureComponent> = {};

  async getSystemPricingAttributeKeys(signal?: AbortSignal): Promise<string[]> {
    if (this.systemKeysCache) {
      return this.systemKeysCache;
    }
    if (this.systemKeysInFlight) {
      return this.systemKeysInFlight;
    }
    this.systemKeysInFlight = axios.get('/api/v1/pricing-metadata/system-attributes', { signal })
      .then((response) => {
        const keys: string[] = response.data || [];
        this.systemKeysCache = keys;
        return keys;
      })
      .finally(() => {
        this.systemKeysInFlight = null;
      });
    return this.systemKeysInFlight!;
  }

  clearSystemPricingAttributeKeysCache() {
    this.systemKeysCache = null;
    this.systemKeysInFlight = null;
  }

  async getPricingMetadata(signal?: AbortSignal): Promise<PricingMetadata[]> {
    try {
      const response = await axios.get('/api/v1/pricing-metadata', { signal });
      return response.data || [];
    } catch (error) {
      if (axios.isCancel(error)) throw error;
      console.error('Failed to fetch pricing metadata:', error);
      throw error;
    }
  }

  /**
   * Calculate pricing for a single product
   */
  async calculateProductPrice(request: ProductPriceRequest, signal?: AbortSignal): Promise<ProductPricingCalculationResult> {
    try {
      const response = await axios.post('/api/v1/pricing/calculate/product', request, { signal });
      return response.data;
    } catch (error) {
      if (axios.isCancel(error)) throw error;
      console.error('Product pricing calculation failed:', error);
      throw error;
    }
  }

  /**
   * Calculate pricing for a product bundle
   */
  async calculateBundlePrice(request: BundlePriceRequest, signal?: AbortSignal): Promise<BundlePriceResponse> {
    try {
      const response = await axios.post('/api/v1/pricing/calculate/bundle', request, { signal });
      return response.data;
    } catch (error) {
      if (axios.isCancel(error)) throw error;
      console.error('Bundle pricing calculation failed:', error);
      throw error;
    }
  }

  async getPricingComponentByCode(code: string, version?: number, signal?: AbortSignal): Promise<PricingComponent> {
    const cacheKey = `${code}${version ? `_v${version}` : ''}`;
    if (this.componentCache[cacheKey]) {
      return this.componentCache[cacheKey];
    }

    try {
      const response = await axios.get(`/api/v1/pricing-components/code/${code}`, {
        params: { version },
        signal
      });
      const data = response.data;
      this.componentCache[cacheKey] = data;
      return data;
    } catch (error) {
      if (axios.isCancel(error)) throw error;
      console.error(`Failed to fetch pricing component ${code}:`, error);
      throw error;
    }
  }

  async getFeatureComponentByCode(code: string, version?: number, signal?: AbortSignal): Promise<FeatureComponent> {
    const cacheKey = `${code}${version ? `_v${version}` : ''}`;
    if (this.featureCache[cacheKey]) {
      return this.featureCache[cacheKey];
    }

    try {
      const response = await axios.get(`/api/v1/features/code/${code}`, {
        params: { version },
        signal
      });
      // The backend returns a List<FeatureComponentResponse> for /code/{code}
      const data = Array.isArray(response.data) ? response.data[0] : response.data;
      if (!data) {
        throw new Error(`Feature component ${code} not found`);
      }
      this.featureCache[cacheKey] = data;
      return data;
    } catch (error) {
      if (axios.isCancel(error)) throw error;
      console.error(`Failed to fetch feature component ${code}:`, error);
      throw error;
    }
  }

  clearComponentCache() {
    this.componentCache = {};
    this.featureCache = {};
  }

  /**
   * Format currency values consistently
   */
  formatCurrency(amount: number, locale: string = 'en-US', currency: string = 'USD'): string {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currency,
    }).format(amount);
  }

  /**
   * Format percentage values
   */
  formatPercentage(value: number, decimals: number = 2): string {
    return `${value.toFixed(decimals)}%`;
  }

  /**
   * Determine if a value type is a fee
   */
  isFeeType(valueType: string): boolean {
    return valueType.startsWith('FEE_') || valueType === 'RATE_ABSOLUTE';
  }

  /**
   * Determine if a value type is a discount
   */
  isDiscountType(valueType: string): boolean {
    return valueType.startsWith('DISCOUNT_');
  }

  /**
   * Determine if a value type is percentage-based
   */
  isPercentageType(valueType: string): boolean {
    return valueType.includes('PERCENTAGE');
  }

  /**
   * Get human-readable label for value type
   */
  getValueTypeLabel(valueType: string): string {
    const labels: Record<string, string> = {
      FEE_ABSOLUTE: 'Absolute Fee',
      FEE_PERCENTAGE: 'Percentage Fee',
      RATE_ABSOLUTE: 'Absolute Rate',
      DISCOUNT_ABSOLUTE: 'Absolute Discount',
      DISCOUNT_PERCENTAGE: 'Percentage Discount',
      FREE_COUNT: 'Free Count'
    };
    return labels[valueType] || valueType;
  }

  /**
   * Get color class for value type
   */
  getValueTypeColor(valueType: string): string {
    if (this.isFeeType(valueType)) {
      return 'text-red-600 bg-red-50';
    }
    if (this.isDiscountType(valueType)) {
      return 'text-green-600 bg-green-50';
    }
    return 'text-gray-600 bg-gray-50';
  }

  /**
   * Calculate pro-rata impact
   */
  calculateProRataImpact(enrollmentDate: Date, effectiveDate: Date = new Date()): number {
    // Only apply pro-rata if enrollment is in the same month
    if (enrollmentDate.getMonth() !== effectiveDate.getMonth() ||
        enrollmentDate.getFullYear() !== effectiveDate.getFullYear()) {
      return 1.0; // 100% - full month
    }

    const daysInMonth = new Date(enrollmentDate.getFullYear(), enrollmentDate.getMonth() + 1, 0).getDate();
    const activeDays = daysInMonth - enrollmentDate.getDate() + 1;
    return activeDays / daysInMonth;
  }
}

// Export singleton instance
export const PricingService = new PricingServiceClass();
