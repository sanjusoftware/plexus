import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Save, X, List, CheckCircle2, Archive, AlertCircle } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';

interface ProductType {
  id: number;
  name: string;
  code: string;
  status: string;
}

const ProductTypesPage = () => {
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<ProductType | null>(null);
  const [formData, setFormData] = useState({ name: '', code: '' });
  const [isCodeEdited, setIsCodeEdited] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [typeToActOn, setTypeToActOn] = useState<ProductType | null>(null);

  const fetchProductTypes = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/product-types');
      setProductTypes(response.data);
    } catch (err: any) {
      setError('Failed to fetch product types. Make sure you have the required permissions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProductTypes();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      if (editingType) {
        await axios.put(`/api/v1/product-types/${editingType.id}`, formData);
        setSuccess('Product type updated successfully.');
      } else {
        await axios.post('/api/v1/product-types', formData);
        setSuccess('Product type created successfully.');
      }
      setIsModalOpen(false);
      setEditingType(null);
      setFormData({ name: '', code: '' });
      fetchProductTypes();
    } catch (err: any) {
      setError(err.response?.data?.message || 'An error occurred while saving.');
    }
  };

  const handleAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/product-types/${id}/${action}`);
      setSuccess(`Product type ${action}d successfully.`);
      fetchProductTypes();
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to ${action} product type.`);
    }
  };

  const handleConfirmAction = async () => {
    if (!typeToActOn) return;

    try {
      if (typeToActOn.status === 'DRAFT') {
        await axios.delete(`/api/v1/product-types/${typeToActOn.id}`);
        setSuccess('Product type deleted successfully.');
      } else if (typeToActOn.status === 'ACTIVE') {
        await axios.post(`/api/v1/product-types/${typeToActOn.id}/archive`);
        setSuccess('Product type archived successfully.');
      }
      fetchProductTypes();
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to ${typeToActOn.status === 'DRAFT' ? 'delete' : 'archive'} product type.`);
    } finally {
      setShowConfirmModal(false);
      setTypeToActOn(null);
    }
  };

  const triggerConfirmAction = (type: ProductType) => {
    setTypeToActOn(type);
    setShowConfirmModal(true);
  };

  const openModal = (type?: ProductType) => {
    if (type) {
      setEditingType(type);
      setFormData({ name: type.name, code: type.code });
      setIsCodeEdited(true);
    } else {
      setEditingType(null);
      setFormData({ name: '', code: '' });
      setIsCodeEdited(false);
    }
    setIsModalOpen(true);
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
        <button
          onClick={() => openModal()}
          className="bg-blue-600 text-white px-5 py-2.5 rounded-xl flex items-center hover:bg-blue-700 transition font-bold shadow-lg shadow-blue-100"
        >
          <Plus className="w-5 h-5 mr-2" /> Add New Type
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-r-xl flex items-center animate-in fade-in slide-in-from-top-1">
          <AlertCircle className="w-5 h-5 text-red-500 mr-3" />
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}
      {success && (
        <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded-r-xl flex items-center animate-in fade-in slide-in-from-top-1">
          <CheckCircle2 className="w-5 h-5 text-green-500 mr-3" />
          <p className="text-sm text-green-700">{success}</p>
        </div>
      )}

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
                        <button onClick={() => handleAction(type.id, 'activate')} className="bg-green-100 text-green-700 px-3 py-1.5 rounded-lg text-xs font-bold hover:bg-green-200 transition flex items-center inline-flex" title="Activate">
                          <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Activate
                        </button>
                      )}
                      {type.status === 'ACTIVE' && (
                        <button onClick={() => triggerConfirmAction(type)} className="text-gray-400 hover:bg-gray-100 p-2 rounded-lg transition" title="Archive"><Archive className="w-4 h-4" /></button>
                      )}
                      {type.status !== 'ARCHIVED' && (
                        <>
                          <button onClick={() => openModal(type)} className="text-blue-600 hover:bg-blue-50 p-2 rounded-lg transition" title="Edit"><Edit2 className="w-4 h-4" /></button>
                          <button onClick={() => triggerConfirmAction(type)} className="text-red-600 hover:bg-red-50 p-2 rounded-lg transition" title={type.status === 'DRAFT' ? "Delete" : "Archive"}><Trash2 className="w-4 h-4" /></button>
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

      {isModalOpen && (
        <div className="fixed inset-0 bg-black bg-opacity-40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl p-8 max-w-md w-full shadow-2xl animate-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center mb-6 border-b pb-4">
              <h2 className="text-xl font-bold text-gray-900">{editingType ? 'Edit' : 'New'} Product Type</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600 transition p-2 hover:bg-gray-100 rounded-full"><X className="w-6 h-6" /></button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-6">
              {error && (
                <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
                  <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
                  <p className="text-xs font-bold">{error}</p>
                </div>
              )}
              <div>
                <label className="block text-xs font-black text-gray-500 uppercase tracking-widest mb-2">Display Name</label>
                <input
                  type="text"
                  required
                  className="block w-full border border-gray-200 rounded-xl shadow-sm p-3.5 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition"
                  value={formData.name}
                  onChange={(e) => {
                    const name = e.target.value;
                    let code = formData.code;
                    if (!editingType && !isCodeEdited) {
                      code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                    }
                    setFormData({ ...formData, name, code });
                  }}
                  placeholder="e.g. Savings Accounts"
                />
                <p className="mt-2 text-[10px] text-gray-400 leading-relaxed italic">The user-friendly name displayed in reports and customer interfaces.</p>
              </div>
              <div>
                <label className="block text-xs font-black text-gray-500 uppercase tracking-widest mb-2">Unique Code</label>
                <input
                  type="text"
                  required
                  className="block w-full border border-gray-200 rounded-xl shadow-sm p-3.5 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 font-mono transition"
                  value={formData.code}
                  onChange={(e) => {
                    setIsCodeEdited(true);
                    setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                  }}
                  placeholder="e.g. SAVINGS"
                />
                <p className="mt-2 text-[10px] text-gray-400 leading-relaxed italic">The immutable identifier used for API calls and system logic.</p>
              </div>
              <div className="pt-4 flex space-x-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 px-4 py-3.5 border border-gray-200 rounded-2xl font-bold text-gray-600 hover:bg-gray-50 transition">Cancel</button>
                <button type="submit" className="flex-1 px-4 py-3.5 bg-blue-600 text-white rounded-2xl font-bold hover:bg-blue-700 transition flex items-center justify-center shadow-lg shadow-blue-100">
                  <Save className="w-5 h-5 mr-2" /> Save Type
                </button>
              </div>
            </form>
          </div>
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
