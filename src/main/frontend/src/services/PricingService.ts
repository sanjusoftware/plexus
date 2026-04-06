import axios from 'axios';

export interface ProductPriceRequest {
  productId: number;
  productBundleId?: number;
  transactionAmount?: number;
  effectiveDate?: string;
  enrollmentDate?: string;
  customerSegment: string;
  customAttributes?: Record<string, any>;
}

export interface PriceComponentDetail {
  componentCode: string;
  targetComponentCode?: string;
  rawValue: number;
  valueType: 'FEE_ABSOLUTE' | 'FEE_PERCENTAGE' | 'RATE_ABSOLUTE' | 'DISCOUNT_ABSOLUTE' | 'DISCOUNT_PERCENTAGE';
  proRataApplicable: boolean;
  applyChargeOnFullBreach: boolean;
  calculatedAmount: number;
  sourceType: 'FIXED_VALUE' | 'RULES_ENGINE';
  matchedTierCode?: string;
  matchedTierId?: number;
}

export interface ProductPricingCalculationResult {
  finalChargeablePrice: number;
  componentBreakdown: PriceComponentDetail[];
}

export interface BundlePriceRequest {
  productBundleId: number;
  transactionAmount?: number;
  effectiveDate?: string;
  enrollmentDate?: string;
  customerSegment: string;
  customAttributes?: Record<string, any>;
}

export interface BundlePriceResponse {
  totalPrice: number;
  productsBreakdown: ProductPricingCalculationResult[];
}

class PricingServiceClass {
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

