import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import PriceSimulationTool from './PriceSimulationTool';
import { PricingService } from '../services/PricingService';

const mockCalculateProductPrice = jest.fn();

jest.mock('../services/PricingService', () => ({
  PricingService: {
    calculateProductPrice: (...args: any[]) => mockCalculateProductPrice(...args),
    formatCurrency: (amount: number) => `$${amount.toFixed(2)}`,
    getValueTypeLabel: (valueType: string) => valueType,
    isPercentageType: (valueType: string) => valueType.includes('PERCENTAGE'),
    isFeeType: (valueType: string) => valueType.startsWith('FEE_') || valueType === 'RATE_ABSOLUTE',
  },
}));

jest.mock('./PlexusSelect', () => ({
  __esModule: true,
  default: ({ options = [], value, onChange }: any) => (
    <select
      value={value?.value ?? ''}
      onChange={(event) => onChange?.(options.find((option: any) => option.value === event.target.value) || null)}
    >
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>{option.label}</option>
      ))}
    </select>
  )
}));

describe('PriceSimulationTool', () => {
  beforeEach(() => {
    mockCalculateProductPrice.mockReset();
    mockCalculateProductPrice.mockResolvedValue({
      finalChargeablePrice: 18,
      componentBreakdown: [],
    });
  });

  test('sends canonical pricing keys including loyalty score and salary account flag', async () => {
    render(<PriceSimulationTool isOpen onClose={jest.fn()} defaultProductId={7} />);

    // Set Loyalty Score
    const numberInputs = document.querySelectorAll('input[type="number"]');
    const loyaltyScoreInput = Array.from(numberInputs).find((input) => (input as HTMLInputElement).placeholder === '' && (input as HTMLInputElement).value === '0') as HTMLInputElement | undefined;
    if (loyaltyScoreInput) {
      fireEvent.change(loyaltyScoreInput, { target: { value: '100' } });
    }

    // Set Is Salary Account checkbox
    const checkboxes = document.querySelectorAll('input[type="checkbox"]');
    if (checkboxes.length > 0) {
      fireEvent.click(checkboxes[0]);
    }

    const dateInputs = document.querySelectorAll('input[type="date"]');
    fireEvent.change(dateInputs[0], { target: { value: '2026-05-01' } });
    fireEvent.change(dateInputs[1], { target: { value: '2026-04-13' } });

    fireEvent.click(screen.getByRole('button', { name: /run simulation/i }));

    await waitFor(() => {
      expect(mockCalculateProductPrice).toHaveBeenCalledWith({
        productId: 7,
        enrollmentDate: '2026-04-13',
        customAttributes: {
          TRANSACTION_AMOUNT: 1000,
          CUSTOMER_SEGMENT: 'RETAIL',
          EFFECTIVE_DATE: '2026-05-01',
          ENROLLMENT_DATE: '2026-04-13',
          LOYALTY_SCORE: 100,
          IS_SALARY_ACCOUNT: true,
        }
      });
    });
  });
});

