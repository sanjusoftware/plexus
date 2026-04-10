import React from 'react';
import { render, screen, within } from '@testing-library/react';
import PricingMetadataPage from './PricingMetadataPage';
import ProductTypesPage from './ProductTypesPage';

const mockNavigate = jest.fn();
const mockSetToast = jest.fn();
const mockAxios = {
  get: jest.fn(),
  post: jest.fn(),
  delete: jest.fn(),
  isCancel: jest.fn().mockReturnValue(false)
};

jest.mock('axios', () => ({
  __esModule: true,
  default: {
    get: (...args: any[]) => mockAxios.get(...args),
    post: (...args: any[]) => mockAxios.post(...args),
    delete: (...args: any[]) => mockAxios.delete(...args),
    isCancel: (...args: any[]) => mockAxios.isCancel(...args),
    interceptors: {
      request: { use: jest.fn(), eject: jest.fn() },
      response: { use: jest.fn(), eject: jest.fn() }
    },
    defaults: { withCredentials: false }
  }
}), { virtual: true });

jest.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => ({ pathname: '/', state: {} })
}), { virtual: true });

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    setToast: mockSetToast
  })
}));

jest.mock('../../components/HasPermission', () => ({
  HasPermission: ({ children }: { children: React.ReactNode }) => <>{children}</>
}));

describe('Admin table pages', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockSetToast.mockReset();
    mockAxios.get.mockReset();
    mockAxios.post.mockReset();
    mockAxios.delete.mockReset();
  });

  test('pricing metadata renders Name before Key with shared action styling', async () => {
    mockAxios.get.mockResolvedValueOnce({
      data: [
        {
          id: 1,
          displayName: 'Customer Segment',
          attributeKey: 'customer_segment',
          dataType: 'STRING'
        }
      ]
    });

    render(<PricingMetadataPage />);

    const table = await screen.findByRole('table', { name: /pricing metadata table/i });
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);

    expect(headers).toEqual(['Attribute Details', 'Type', 'Actions']);
    expect(within(table).getByText('Customer Segment')).toBeInTheDocument();
    expect(within(table).getByText('customer_segment')).toBeInTheDocument();
    expect(within(table).getByText('Actions')).toHaveClass('admin-table__actions-header');
    expect(within(table).getByTitle('Edit')).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--primary');
    expect(within(table).getByTitle('Delete')).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--danger');
  });

  test('product types renders Name before Code and uses the shared action button variants', async () => {
    mockAxios.get.mockResolvedValueOnce({
      data: [
        {
          id: 99,
          name: 'Savings Account',
          code: 'SAVINGS',
          status: 'DRAFT'
        }
      ]
    });

    render(<ProductTypesPage />);

    const table = await screen.findByRole('table', { name: /product types table/i });
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);

    expect(headers).toEqual(['Type Details', 'Status', 'Actions']);
    expect(within(table).getByText('Savings Account')).toBeInTheDocument();
    expect(within(table).getByText('SAVINGS')).toBeInTheDocument();
    expect(within(table).getByText('Actions')).toHaveClass('admin-table__actions-header');
    expect(within(table).getByTitle('Activate')).toHaveClass(
      'admin-table__action-btn',
      'admin-table__action-btn--success',
      'admin-table__action-btn--compact'
    );
    expect(within(table).getByTitle('Edit')).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--primary');
    expect(within(table).getByTitle('Delete')).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--danger');
  });
});

