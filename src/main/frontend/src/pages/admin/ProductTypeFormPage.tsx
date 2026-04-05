import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, X } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';

const ProductTypeFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const isEditing = !!id;

  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ name: '', code: '' });
  const [isCodeEdited, setIsCodeEdited] = useState(false);

  useEffect(() => {
    if (isEditing) {
      const fetchType = async () => {
        try {
          const response = await axios.get('/api/v1/product-types');
          const type = response.data.find((t: any) => t.id.toString() === id);
          if (type) {
            setFormData({ name: type.name, code: type.code });
            setEntityName(type.name);
            setIsCodeEdited(true);
          } else {
            setToast({ message: 'Product type not found.', type: 'error' });
          }
        } catch (err: any) {
          setToast({ message: 'Failed to fetch product type.', type: 'error' });
        } finally {
          setLoading(false);
        }
      };
      fetchType();
    }
  }, [id, isEditing, setEntityName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/product-types/${id}`, formData);
      } else {
        await axios.post('/api/v1/product-types', formData);
      }
      setToast({ message: isEditing ? 'Product type updated successfully.' : 'Product type created successfully.', type: 'success' });
      navigate('/product-types');
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'An error occurred while saving.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    const isDirty = isEditing
      ? formData.name !== '' // simplified dirty check for now
      : formData.name !== '' || formData.code !== '';

    if (isDirty && !window.confirm('You will lose unsaved changes. Are you sure?')) {
      return;
    }
    navigate('/product-types');
  };

  if (loading) {
    return (
      <div className="flex justify-center p-32">
        <Loader2 className="w-16 h-16 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="relative">
          <h1 className="text-xl font-bold text-gray-900 tracking-tight uppercase">
            {isEditing ? 'Edit' : 'New'} Product Type
          </h1>
          <p className="text-gray-500 font-bold mt-0.5 uppercase tracking-widest text-[10px]">
            Define categories for bank products.
          </p>
        </div>
        <button
          onClick={handleCancel}
          className="bg-gray-50 text-gray-400 p-2 rounded-xl hover:bg-gray-100 transition relative border border-gray-100 shadow-sm"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100 p-6">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-1.5">Display Name</label>
            <input
              type="text"
              required
              className="block w-full border border-gray-200 rounded-xl p-2.5 font-bold text-gray-900 text-sm transition focus:border-blue-500 shadow-sm"
              value={formData.name}
              onChange={(e) => {
                const name = e.target.value;
                let code = formData.code;
                if (!isEditing && !isCodeEdited) {
                  code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                }
                setFormData({ ...formData, name, code });
              }}
              placeholder="e.g. Savings Accounts"
            />
            <p className="mt-1.5 text-[10px] text-gray-400 font-medium italic">The user-friendly name displayed in reports and customer interfaces.</p>
          </div>
          <div>
            <label className="block text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-1.5">Unique Code</label>
            <input
              type="text"
              required
              className="block w-full border border-gray-200 rounded-xl p-2.5 font-mono font-bold text-blue-700 text-sm transition focus:border-blue-500 shadow-sm"
              value={formData.code}
              onChange={(e) => {
                setIsCodeEdited(true);
                setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
              }}
              placeholder="e.g. SAVINGS"
            />
            <p className="mt-1.5 text-[10px] text-gray-400 font-medium italic">The immutable identifier used for API calls and system logic.</p>
          </div>
          <div className="pt-4 flex space-x-3">
            <button type="button" onClick={handleCancel} className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl font-bold text-gray-500 hover:bg-gray-50 transition uppercase tracking-widest text-[10px]">Cancel</button>
            <button type="submit" disabled={submitting} className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition flex items-center justify-center shadow-lg shadow-blue-100 uppercase tracking-widest text-[10px] disabled:opacity-50">
              {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
              Save Type
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProductTypeFormPage;
