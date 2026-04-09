import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, List, CheckCircle2 } from 'lucide-react';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow
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
}

const ProductTypesPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [typeToActOn, setTypeToActOn] = useState<ProductType | null>(null);

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
    if (location.state?.success) {
      setToast({ message: location.state.success, type: 'success' });
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [fetchProductTypes, signal, location, setToast, navigate]);

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
              Add New Type
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
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {productTypes.length === 0 ? (
                <AdminDataTableEmptyRow colSpan={3}>No product types found. Get started by creating your first one.</AdminDataTableEmptyRow>
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
                    <AdminDataTableActionCell>
                      {type.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/product-types/*/activate">
                          <AdminDataTableActionButton onClick={() => handleAction(type.id, 'activate')} tone="success" size="compact" title="Activate" aria-label={`Activate ${type.name}`}>
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            Activate
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {type.status !== 'ARCHIVED' && (
                        <>
                          <HasPermission action="PUT" path="/api/v1/product-types/*">
                            <AdminDataTableActionButton onClick={() => navigate(`/product-types/edit/${type.id}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${type.name}`}>
                              <Edit2 className="h-3.5 w-3.5" />
                              Edit
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="DELETE" path="/api/v1/product-types/*">
                            <AdminDataTableActionButton onClick={() => triggerConfirmAction(type)} tone="danger" size="compact" title={type.status === 'DRAFT' ? "Delete" : "Archive"} aria-label={`${type.status === 'DRAFT' ? 'Delete' : 'Archive'} ${type.name}`}>
                              <Trash2 className="h-3.5 w-3.5" />
                              {type.status === 'DRAFT' ? "Delete" : "Archive"}
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
