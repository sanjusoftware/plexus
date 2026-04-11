import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, List } from 'lucide-react';
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
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

interface ProductType {
  id: number;
  name: string;
  code: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}


const ProductTypesPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [typeToActOn, setTypeToActOn] = useState<ProductType | null>(null);
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();

  const fetchProductTypes = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/product-types', { signal: abortSignal });
      setProductTypes(response.data);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch product types. Make sure you have the required permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchProductTypes(signal);
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (successMessage && consumedSuccessKeyRef.current !== location.key) {
      consumedSuccessKeyRef.current = location.key;
      setToast({ message: successMessage, type: 'success' });
    }
  }, [fetchProductTypes, signal, location, setToast]);

  const handleAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/product-types/${id}/${action}`);
      setToast({ message: `Product type ${action}d successfully.`, type: 'success' });
      await fetchProductTypes(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} product type.`, type: 'error' });
    }
  };

  const handleConfirmAction = async () => {
    if (!typeToActOn) return;

    try {
      if (typeToActOn.status === 'DRAFT') {
        await axios.delete(`/api/v1/product-types/${typeToActOn.id}`);
        setToast({ message: 'Product type deleted successfully.', type: 'success' });
      } else if (typeToActOn.status === 'ACTIVE') {
        await axios.post(`/api/v1/product-types/${typeToActOn.id}/archive`);
        setToast({ message: 'Product type archived successfully.', type: 'success' });
      }
      await fetchProductTypes(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${typeToActOn.status === 'DRAFT' ? 'delete' : 'archive'} product type.`, type: 'error' });
    } finally {
      setShowConfirmModal(false);
      setTypeToActOn(null);
    }
  };

  const triggerConfirmAction = (type: ProductType) => {
    setTypeToActOn(type);
    setShowConfirmModal(true);
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={List}
        title="Product Types"
        description="Define broad categories for your bank's products such as savings, loans, and cards."
        actions={
          <HasPermission action="POST" path="/api/v1/product-types">
            <button
              onClick={() => navigate('/product-types/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" />
              New Type
            </button>
          </HasPermission>
        }
      />

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product types table">
            <thead>
              <tr>
                <th>Type Details</th>
                <th>Status</th>
                <th>Updated At</th>
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {productTypes.length === 0 ? (
                <AdminDataTableEmptyRow colSpan={4}>No product types found. Get started by creating your first one.</AdminDataTableEmptyRow>
              ) : (
                productTypes.map((type) => (
                  <AdminDataTableRow key={type.id}>
                    <td className="whitespace-nowrap max-w-[250px]">
                      <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={type.name}>{type.name}</div>
                      <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={type.code}>{type.code}</div>
                    </td>
                    <td className="whitespace-nowrap">
                      <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${
                        type.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                        type.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                        type.status === 'ARCHIVED' ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'
                      }`}>
                        {type.status}
                      </span>
                    </td>
                    <AuditTimestampCell value={type.updatedAt || type.createdAt} />
                    <AdminDataTableActionCell>
                      {type.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/product-types/*/activate">
                          <AdminDataTableActionButton onClick={() => handleAction(type.id, 'activate')} tone="success" size="compact" title="Activate" aria-label={`Activate ${type.name}`}>
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {type.status === 'DRAFT' && (
                        <>
                          <HasPermission action="PUT" path="/api/v1/product-types/*">
                            <AdminDataTableActionButton onClick={() => navigate(`/product-types/edit/${type.id}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${type.name}`}>
                              <AdminDataTableActionContent action="edit" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="DELETE" path="/api/v1/product-types/*">
                            <AdminDataTableActionButton onClick={() => triggerConfirmAction(type)} tone="danger" size="compact" title="Delete" aria-label={`Delete ${type.name}`}>
                              <AdminDataTableActionContent action="delete" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {type.status === 'ACTIVE' && (
                        <>
                          <HasPermission action="PUT" path="/api/v1/product-types/*">
                            <AdminDataTableActionButton disabled tone="neutral" size="compact" title="Active product types cannot be edited. Archive and create a new draft type instead." aria-label={`Edit ${type.name} (Disabled)`}>
                              <AdminDataTableActionContent action="edit" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/product-types/*/archive">
                            <AdminDataTableActionButton onClick={() => triggerConfirmAction(type)} tone="danger" size="compact" title="Archive" aria-label={`Archive ${type.name}`}>
                              <AdminDataTableActionContent action="archive" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                ))
              )}
            </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={showConfirmModal}
        onClose={() => { setShowConfirmModal(false); setTypeToActOn(null); }}
        onConfirm={handleConfirmAction}
        title={typeToActOn?.status === 'DRAFT' ? "Confirm Deletion" : "Confirm Archival"}
        message={typeToActOn?.status === 'DRAFT'
          ? "Are you sure you want to permanently delete this product type? This action cannot be undone."
          : "Are you sure you want to archive this product type? This will prevent it from being used for new products."}
        confirmText={typeToActOn?.status === 'DRAFT' ? "Confirm & Delete" : "Confirm & Archive"}
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductTypesPage;
