export interface ProductSimulationPricingLink {
  effectiveDate?: string;
  expiryDate?: string | null;
}

export interface ProductSimulationProduct {
  code: string;
  pricing?: ProductSimulationPricingLink[];
}

export const isIsoDateString = (value?: string | null): value is string =>
  Boolean(value && /^\d{4}-\d{2}-\d{2}$/.test(value));

export const getSimulationEffectiveDateFloor = (product: ProductSimulationProduct): string | undefined => {
  const effectiveDates = (product.pricing || [])
    .map((link) => link.effectiveDate)
    .filter(isIsoDateString)
    .sort();

  return effectiveDates[0];
};

export const getSimulationEffectiveDateValidationMessage = (
  product: ProductSimulationProduct,
  requestedEffectiveDate?: string | null,
): string | undefined => {
  const minimumEffectiveDate = getSimulationEffectiveDateFloor(product);
  if (!minimumEffectiveDate || !isIsoDateString(requestedEffectiveDate) || requestedEffectiveDate >= minimumEffectiveDate) {
    return undefined;
  }

  return `Pricing for ${product.code} is available from ${minimumEffectiveDate}.`;
};

