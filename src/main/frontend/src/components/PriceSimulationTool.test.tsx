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

  test('shows helper text for effective and enrollment dates and sends canonical pricing keys', async () => {
    render(<PriceSimulationTool isOpen onClose={jest.fn()} defaultProductId={7} />);

    expect(screen.getByText('How dates work')).toBeInTheDocument();
    expect(screen.getAllByText('Billing-cycle date used to select the pricing month and active pricing configuration. Example: 2026-05-01 runs the May billing cycle.')).toHaveLength(2);
    expect(screen.getAllByText('Customer join date used for pro-rata within the selected billing cycle. Billing starts from the later of enrollment date and pricing start date.')).toHaveLength(2);

    const dateInputs = document.querySelectorAll('input[type="date"]');
    expect(dateInputs).toHaveLength(2);

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
        }
      });
    });
  });
});

