import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, Database } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import PlexusSelect from '../../components/PlexusSelect';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

const PricingMetadataFormPage = () => {
  const { attributeKey } = useParams<{ attributeKey?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const isEditing = !!attributeKey;

  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ attributeKey: '', displayName: '', dataType: 'STRING' });
  const [isKeyEdited, setIsKeyEdited] = useState(false);

  const dataTypes = ['STRING', 'DECIMAL', 'INTEGER', 'BOOLEAN', 'DATE'];
  const signal = useAbortSignal();

  useEffect(() => {
    if (isEditing) {
      const fetchMetadata = async () => {
        try {
          const response = await axios.get('/api/v1/pricing-metadata', { signal });
          const meta = response.data.find((m: any) => m.attributeKey === attributeKey);
          if (meta) {
            setFormData({ attributeKey: meta.attributeKey, displayName: meta.displayName, dataType: meta.dataType });
            setEntityName(meta.displayName);
            setIsKeyEdited(true);
          }
        } catch (err: any) {
          if (axios.isCancel(err)) return;
          setToast({ message: 'Failed to fetch pricing metadata.', type: 'error' });
        } finally {
          if (!signal.aborted) {
            setLoading(false);
          }
        }
      };
      fetchMetadata();
    }
  }, [attributeKey, isEditing, setEntityName, setToast, signal]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/pricing-metadata/${attributeKey}`, formData);
      } else {
        await axios.post('/api/v1/pricing-metadata', formData);
      }
      setToast({ message: isEditing ? 'Metadata updated successfully.' : 'Metadata registered successfully.', type: 'success' });
      navigate('/pricing-metadata');
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'An error occurred while saving.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    const isDirty = isEditing
      ? formData.displayName !== '' // simplified dirty check
      : formData.attributeKey !== '' || formData.displayName !== '' || formData.dataType !== 'STRING';

    if (isDirty && !window.confirm('You will lose unsaved changes. Are you sure?')) {
      return;
    }
    navigate('/pricing-metadata');
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
        icon={Database}
        title={`${isEditing ? 'Edit' : 'Register'} Attribute`}
        description="Define pricing calculation inputs."
        onClose={handleCancel}
      />

      <div className="admin-form">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="admin-field">
            <label className="admin-label">Friendly Display Name</label>
            <input
              type="text"
              required
              className="admin-input"
              value={formData.displayName}
              onChange={(e) => {
                const name = e.target.value;
                let key = formData.attributeKey;
                if (!isEditing && !isKeyEdited) {
                  key = name.toLowerCase().trim().replace(/\s+/g, '_').replace(/[^a-z0-9_-]/g, '');
                }
                setFormData({ ...formData, displayName: name, attributeKey: key });
              }}
              placeholder="e.g. Account Balance"
            />
            <p className="admin-help">What the business user sees in the pricing dashboard.</p>
          </div>
          <div className="admin-field">
            <label className="admin-label">Internal Attribute Key</label>
            <input
              type="text"
              required
              disabled={isEditing}
              className={`admin-input admin-input-mono ${isEditing ? 'cursor-not-allowed bg-gray-50' : ''}`}
              value={formData.attributeKey}
              onChange={(e) => {
                setIsKeyEdited(true);
                setFormData({ ...formData, attributeKey: e.target.value.toLowerCase().replace(/\s/g, '_').replace(/[^a-z0-9_-]/g, '') });
              }}
              placeholder="e.g. current_balance"
            />
            {!isEditing && <p className="admin-help">Used by developers in rule definitions. Must be unique.</p>}
          </div>
          <div className="admin-field">
            <label className="admin-label">Attribute Data Type</label>
            <PlexusSelect
              required
              options={dataTypes.map(type => ({ value: type, label: type }))}
              value={dataTypes.includes(formData.dataType) ? { value: formData.dataType, label: formData.dataType } : null}
              onChange={(opt) => setFormData({ ...formData, dataType: opt ? opt.value : 'STRING' })}
            />
            <p className="admin-help">Affects how the rule engine processes and validates values.</p>
          </div>
          <div className="admin-actions">
            <button type="button" onClick={handleCancel} className="admin-secondary-btn sm:min-w-[140px]">Cancel</button>
            <button type="submit" disabled={submitting} className="admin-primary-btn sm:min-w-[170px] sm:justify-center">
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save Metadata
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default PricingMetadataFormPage;
