import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, List, CheckCircle2 } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { useAuth } from '../../context/AuthContext';

interface ProductType {
  id: number;
  name: string;
  code: string;
  status: string;
}

const ProductTypesPage = () => {
  const navigate = useNavigate();
  const { setToast } = useAuth();
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [typeToActOn, setTypeToActOn] = useState<ProductType | null>(null);

  const fetchProductTypes = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/product-types');
      setProductTypes(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch product types. Make sure you have the required permissions.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [setToast]);

  useEffect(() => {
    fetchProductTypes();
  }, [fetchProductTypes]);

  const handleAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/product-types/${id}/${action}`);
      setToast({ message: `Product type ${action}d successfully.`, type: 'success' });
      fetchProductTypes();
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
      fetchProductTypes();
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
        <div className="admin-table-card">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Status</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {productTypes.length === 0 ? (
                <tr>
                  <td colSpan={4} className="admin-empty-state">No product types found. Get started by creating your first one.</td>
                </tr>
              ) : (
                productTypes.map((type) => (
                  <tr key={type.id} className="hover:bg-gray-50/50 transition">
                    <td className="whitespace-nowrap font-mono font-bold text-blue-700">{type.code}</td>
                    <td className="whitespace-nowrap text-sm font-medium text-gray-900">{type.name}</td>
                    <td className="whitespace-nowrap">
                      <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${
                        type.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                        type.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                        type.status === 'ARCHIVED' ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'
                      }`}>
                        {type.status}
                      </span>
                    </td>
                    <td className="whitespace-nowrap text-right text-xs font-medium space-x-2">
                      {type.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/product-types/*/activate">
                          <button onClick={() => handleAction(type.id, 'activate')} className="inline-flex items-center rounded-lg bg-green-100 px-2 py-1 text-[10px] font-bold text-green-700 transition hover:bg-green-200" title="Activate">
                            <CheckCircle2 className="mr-1 h-3 w-3" /> Activate
                          </button>
                        </HasPermission>
                      )}
                      {type.status !== 'ARCHIVED' && (
                        <>
                          <HasPermission action="PUT" path="/api/v1/product-types/*">
                            <button onClick={() => navigate(`/product-types/edit/${type.id}`)} className="rounded-lg p-1.5 text-blue-600 transition hover:bg-blue-50" title="Edit"><Edit2 className="h-3.5 w-3.5" /></button>
                          </HasPermission>
                          <HasPermission action="DELETE" path="/api/v1/product-types/*">
                            <button onClick={() => triggerConfirmAction(type)} className="rounded-lg p-1.5 text-red-600 transition hover:bg-red-50" title={type.status === 'DRAFT' ? "Delete" : "Archive"}><Trash2 className="h-3.5 w-3.5" /></button>
                          </HasPermission>
                        </>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
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
