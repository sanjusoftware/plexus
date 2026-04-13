import React, { useCallback, useEffect, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Bookmark, Loader2, Plus } from 'lucide-react';
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
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

interface ProductCategory {
  id: number;
  code: string;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

const ProductCategoriesPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const signal = useAbortSignal();

  const [loading, setLoading] = useState(true);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [categoryToDelete, setCategoryToDelete] = useState<ProductCategory | null>(null);
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const fetchCategories = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/product-categories', { signal: abortSignal });
      setCategories(Array.isArray(response.data) ? response.data : []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: err.response?.data?.message || 'Failed to fetch product categories.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchCategories(signal);
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (successMessage && consumedSuccessKeyRef.current !== location.key) {
      consumedSuccessKeyRef.current = location.key;
      setToast({ message: successMessage, type: 'success' });
    }
  }, [fetchCategories, signal, location, setToast]);

  const handleDelete = async () => {
    if (!categoryToDelete) return;

    try {
      await axios.delete(`/api/v1/product-categories/${categoryToDelete.id}`);
      setToast({ message: 'Product category deleted successfully.', type: 'success' });
      await fetchCategories(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Failed to delete product category.', type: 'error' });
    } finally {
      setShowConfirmModal(false);
      setCategoryToDelete(null);
    }
  };

  const triggerDelete = (category: ProductCategory) => {
    setCategoryToDelete(category);
    setShowConfirmModal(true);
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Bookmark}
        title="Product Categories"
        description="Manage bank-defined product category master data."
        actions={
          <HasPermission permission="catalog:product-category:create">
            <button
              onClick={() => navigate('/product-categories/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" />
              New Category
            </button>
          </HasPermission>
        }
      />

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product categories table">
          <thead>
            <tr>
              <th>Category Details</th>
              <th>Updated At</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {categories.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={3}>No product categories found. Get started by creating your first one.</AdminDataTableEmptyRow>
            ) : (
              categories.map((category) => (
                <AdminDataTableRow key={category.id}>
                  <td className="whitespace-nowrap max-w-[250px]">
                    <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={category.name}>{category.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={category.code}>{category.code}</div>
                  </td>
                  <AuditTimestampCell value={category.updatedAt || category.createdAt} />
                  <AdminDataTableActionCell>
                    <HasPermission permission="catalog:product-category:update">
                      <AdminDataTableActionButton onClick={() => navigate(`/product-categories/edit/${category.id}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${category.name}`}>
                        <AdminDataTableActionContent action="edit" />
                      </AdminDataTableActionButton>
                    </HasPermission>
                    <HasPermission permission="catalog:product-category:delete">
                      <AdminDataTableActionButton onClick={() => triggerDelete(category)} tone="danger" size="compact" title="Delete" aria-label={`Delete ${category.name}`}>
                        <AdminDataTableActionContent action="delete" />
                      </AdminDataTableActionButton>
                    </HasPermission>
                  </AdminDataTableActionCell>
                </AdminDataTableRow>
              ))
            )}
          </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={showConfirmModal}
        onClose={() => { setShowConfirmModal(false); setCategoryToDelete(null); }}
        onConfirm={handleDelete}
        title="Confirm Deletion"
        message="Are you sure you want to permanently delete this product category? This action cannot be undone."
        confirmText="Confirm & Delete"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductCategoriesPage;
