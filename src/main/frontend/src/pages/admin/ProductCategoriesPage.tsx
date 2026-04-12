import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { FolderTree, Loader2, Plus } from 'lucide-react';
import {
  AdminDataTable,
  AdminDataTableEmptyRow,
  AdminDataTableRow,
  AuditTimestampCell
} from '../../components/AdminDataTable';
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
  const { setToast } = useAuth();
  const signal = useAbortSignal();

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [newCategory, setNewCategory] = useState({ code: '', name: '' });

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
  }, [fetchCategories, signal]);

  const handleCreate = async () => {
    const payload = {
      code: newCategory.code.toUpperCase().trim().replace(/\s+/g, '_'),
      name: newCategory.name.trim()
    };

    if (!payload.code || !payload.name) {
      setToast({ message: 'Category code and name are required.', type: 'error' });
      return;
    }

    setSubmitting(true);
    try {
      await axios.post('/api/v1/product-categories', payload);
      setToast({ message: 'Product category created successfully.', type: 'success' });
      setNewCategory({ code: '', name: '' });
      await fetchCategories(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Failed to create product category.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={FolderTree}
        title="Product Categories"
        description="Manage bank-defined product category master data."
      />

      <div className="admin-card mb-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input
            type="text"
            value={newCategory.code}
            onChange={(e) => setNewCategory((prev) => ({ ...prev, code: e.target.value }))}
            placeholder="Category Code (e.g. RETAIL)"
            className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-900 text-sm transition focus:border-blue-500 shadow-sm"
          />
          <input
            type="text"
            value={newCategory.name}
            onChange={(e) => setNewCategory((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Category Name"
            className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-900 text-sm transition focus:border-blue-500 shadow-sm"
          />
          <HasPermission action="POST" path="/api/v1/product-categories">
            <button
              type="button"
              onClick={handleCreate}
              disabled={submitting}
              className="admin-primary-btn justify-center"
            >
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              Create Category
            </button>
          </HasPermission>
        </div>
      </div>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product categories table">
          <thead>
            <tr>
              <th>Category</th>
              <th>Updated At</th>
            </tr>
          </thead>
          <tbody>
            {categories.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={2}>No product categories found. Create your first category above.</AdminDataTableEmptyRow>
            ) : (
              categories.map((category) => (
                <AdminDataTableRow key={category.id}>
                  <td className="whitespace-nowrap max-w-[250px]">
                    <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={category.name}>{category.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={category.code}>{category.code}</div>
                  </td>
                  <AuditTimestampCell value={category.updatedAt || category.createdAt} />
                </AdminDataTableRow>
              ))
            )}
          </tbody>
        </AdminDataTable>
      )}
    </AdminPage>
  );
};

export default ProductCategoriesPage;

