import { formatComponentLabelWithProRata, formatPercentageBaseHint, formatProRataHint, getSimulationDateGuidance, getSimulationFieldHelperText } from './ProductManagementPage.utils';

describe('ProductManagementPage receipt helpers', () => {
  test('formats active-day metadata for receipt display', () => {
    expect(formatProRataHint({ activeDays: 18, billingCycleDays: 30 }))
      .toBe('Pro-rated for 18 of 30 days');
  });

  test('formats the component label with an inline pro-rata suffix', () => {
    expect(formatComponentLabelWithProRata('Monthly Fee', { activeDays: 18, billingCycleDays: 30 }))
      .toBe('Monthly Fee (Pro-rated for 18 of 30 days)');
  });

  test('returns null when active-day metadata is missing', () => {
    expect(formatProRataHint({ activeDays: undefined, billingCycleDays: 30 })).toBeNull();
    expect(formatProRataHint({ activeDays: 18, billingCycleDays: undefined })).toBeNull();
  });

  test('suppresses pro-rata text when the full billing cycle is active', () => {
    expect(formatProRataHint({ activeDays: 31, billingCycleDays: 31 })).toBeNull();
    expect(formatComponentLabelWithProRata('Monthly Fee', { activeDays: 31, billingCycleDays: 31 }))
      .toBe('Monthly Fee');
  });

  test('returns null for invalid active-day values', () => {
    expect(formatProRataHint({ activeDays: -1, billingCycleDays: 30 })).toBeNull();
    expect(formatProRataHint({ activeDays: 31, billingCycleDays: 30 })).toBeNull();
    expect(formatProRataHint({ activeDays: 10, billingCycleDays: 0 })).toBeNull();
  });

  test('leaves the component label unchanged when there is no valid pro-rata metadata', () => {
    expect(formatComponentLabelWithProRata('Monthly Fee', { activeDays: undefined, billingCycleDays: 30 }))
      .toBe('Monthly Fee');
  });

  test('returns helper text for effective date and enrollment date fields', () => {
    expect(getSimulationFieldHelperText('EFFECTIVE_DATE'))
      .toBe('Billing-cycle date used to select the pricing month and active pricing configuration. Example: 2026-05-01 runs the May billing cycle.');
    expect(getSimulationFieldHelperText('enrollment_date'))
      .toBe('Customer join date used for pro-rata within the selected billing cycle. Billing starts from the later of enrollment date and pricing start date.');
    expect(getSimulationFieldHelperText('LOYALTY_SCORE')).toBeNull();
  });

  test('returns shared highlighted guidance for simulation date callouts', () => {
    expect(getSimulationDateGuidance()).toEqual([
      {
        label: 'Effective Date',
        description: 'Billing-cycle date used to select the pricing month and active pricing configuration. Example: 2026-05-01 runs the May billing cycle.',
      },
      {
        label: 'Enrollment Date',
        description: 'Customer join date used for pro-rata within the selected billing cycle. Billing starts from the later of enrollment date and pricing start date.',
      },
    ]);
  });

  test('labels prorated untargeted discount bases clearly', () => {
    expect(formatPercentageBaseHint({
      valueType: 'DISCOUNT_PERCENTAGE',
      rawValue: 10,
      calculatedAmount: -78,
      targetComponentCode: undefined,
      activeDays: 18,
      billingCycleDays: 30,
    }, {
      isDiscount: true,
      formatCurrency: (amount) => `$${amount.toFixed(2)}`,
    })).toBe('10% of prorated total charge pool ($780.00)');
  });

  test('labels prorated targeted discount bases clearly', () => {
    expect(formatPercentageBaseHint({
      valueType: 'DISCOUNT_PERCENTAGE',
      rawValue: 50,
      calculatedAmount: -30,
      targetComponentCode: 'ADV_BASE_FEE',
      activeDays: 18,
      billingCycleDays: 30,
    }, {
      isDiscount: true,
      formatCurrency: (amount) => `$${amount.toFixed(2)}`,
      targetComponentLabel: 'Advanced Base Fee',
    })).toBe('50% of prorated Advanced Base Fee charge pool ($60.00)');
  });
});

