import { getSimulationEffectiveDateFloor, getSimulationEffectiveDateValidationMessage, isIsoDateString } from './ProductManagementPage.utils';

describe('ProductManagementPage simulation date helpers', () => {
  const product = {
    code: 'ADV_PRODUCT_BREACH_PRORATA',
    pricing: [
      { effectiveDate: '2026-04-13', expiryDate: null },
      { effectiveDate: '2026-05-01', expiryDate: null },
      { effectiveDate: undefined, expiryDate: null },
    ]
  };

  test('recognizes valid ISO date strings only', () => {
    expect(isIsoDateString('2026-04-13')).toBe(true);
    expect(isIsoDateString('2026-4-13')).toBe(false);
    expect(isIsoDateString('13-04-2026')).toBe(false);
    expect(isIsoDateString(undefined)).toBe(false);
  });

  test('returns the earliest configured pricing effective date for simulation guardrails', () => {
    expect(getSimulationEffectiveDateFloor(product)).toBe('2026-04-13');
  });

  test('returns a clear validation message when the requested date is before pricing becomes active', () => {
    expect(getSimulationEffectiveDateValidationMessage(product, '2026-04-01'))
      .toBe('Pricing for ADV_PRODUCT_BREACH_PRORATA is available from 2026-04-13.');
  });

  test('allows same-day and later pricing simulation dates', () => {
    expect(getSimulationEffectiveDateValidationMessage(product, '2026-04-13')).toBeUndefined();
    expect(getSimulationEffectiveDateValidationMessage(product, '2026-04-30')).toBeUndefined();
  });
});

