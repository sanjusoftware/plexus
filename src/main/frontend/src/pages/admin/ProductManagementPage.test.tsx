import { formatComponentLabelWithProRata, formatProRataHint } from './ProductManagementPage.utils';

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

  test('returns null for invalid active-day values', () => {
    expect(formatProRataHint({ activeDays: -1, billingCycleDays: 30 })).toBeNull();
    expect(formatProRataHint({ activeDays: 31, billingCycleDays: 30 })).toBeNull();
    expect(formatProRataHint({ activeDays: 10, billingCycleDays: 0 })).toBeNull();
  });

  test('leaves the component label unchanged when there is no valid pro-rata metadata', () => {
    expect(formatComponentLabelWithProRata('Monthly Fee', { activeDays: undefined, billingCycleDays: 30 }))
      .toBe('Monthly Fee');
  });
});

