import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, List, CheckCircle2 } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
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

  const fetchProductTypes = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/product-types');
      setProductTypes(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch product types. Make sure you have the required permissions.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProductTypes();
  }, []);

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
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex justify-between items-center bg-white p-6 rounded-2xl shadow-sm border">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center">
            <List className="w-6 h-6 mr-3 text-blue-600" /> Product Types
          </h1>
          <p className="text-gray-500 text-sm mt-1">Define broad categories for your bank's products (e.g., SAVINGS, LOANS).</p>
        </div>
        <HasPermission action="POST" path="/api/v1/product-types">
          <button
            onClick={() => navigate('/product-types/create')}
            className="bg-blue-600 text-white px-5 py-2.5 rounded-xl flex items-center hover:bg-blue-700 transition font-bold shadow-lg shadow-blue-100"
          >
            <Plus className="w-5 h-5 mr-2" /> Add New Type
          </button>
        </HasPermission>
      </div>

      {loading ? (
        <div className="flex justify-center p-24 bg-white rounded-2xl border"><Loader2 className="w-10 h-10 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-2xl shadow-sm border overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-8 py-4 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Code</th>
                <th className="px-8 py-4 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Name</th>
                <th className="px-8 py-4 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Status</th>
                <th className="px-8 py-4 text-right text-xs font-bold text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-100">
              {productTypes.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-8 py-12 text-center text-gray-500 italic">No product types found. Get started by creating your first one.</td>
                </tr>
              ) : (
                productTypes.map((type) => (
                  <tr key={type.id} className="hover:bg-gray-50/50 transition">
                    <td className="px-8 py-5 whitespace-nowrap text-sm font-mono font-bold text-blue-700">{type.code}</td>
                    <td className="px-8 py-5 whitespace-nowrap text-sm text-gray-900 font-medium">{type.name}</td>
                    <td className="px-8 py-5 whitespace-nowrap">
                      <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${
                        type.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                        type.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                        type.status === 'ARCHIVED' ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'
                      }`}>
                        {type.status}
                      </span>
                    </td>
                    <td className="px-8 py-5 whitespace-nowrap text-right text-sm font-medium space-x-2">
                      {type.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/product-types/*/activate">
                          <button onClick={() => handleAction(type.id, 'activate')} className="bg-green-100 text-green-700 px-3 py-1.5 rounded-lg text-xs font-bold hover:bg-green-200 transition flex items-center inline-flex" title="Activate">
                            <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Activate
                          </button>
                        </HasPermission>
                      )}
                      {type.status !== 'ARCHIVED' && (
                        <>
                          <HasPermission action="PUT" path="/api/v1/product-types/*">
                            <button onClick={() => navigate(`/product-types/edit/${type.id}`)} className="text-blue-600 hover:bg-blue-50 p-2 rounded-lg transition" title="Edit"><Edit2 className="w-4 h-4" /></button>
                          </HasPermission>
                          <HasPermission action="DELETE" path="/api/v1/product-types/*">
                            <button onClick={() => triggerConfirmAction(type)} className="text-red-600 hover:bg-red-50 p-2 rounded-lg transition" title={type.status === 'DRAFT' ? "Delete" : "Archive"}><Trash2 className="w-4 h-4" /></button>
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
    </div>
  );
};

export default ProductTypesPage;
