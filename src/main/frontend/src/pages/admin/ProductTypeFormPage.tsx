import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, List } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

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
  const signal = useAbortSignal();

  useEffect(() => {
    if (isEditing) {
      const fetchType = async () => {
        try {
          const response = await axios.get('/api/v1/product-types', { signal });
          const type = response.data.find((t: any) => t.id.toString() === id);
          if (type) {
            setFormData({ name: type.name, code: type.code });
            setEntityName(type.name);
            setIsCodeEdited(true);
          } else {
            setToast({ message: 'Product type not found.', type: 'error' });
          }
        } catch (err: any) {
          if (axios.isCancel(err)) return;
          setToast({ message: 'Failed to fetch product type.', type: 'error' });
        } finally {
          if (!signal.aborted) {
            setLoading(false);
          }
        }
      };
      fetchType();
    }
  }, [id, isEditing, setEntityName, setToast, signal]);

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
      <div className="flex justify-center p-20">
        <Loader2 className="h-12 w-12 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <AdminPage width="narrow">
      <AdminFormHeader
        icon={List}
        title={`${isEditing ? 'Edit' : 'New'} Product Type`}
        description="Define categories for bank products."
        onClose={handleCancel}
      />

      <div className="admin-form">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="admin-field">
            <label className="admin-label">Display Name</label>
            <input
              type="text"
              required
              className="admin-input"
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
            <p className="admin-help">The user-friendly name displayed in reports and customer interfaces.</p>
          </div>
          <div className="admin-field">
            <label className="admin-label">Unique Code</label>
            <input
              type="text"
              required
              className="admin-input admin-input-mono"
              value={formData.code}
              onChange={(e) => {
                setIsCodeEdited(true);
                setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
              }}
              placeholder="e.g. SAVINGS"
            />
            <p className="admin-help">The immutable identifier used for API calls and system logic.</p>
          </div>
          <div className="admin-actions">
            <button type="button" onClick={handleCancel} className="admin-secondary-btn sm:min-w-[140px]">Cancel</button>
            <button type="submit" disabled={submitting} className="admin-primary-btn sm:min-w-[160px] sm:justify-center">
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save Type
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default ProductTypeFormPage;
