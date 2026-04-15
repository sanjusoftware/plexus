import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ProductFormPage from '../../../pages/admin/ProductFormPage';

const mockNavigate = jest.fn();
const mockSetToast = jest.fn();
const mockSetEntityName = jest.fn();
const mockHasPermission = jest.fn();
const mockResetDirtyBaseline = jest.fn();
const mockConfirmDiscardChanges = jest.fn().mockReturnValue(true);
const mockParams: { id?: string } = {};
const mockAxios = {
  get: jest.fn(),
  post: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn(),
  isCancel: jest.fn().mockReturnValue(false)
};

const stableSignal = { aborted: false } as AbortSignal;

const toTestIdSlug = (value: string) => value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

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
  useParams: () => mockParams
}), { virtual: true });

jest.mock('../../../context/AuthContext', () => ({
  useAuth: () => ({
    user: { bank_id: 'BANK_A' },
    setToast: mockSetToast
  })
}));

jest.mock('../../../context/BreadcrumbContext', () => ({
  useBreadcrumb: () => ({
    setEntityName: mockSetEntityName
  })
}));

jest.mock('../../../hooks/useAbortSignal', () => ({
  useAbortSignal: () => stableSignal
}));

jest.mock('../../../hooks/useHasPermission', () => ({
  useHasPermission: () => ({
    hasPermission: (...args: any[]) => mockHasPermission(...args)
  })
}));

jest.mock('../../../hooks/useUnsavedChangesGuard', () => ({
  useUnsavedChangesGuard: () => ({
    resetDirtyBaseline: mockResetDirtyBaseline,
    confirmDiscardChanges: mockConfirmDiscardChanges
  })
}));

jest.mock('../../../components/PriceSimulationTool', () => () => null);

jest.mock('../../../components/PlexusSelect', () => ({
  __esModule: true,
  default: ({ options = [], value, onChange, placeholder, formatOptionLabel, optionMetaLayout = 'inline' }: any) => {
    const slug = toTestIdSlug(placeholder || 'select');
    const renderNode = (option: any) => {
      if (formatOptionLabel) {
        return formatOptionLabel(option);
      }

      if (!option) {
        return null;
      }

      const secondary = option.secondaryLabel || option.code || option.metaKey;
      if (!secondary) {
        return option.label;
      }

      return optionMetaLayout === 'stacked'
        ? <span>{option.label}<br />({secondary})</span>
        : <span>{option.label} ({secondary})</span>;
    };

    return (
      <div data-testid={`select-${slug}`}>
        <div data-testid={`value-${slug}`}>{value ? renderNode(value) : placeholder}</div>
        <div>
          {options.map((option: any) => (
            <button
              key={`${slug}-${option.value}`}
              type="button"
              onClick={() => onChange?.(option)}
            >
              {renderNode(option)}
            </button>
          ))}
        </div>
      </div>
    );
  }
}));

describe('ProductFormPage', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockSetToast.mockReset();
    mockSetEntityName.mockReset();
    mockHasPermission.mockReset();
    mockResetDirtyBaseline.mockReset();
    mockConfirmDiscardChanges.mockReset();
    mockConfirmDiscardChanges.mockReturnValue(true);
    mockAxios.get.mockReset();
    mockAxios.post.mockReset();
    mockAxios.patch.mockReset();
    mockAxios.delete.mockReset();
    mockParams.id = undefined;
    mockHasPermission.mockReturnValue(true);
  });

  const mockBaseLookups = () => {
    mockAxios.get.mockImplementation((url: string) => {
      if (url === '/api/v1/features') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/api/v1/pricing-components') {
        return Promise.resolve({
          data: [
            { code: 'ADV_SALARY_BASE_DISCOUNT', name: 'Advanced Salary Based Discount' },
            { code: 'LOYALTY_DISCOUNT', name: 'Loyalty Discount' },
            { code: 'ADV_BASE_FEE', name: 'Advanced Base Fee' }
          ]
        });
      }
      if (url === '/api/v1/product-types') {
        return Promise.resolve({
          data: [
            { code: 'RETAIL_BANKING', name: 'Retail Banking' }
          ]
        });
      }
      if (url === '/api/v1/product-categories') {
        return Promise.resolve({
          data: [
            { code: 'RETAIL', name: 'Retail' },
            { code: 'AFFLUENT_SALARIED', name: 'Affluent Salaried' }
          ]
        });
      }
      if (url === '/api/v1/banks/BANK_A/product-categories') {
        return Promise.resolve({
          data: {
            categories: ['RETAIL', 'AFFLUENT_SALARIED'],
            examples: ['RETAIL', 'WEALTH']
          }
        });
      }
      if (url === '/api/v1/products/42') {
        return Promise.resolve({
          data: {
            id: 42,
            status: 'DRAFT',
            code: 'ADV_PRODUCT_RULED_MIX',
            name: 'Advanced mix rule based product',
            category: 'AFFLUENT_SALARIED',
            productType: { code: 'RETAIL_BANKING', name: 'Retail Banking' },
            tagline: '',
            fullDescription: '',
            features: [],
            pricing: [
              {
                pricingComponentCode: 'ADV_SALARY_BASE_DISCOUNT',
                targetComponentCode: 'LOYALTY_DISCOUNT',
                useRulesEngine: false,
                fixedValueType: 'FEE_ABSOLUTE'
              }
            ]
          }
        });
      }
      return Promise.resolve({ data: null });
    });
  };

  test('renders product classification, target market segment, and target component with display name plus code', async () => {
    mockParams.id = '42';
    mockBaseLookups();

    render(<ProductFormPage />);

    await screen.findByDisplayValue('Advanced mix rule based product');

    const productTypeValue = screen.getByTestId(`value-${toTestIdSlug('Select Product Classification...')}`);
    expect(productTypeValue).toHaveTextContent('Retail Banking');
    expect(productTypeValue).toHaveTextContent('(RETAIL_BANKING)');

    const aggregateValue = screen.getByTestId(`value-${toTestIdSlug('Select Global Pricing...')}`);
    expect(aggregateValue).toHaveTextContent('Advanced Salary Based Discount');
    expect(aggregateValue).toHaveTextContent('(ADV_SALARY_BASE_DISCOUNT)');

    const categoryValue = screen.getByTestId(`value-${toTestIdSlug('Select Existing Category...')}`);
    expect(categoryValue).toHaveTextContent('Affluent Salaried');
    expect(categoryValue).toHaveTextContent('(AFFLUENT_SALARIED)');

    const targetValue = screen.getByTestId(`value-${toTestIdSlug('Select Target (Optional)...')}`);
    expect(targetValue).toHaveTextContent('Loyalty Discount');
    expect(targetValue).toHaveTextContent('(LOYALTY_DISCOUNT)');
  });

  test('creates a new category inline and selects it on the form', async () => {
    mockBaseLookups();
    mockAxios.post.mockResolvedValueOnce({
      data: { code: 'AFFLUENT_PLUS', name: 'Affluent Plus' }
    });

    render(<ProductFormPage />);

    await screen.findByTestId(`value-${toTestIdSlug('Select Existing Category...')}`);

    fireEvent.click(screen.getByRole('button', { name: /\+ create new category/i }));

    fireEvent.change(screen.getByPlaceholderText('e.g. Affluent Salaried'), {
      target: { value: 'Affluent Plus' }
    });
    fireEvent.change(screen.getByPlaceholderText('AFFLUENT_SALARIED'), {
      target: { value: 'AFFLUENT_PLUS' }
    });

    fireEvent.click(screen.getByRole('button', { name: 'Create Category' }));

    await waitFor(() => {
      expect(mockAxios.post).toHaveBeenCalledWith('/api/v1/product-categories', {
        code: 'AFFLUENT_PLUS',
        name: 'Affluent Plus'
      });
    });

    await waitFor(() => {
      const categoryValue = screen.getByTestId(`value-${toTestIdSlug('Select Existing Category...')}`);
      expect(categoryValue).toHaveTextContent('Affluent Plus');
      expect(categoryValue).toHaveTextContent('(AFFLUENT_PLUS)');
    });
    expect(mockSetToast).toHaveBeenCalledWith({ message: 'Product category created successfully.', type: 'success' });
  });
});




