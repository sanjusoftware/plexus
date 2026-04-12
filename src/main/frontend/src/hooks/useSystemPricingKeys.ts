import { useCallback, useMemo } from 'react';
import { useAuth } from '../context/AuthContext';

export const SYSTEM_PRICING_KEYS = {
  CUSTOMER_SEGMENT: 'CUSTOMER_SEGMENT',
  TRANSACTION_AMOUNT: 'TRANSACTION_AMOUNT',
  EFFECTIVE_DATE: 'EFFECTIVE_DATE',
  PRODUCT_ID: 'PRODUCT_ID',
  PRODUCT_BUNDLE_ID: 'PRODUCT_BUNDLE_ID',
  GROSS_TOTAL_AMOUNT: 'GROSS_TOTAL_AMOUNT',
  BANK_ID: 'BANK_ID',
} as const;

const toUpper = (key: string) => (key || '').toUpperCase();

export const useSystemPricingKeys = () => {
  const { systemPricingKeys } = useAuth();

  const keySet = useMemo(
    () => new Set((systemPricingKeys || []).map((key) => key.toUpperCase())),
    [systemPricingKeys]
  );

  const hasKey = useCallback((key: string) => keySet.has(toUpper(key)), [keySet]);

  const isSystemAmountKey = useCallback((key: string) => toUpper(key) === SYSTEM_PRICING_KEYS.TRANSACTION_AMOUNT, []);
  const isSystemDateKey = useCallback((key: string) => toUpper(key) === SYSTEM_PRICING_KEYS.EFFECTIVE_DATE, []);
  const isSystemCustomerSegmentKey = useCallback((key: string) => toUpper(key) === SYSTEM_PRICING_KEYS.CUSTOMER_SEGMENT, []);
  const isHiddenSystemKey = useCallback((key: string) => {
    const normalized = toUpper(key);
    return normalized === SYSTEM_PRICING_KEYS.BANK_ID
      || normalized === SYSTEM_PRICING_KEYS.PRODUCT_ID
      || normalized === SYSTEM_PRICING_KEYS.PRODUCT_BUNDLE_ID;
  }, []);

  return {
    systemPricingKeys,
    systemPricingKeySet: keySet,
    keys: SYSTEM_PRICING_KEYS,
    hasSystemPricingKey: hasKey,
    isSystemAmountKey,
    isSystemDateKey,
    isSystemCustomerSegmentKey,
    isHiddenSystemKey,
  };
};

