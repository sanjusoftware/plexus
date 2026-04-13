import { PriceComponentDetail } from '../../services/PricingService';

export const formatProRataHint = (item: Pick<PriceComponentDetail, 'activeDays' | 'billingCycleDays'>): string | null => {
  if (item.activeDays == null || item.billingCycleDays == null) {
    return null;
  }

  if (item.activeDays < 0 || item.billingCycleDays <= 0 || item.activeDays > item.billingCycleDays) {
    return null;
  }

  return `Pro-rated for ${item.activeDays} of ${item.billingCycleDays} days`;
};

export const formatComponentLabelWithProRata = (
  label: string,
  item: Pick<PriceComponentDetail, 'activeDays' | 'billingCycleDays'>,
): string => {
  const proRataHint = formatProRataHint(item);
  return proRataHint ? `${label} (${proRataHint})` : label;
};

