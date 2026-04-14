import React from 'react';
import { render, screen, within } from '@testing-library/react';
import ProductManagementPage from './ProductManagementPage';
import { formatAuditTimestamp } from '../../utils/auditTimestamp';

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

describe('Product Catalog Page Table', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockSetToast.mockReset();
    mockAxios.get.mockReset();
    mockAxios.post.mockReset();
    mockAxios.delete.mockReset();
  });

  test('renders standard AdminDataTable header structure', async () => {
    mockAxios.get.mockResolvedValueOnce({
      data: {
        content: [
          {
            id: 1,
            code: 'PREMIUM_CREDIT_CARDS',
            name: 'Premium Credit Cards',
            productType: { code: 'CREDIT_CARD', name: 'Credit Card' },
            category: 'RETAIL',
            status: 'DRAFT',
            createdAt: '2026-04-06T23:58:00Z',
            updatedAt: '2026-04-06T23:58:00Z'
          }
        ]
      }
    });

    render(<ProductManagementPage />);

    const table = await screen.findByRole('table', { name: /product catalog table/i });
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);

    expect(headers).toEqual(['Product Details', 'Product Type', 'Category', 'Status', 'Updated At', 'Actions']);
    expect(within(table).getByText('Premium Credit Cards')).toBeInTheDocument();
    expect(within(table).getByText('PREMIUM_CREDIT_CARDS')).toBeInTheDocument();
    expect(within(table).getByText('Credit Card')).toBeInTheDocument();
    expect(within(table).getByText('RETAIL')).toBeInTheDocument();
    expect(within(table).getByText('DRAFT')).toBeInTheDocument();
    expect(within(table).getAllByText(formatAuditTimestamp('2026-04-06T23:58:00Z'))[0]).toBeInTheDocument();
  });

  test('uses AdminDataTableActionCell and shared action styling', async () => {
     mockAxios.get.mockResolvedValueOnce({
      data: {
        content: [
          {
            id: 1,
            code: 'PREMIUM_CREDIT_CARDS',
            name: 'Premium Credit Cards',
            status: 'DRAFT'
          }
        ]
      }
    });

    render(<ProductManagementPage />);

    const table = await screen.findByRole('table', { name: /product catalog table/i });
    const actionsHeader = within(table).getByText('Actions');
    expect(actionsHeader).toHaveClass('admin-table__actions-header');

    const activateButton = within(table).getByTitle('Activate Product');
    expect(activateButton).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--success', 'admin-table__action-btn--compact');

    const editButton = within(table).getByTitle('Modify Product');
    expect(editButton).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--primary', 'admin-table__action-btn--compact');

    const deleteButton = within(table).getByTitle('Delete Product');
    expect(deleteButton).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--danger', 'admin-table__action-btn--compact');
  });

  test('renders version and archive actions for ACTIVE status', async () => {
    mockAxios.get.mockResolvedValueOnce({
      data: {
        content: [
          {
            id: 2,
            code: 'RETAIL_CURRENT_ACCOUNT',
            name: 'Retail Current Account',
            status: 'ACTIVE'
          }
        ]
      }
    });

    render(<ProductManagementPage />);

    const table = await screen.findByRole('table', { name: /product catalog table/i });

    const versionButton = within(table).getByTitle('Create Revision (v+1)');
    expect(versionButton).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--success', 'admin-table__action-btn--compact');

    const editButton = within(table).queryByTitle('Direct editing is not allowed for active products. Create a new version to make changes.');
    expect(editButton).not.toBeInTheDocument();

    const archiveButton = within(table).getByTitle('Archive Product');
    expect(archiveButton).toHaveClass('admin-table__action-btn', 'admin-table__action-btn--danger', 'admin-table__action-btn--compact');
  });
});
