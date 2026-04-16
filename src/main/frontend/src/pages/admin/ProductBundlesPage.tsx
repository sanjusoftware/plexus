import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Library } from 'lucide-react';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
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

interface BundleProduct {
  productId: number;
  productName: string;
  productCode: string;
  mainAccount: boolean;
  mandatory: boolean;
}

interface BundlePricing {
  pricingComponentId: number;
  pricingComponentName: string;
  targetComponentCode?: string;
  fixedValue?: number;
  fixedValueType?: string;
  useRulesEngine: boolean;
  effectiveDate?: string;
  expiryDate?: string | null;
}

interface ProductBundle {
  id: number;
  code: string;
  name: string;
  version?: number;
  status: string;
  targetCustomerSegments: string;
  activationDate: string;
  description: string;
  products: BundleProduct[];
  pricing: BundlePricing[];
  createdAt?: string;
  updatedAt?: string;
}

const ProductBundlesPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();

  const [bundles, setBundles] = useState<ProductBundle[]>([]);
  const [loading, setLoading] = useState(true);

  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; bundleId?: number }>({ isOpen: false });
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean; bundleId?: number; bundleName?: string }>({ isOpen: false });
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const b = await axios.get('/api/v1/bundles', { signal: abortSignal }).catch(err => {
          // Handle 405 or other errors gracefully - bundles endpoint may not be available
          if (err.response?.status === 405 || err.response?.status === 404) {
            return { data: { content: [] } };
          }
          throw err;
        });
      setBundles(b.data.content || []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch bundles. Please check your role permissions.', type: 'error' });
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
      await axios.post(`/api/v1/bundles/${id}/${action}`);
      setToast({ message: `Bundle ${action}d successfully.`, type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} bundle.`, type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    try {
      // Assuming bundle delete acts as archive for ACTIVE bundles in backend as per ProductManagementPage pattern
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle archived successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archival failed.', type: 'error' });
    }
  };

  const handleVersion = async (id: number) => {
    try {
      await axios.post(`/api/v1/bundles/${id}/create-new-version`, {});
      setToast({ message: 'New version created in DRAFT status.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Versioning failed.', type: 'error' });
    }
  };


  return (
    <AdminPage>
      <AdminPageHeader
        icon={Library}
        title="Product Bundles"
        description="Manage product aggregates, mandatory inclusions, and bundle-level pricing."
        actions={
          <HasPermission permission="catalog:bundle:create">
            <button
              onClick={() => navigate('/bundles/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Bundle
            </button>
          </HasPermission>
        }
      />

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable containerClassName="overflow-x-auto" className="min-w-[1200px]">
          <thead>
            <tr>
              <th>Bundle Details</th>
              <th>Target Segments</th>
              <th className="text-center">Status</th>
              <th>Updated At</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {bundles.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={5}>No product bundles found. Click "New Bundle" to create one.</AdminDataTableEmptyRow>
            ) : (
              bundles.map((bundle) => (
                <React.Fragment key={bundle.id}>
                  <AdminDataTableRow
                    onClick={() => navigate(`/bundles/${bundle.id}`)}
                    interactive
                    className="group"
                  >
                    <td className="max-w-[300px]">
                      <div className="flex items-center space-x-3">
                        <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300 flex-shrink-0">
                          <Library className="w-4 h-4 text-blue-600 group-hover:text-white transition duration-300" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 min-w-0">
                            <h3 className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900 transition leading-tight flex-1" title={bundle.name}>{bundle.name}</h3>
                            <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                              v{bundle.version ?? 1}
                            </span>
                          </div>
                          <div className="text-[9px] font-mono font-bold text-blue-600 uppercase tracking-tight truncate">{bundle.code}</div>
                        </div>
                      </div>
                    </td>

                    <td>
                      <div className="flex flex-wrap gap-1">
                        {bundle.targetCustomerSegments.split(',').map((seg, idx) => (
                          <span key={idx} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-[9px] font-bold rounded uppercase tracking-tighter">
                            {seg.trim()}
                          </span>
                        ))}
                      </div>
                    </td>

                    <td className="text-center">
                      <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${bundle.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                        {bundle.status}
                      </span>
                    </td>

                    <AuditTimestampCell value={bundle.updatedAt || bundle.createdAt} />

                    <AdminDataTableActionCell onClick={(e) => e.stopPropagation()}>
                      {bundle.status === 'DRAFT' && (
                        <HasPermission permission="catalog:bundle:activate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleStatusAction(bundle.id, 'activate'); }}
                            tone="success"
                            size="compact"
                          >
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {bundle.status === 'ACTIVE' && (
                        <HasPermission permission="catalog:bundle:create">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleVersion(bundle.id); }}
                            tone="success"
                            size="compact"
                          >
                            <AdminDataTableActionContent action="version" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {bundle.status === 'DRAFT' && (
                        <HasPermission permission="catalog:bundle:update">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); navigate(`/bundles/edit/${bundle.id}`); }}
                            tone="primary"
                            size="compact"
                          >
                            <AdminDataTableActionContent action="edit" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      <HasPermission permission="catalog:bundle:delete">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); bundle.status === 'ACTIVE' ? setArchiveModal({ isOpen: true, bundleId: bundle.id }) : setDeleteModal({ isOpen: true, bundleId: bundle.id, bundleName: bundle.name }); }}
                          tone="danger"
                          size="compact"
                        >
                          <AdminDataTableActionContent action={bundle.status === 'ACTIVE' ? 'archive' : 'delete'} />
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
        onConfirm={() => deleteModal.bundleId && handleDelete(deleteModal.bundleId)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete bundle "${deleteModal.bundleName}"?`}
        confirmText="Delete Bundle"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={archiveModal.isOpen}
        onClose={() => setArchiveModal({ isOpen: false })}
        onConfirm={() => archiveModal.bundleId && handleArchive(archiveModal.bundleId)}
        title="Confirm Archival"
        message="Are you sure you want to archive this bundle? It will be removed from circulation."
        confirmText="Archive Bundle"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductBundlesPage;
