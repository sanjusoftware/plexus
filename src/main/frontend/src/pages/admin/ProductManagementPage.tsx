import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Package, Info } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionContent,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow,
  AuditTimestampCell
} from '../../components/AdminDataTable';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { PricingService } from '../../services/PricingService';

interface Product {
  id: number;
  code: string;
  name: string;
  version?: number;
  productType?: {
    code: string;
    name: string;
  };
  category: string;
  status: string;
  activationDate: string;
  tagline: string;
  fullDescription: string;
  createdAt?: string;
  updatedAt?: string;
}

const ProductManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal states
  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; productId?: number }>({ isOpen: false });
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean; productId?: number; productName?: string }>({ isOpen: false });
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const p = await axios.get('/api/v1/products', { signal: abortSignal });
      setProducts(p.data.content || []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch products. Please check your role permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchInitialData(signal);
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (successMessage && consumedSuccessKeyRef.current !== location.key) {
      consumedSuccessKeyRef.current = location.key;
      setToast({ message: successMessage, type: 'success' });
    }
  }, [location, setToast, fetchInitialData, signal]);

  const handleStatusAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/products/${id}/${action}`);
      setToast({ message: `Product ${action}d successfully.`, type: 'success' });
      PricingService.clearComponentCache(); // Clear cache when something changes
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} product.`, type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axios.delete(`/api/v1/products/${id}`);
      setToast({ message: 'Product deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    try {
      await axios.post(`/api/v1/products/${id}/deactivate`);
      setToast({ message: 'Product archived successfully. It is now terminal and removed from active circulation.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archival failed.', type: 'error' });
    }
  };

  const handleVersion = async (id: number) => {
    try {
      await axios.post(`/api/v1/products/${id}/create-new-version`, {});
      setToast({ message: 'New version created in DRAFT status. You can now edit this new version.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Versioning failed.', type: 'error' });
    }
  };


  return (
    <AdminPage>
      <AdminPageHeader
        icon={Package}
        title="Product Catalog"
        description="Configure bank offerings, feature sets, and pricing bindings."
        actions={
          <HasPermission action="POST" path="/api/v1/products">
            <button
              onClick={() => navigate('/products/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Product
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Fat DTO Architecture" tone="indigo">
        <span className="italic">This system uses a decoupled model. Products are aggregates that link to reusable <strong>Product Features</strong> and <strong>Pricing Components</strong>. You can create the product, its features, and its pricing links in one atomic request below.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product catalog table" containerClassName="overflow-x-auto" className="min-w-[1200px]">
          <thead>
            <tr>
              <th>Product Details</th>
              <th>Product Type</th>
              <th>Category</th>
              <th className="text-center">Status</th>
              <th>Updated At</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {products.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={6}>No active products found. Get started by clicking "Create New Product".</AdminDataTableEmptyRow>
            ) : (
              products.map((prod) => (
                <React.Fragment key={prod.id}>
                  <AdminDataTableRow
                    onClick={() => navigate(`/products/${prod.id}`)}
                    interactive
                    className="group"
                  >
                    {/* Product Info */}
                    <td className="max-w-[300px]">
                      <div className="flex items-center space-x-3">
                        <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300 flex-shrink-0">
                          <Package className="w-4 h-4 text-blue-600 group-hover:text-white transition duration-300" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 min-w-0">
                            <h3 className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900 transition leading-tight flex-1" title={prod.name}>{prod.name}</h3>
                            <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                              v{prod.version ?? 1}
                            </span>
                          </div>
                          <div className="text-[9px] font-mono font-bold text-blue-600 uppercase tracking-tight truncate">{prod.code}</div>
                        </div>
                      </div>
                    </td>

                    {/* Classification */}
                    <td className="max-w-[200px]">
                      {prod.productType ? (
                        <div className="truncate">
                          <div className="text-[11px] font-bold text-gray-700 leading-tight truncate" title={prod.productType.name}>{prod.productType.name}</div>
                          <div className="text-[9px] font-mono text-gray-400 truncate" title={prod.productType.code}>{prod.productType.code}</div>
                        </div>
                      ) : (
                        <div className="text-[10px] text-gray-300 italic uppercase">None</div>
                      )}
                    </td>

                    {/* Category */}
                    <td>
                      <div className="text-[11px] font-bold text-gray-600 uppercase truncate">
                        {prod.category}
                      </div>
                    </td>

                    {/* Status */}
                    <td className="text-center">
                      <div className="flex flex-col items-center">
                        <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                          {prod.status}
                        </span>
                        {prod.status === 'ACTIVE' && prod.activationDate && (
                            <span className="text-[9px] text-gray-400 font-bold mt-1">
                                {new Date(prod.activationDate).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: '2-digit' })}
                            </span>
                        )}
                      </div>
                    </td>

                    {/* Updated At */}
                    <AuditTimestampCell value={prod.updatedAt || prod.createdAt} />

                    {/* Actions */}
                    <AdminDataTableActionCell onClick={(e) => e.stopPropagation()}>
                      {prod.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/products/*/activate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleStatusAction(prod.id, 'activate'); }}
                            tone="success"
                            size="compact"
                            title="Activate Product"
                          >
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {prod.status === 'ACTIVE' && (
                        <HasPermission action="POST" path="/api/v1/products/*/create-new-version">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleVersion(prod.id); }}
                            tone="success"
                            size="compact"
                            title="Create Revision (v+1)"
                          >
                            <AdminDataTableActionContent action="version" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {(prod.status === 'DRAFT') && (
                        <HasPermission action="PATCH" path="/api/v1/products/*">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); navigate(`/products/edit/${prod.id}`); }}
                            tone="primary"
                            size="compact"
                            title="Modify Product"
                          >
                            <AdminDataTableActionContent action="edit" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      <HasPermission action="DELETE" path="/api/v1/products/*">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); prod.status === 'ACTIVE' ? setArchiveModal({ isOpen: true, productId: prod.id }) : setDeleteModal({ isOpen: true, productId: prod.id, productName: prod.name }); }}
                          tone="danger"
                          size="compact"
                          title={prod.status === 'ACTIVE' ? "Archive Product" : "Delete Product"}
                        >
                          <AdminDataTableActionContent action={prod.status === 'ACTIVE' ? 'archive' : 'delete'} />
                        </AdminDataTableActionButton>
                      </HasPermission>
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                </React.Fragment>
              ))
            )}
          </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={deleteModal.isOpen}
        onClose={() => setDeleteModal({ isOpen: false })}
        onConfirm={() => deleteModal.productId && handleDelete(deleteModal.productId)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete "${deleteModal.productName || 'this product'}"? This action cannot be undone.`}
        confirmText="Confirm & Delete"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={archiveModal.isOpen}
        onClose={() => setArchiveModal({ isOpen: false })}
        onConfirm={() => archiveModal.productId && handleArchive(archiveModal.productId)}
        title="Confirm Product Archival"
        message="Are you sure you want to archive this product? This action is terminal: it will immediately remove the product from active circulation and it cannot be re-activated. Customers currently enrolled will be affected based on bank policy."
        confirmText="Archive Product"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductManagementPage;
