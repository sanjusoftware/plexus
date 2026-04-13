import React from 'react';
import { render, screen, within } from '@testing-library/react';
import PricingComponentFormPage from './PricingComponentFormPage';

const mockNavigate = jest.fn();
const mockSetToast = jest.fn();
const mockSetEntityName = jest.fn();
const mockAxios = {
  get: jest.fn(),
  post: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn(),
  isCancel: jest.fn().mockReturnValue(false)
};

const stableSignal = { aborted: false } as AbortSignal;

jest.mock('axios', () => ({
  __esModule: true,
  default: {
    get: (...args: any[]) => mockAxios.get(...args),
    post: (...args: any[]) => mockAxios.post(...args),
    patch: (...args: any[]) => mockAxios.patch(...args),
    delete: (...args: any[]) => mockAxios.delete(...args),
    isCancel: (val: any) => mockAxios.isCancel(val),
    interceptors: {
      request: { use: jest.fn(), eject: jest.fn() },
      response: { use: jest.fn(), eject: jest.fn() }
    },
    defaults: { withCredentials: false }
  }
}), { virtual: true });

jest.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useParams: () => ({ id: '10' })
}), { virtual: true });

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    user: { currencyCode: 'USD' },
    setToast: mockSetToast
  })
}));

jest.mock('../../context/BreadcrumbContext', () => ({
  useBreadcrumb: () => ({
    setEntityName: mockSetEntityName
  })
}));

jest.mock('../../hooks/useAbortSignal', () => ({
  useAbortSignal: () => stableSignal
}));

jest.mock('../../hooks/useUnsavedChangesGuard', () => ({
  useUnsavedChangesGuard: () => ({
    resetDirtyBaseline: jest.fn(),
    confirmDiscardChanges: jest.fn().mockReturnValue(true)
  })
}));

describe('PricingComponentFormPage full-breach hydration', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockSetToast.mockReset();
    mockSetEntityName.mockReset();
    mockAxios.get.mockReset();
    mockAxios.post.mockReset();
    mockAxios.patch.mockReset();
    mockAxios.delete.mockReset();
  });

  const mockEditPayload = (tier: any) => {
    mockAxios.get.mockImplementation((url: string) => {
      if (url === '/api/v1/pricing-metadata') {
        return Promise.resolve({ data: [] });
      }

      if (url === '/api/v1/pricing-components/10') {
        return Promise.resolve({
          data: {
            id: 10,
            name: 'Breach Fee',
            code: 'ADV_BREACH_FEE',
            status: 'DRAFT',
            type: 'FEE',
            description: '',
            proRataApplicable: false,
            pricingTiers: [tier]
          }
        });
      }

      return Promise.resolve({ data: null });
    });
  };

  const getFullBreachToggleSection = async () => {
    await screen.findByText('Segment Pricing Tiers');
    const heading = screen.getByText('Apply Charge On Full Breach');
    const section = heading.closest('div');
    if (!section) {
      throw new Error('Unable to locate full-breach toggle section.');
    }
    return section;
  };

  test('hydrates full-breach toggle from nested priceValues[0].applyChargeOnFullBreach=true', async () => {
    mockEditPayload({
      id: 15,
      name: 'Full breach',
      code: 'BREACH_FULL',
      priority: 100,
      minThreshold: 10000,
      maxThreshold: null,
      conditions: [],
      priceValues: [
        {
          rawValue: 5,
          valueType: 'FEE_PERCENTAGE',
          applyChargeOnFullBreach: true
        }
      ]
    });

    render(<PricingComponentFormPage />);

    const section = await getFullBreachToggleSection();
    expect(within(section).getByText('ENABLED')).toBeInTheDocument();
  });

  test('uses tier-level applyChargeOnFullBreach when present (tier=false overrides nested=true)', async () => {
    mockEditPayload({
      id: 15,
      name: 'Full breach',
      code: 'BREACH_FULL',
      priority: 100,
      minThreshold: 10000,
      maxThreshold: null,
      applyChargeOnFullBreach: false,
      conditions: [],
      priceValues: [
        {
          rawValue: 5,
          valueType: 'FEE_PERCENTAGE',
          applyChargeOnFullBreach: true
        }
      ]
    });

    render(<PricingComponentFormPage />);

    const section = await getFullBreachToggleSection();
    expect(within(section).getByText('DISABLED')).toBeInTheDocument();
  });
});

