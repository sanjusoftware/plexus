import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, X } from 'lucide-react';
import PlexusSelect from '../../components/PlexusSelect';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';

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

  useEffect(() => {
    if (isEditing) {
      const fetchMetadata = async () => {
        try {
          const response = await axios.get('/api/v1/pricing-metadata');
          const meta = response.data.find((m: any) => m.attributeKey === attributeKey);
          setFormData({ attributeKey: meta.attributeKey, displayName: meta.displayName, dataType: meta.dataType });
          setEntityName(meta.displayName);
          setIsKeyEdited(true);
        } catch (err: any) {
          setToast({ message: 'Failed to fetch pricing metadata.', type: 'error' });
        } finally {
          setLoading(false);
        }
      };
      fetchMetadata();
    }
  }, [attributeKey, isEditing, setEntityName]);

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
            {isEditing ? 'Edit' : 'Register'} Attribute
          </h1>
          <p className="text-gray-500 font-bold mt-0.5 uppercase tracking-widest text-[10px]">
            Define pricing calculation inputs.
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
            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Friendly Display Name</label>
            <input
              type="text"
              required
              className="block w-full border border-gray-200 rounded-xl p-2.5 font-bold text-sm text-gray-900 transition focus:border-blue-500 shadow-sm"
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
            <p className="mt-1.5 text-[10px] text-gray-400 font-medium italic">What the business user sees in the pricing dashboard.</p>
          </div>
          <div>
            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Internal Attribute Key</label>
            <input
              type="text"
              required
              disabled={isEditing}
              className={`block w-full border border-gray-200 rounded-xl p-2.5 font-mono font-bold text-blue-700 text-sm transition focus:border-blue-500 shadow-sm ${isEditing ? 'bg-gray-50 cursor-not-allowed' : ''}`}
              value={formData.attributeKey}
              onChange={(e) => {
                setIsKeyEdited(true);
                setFormData({ ...formData, attributeKey: e.target.value.toLowerCase().replace(/\s/g, '_').replace(/[^a-z0-9_-]/g, '') });
              }}
              placeholder="e.g. current_balance"
            />
            {!isEditing && <p className="mt-1.5 text-[10px] text-gray-400 font-medium italic">Used by developers in rule definitions. Must be unique.</p>}
          </div>
          <div>
            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Attribute Data Type</label>
            <PlexusSelect
              required
              options={dataTypes.map(type => ({ value: type, label: type }))}
              value={dataTypes.includes(formData.dataType) ? { value: formData.dataType, label: formData.dataType } : null}
              onChange={(opt) => setFormData({ ...formData, dataType: opt ? opt.value : 'STRING' })}
            />
            <p className="mt-1.5 text-[10px] text-gray-400 font-medium italic">Affects how the rule engine processes and validates values.</p>
          </div>
          <div className="pt-4 flex space-x-3">
            <button type="button" onClick={handleCancel} className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl font-bold text-gray-500 hover:bg-gray-50 transition uppercase tracking-widest text-[10px]">Cancel</button>
            <button type="submit" disabled={submitting} className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition flex items-center justify-center shadow-lg shadow-blue-100 uppercase tracking-widest text-[10px] disabled:opacity-50">
              {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
              Save Metadata
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PricingMetadataFormPage;
