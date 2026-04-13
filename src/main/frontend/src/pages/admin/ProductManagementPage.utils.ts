import { PriceComponentDetail } from '../../services/PricingService';

const hasValidProRataWindow = (item: Pick<PriceComponentDetail, 'activeDays' | 'billingCycleDays'>): boolean => {
  return item.activeDays != null
    && item.billingCycleDays != null
    && item.activeDays >= 0
    && item.billingCycleDays > 0
    && item.activeDays <= item.billingCycleDays;
};

export const hasPartialProRata = (item: Pick<PriceComponentDetail, 'activeDays' | 'billingCycleDays'>): boolean => {
  return hasValidProRataWindow(item) && item.activeDays! < item.billingCycleDays!;
};

export const formatProRataHint = (item: Pick<PriceComponentDetail, 'activeDays' | 'billingCycleDays'>): string | null => {
  if (!hasPartialProRata(item)) {
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

export const getSimulationFieldHelperText = (attributeKey: string): string | null => {
  const normalized = (attributeKey || '').toUpperCase();

  if (normalized === 'EFFECTIVE_DATE') {
    return 'Billing-cycle date used to select the pricing month and active pricing configuration. Example: 2026-05-01 runs the May billing cycle.';
  }

  if (normalized === 'ENROLLMENT_DATE') {
    return 'Customer join date used for pro-rata within the selected billing cycle. Billing starts from the later of enrollment date and pricing start date.';
  }

  return null;
};

export const getSimulationDateGuidance = (): Array<{ label: string; description: string }> => ([
  {
    label: 'Effective Date',
    description: getSimulationFieldHelperText('EFFECTIVE_DATE') || '',
  },
  {
    label: 'Enrollment Date',
    description: getSimulationFieldHelperText('ENROLLMENT_DATE') || '',
  },
]);

interface PercentageBaseHintOptions {
  isDiscount: boolean;
  formatCurrency: (amount: number) => string;
  targetComponentLabel?: string | null;
}

export const formatPercentageBaseHint = (
  item: Pick<PriceComponentDetail, 'valueType' | 'rawValue' | 'calculatedAmount' | 'targetComponentCode' | 'activeDays' | 'billingCycleDays'>,
  options: PercentageBaseHintOptions,
): string | null => {
  if (!item.valueType?.includes('PERCENTAGE')) return null;

  const raw = Math.abs(Number(item.rawValue ?? 0));
  const calculated = Math.abs(Number(item.calculatedAmount ?? 0));
  if (!raw || !calculated) return null;

  const inferredBase = (calculated * 100) / raw;
  const isPartial = hasPartialProRata(item);

  if (!options.isDiscount) {
    return `${raw}% of transaction amount (${options.formatCurrency(inferredBase)})`;
  }

  if (item.targetComponentCode) {
    const targetLabel = options.targetComponentLabel || item.targetComponentCode;
    const poolLabel = isPartial ? `prorated ${targetLabel} charge pool` : `${targetLabel} charge pool`;
    return `${raw}% of ${poolLabel} (${options.formatCurrency(inferredBase)})`;
  }

  const poolLabel = isPartial ? 'prorated total charge pool' : 'total charge pool';
  return `${raw}% of ${poolLabel} (${options.formatCurrency(inferredBase)})`;
};

