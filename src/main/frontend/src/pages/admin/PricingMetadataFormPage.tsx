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
    <div className="max-w-2xl mx-auto space-y-8">
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="absolute top-0 right-0 w-40 h-full bg-blue-50 -skew-x-12 translate-x-20 opacity-30"></div>
        <div className="relative">
          <h1 className="text-3xl font-black text-gray-900 tracking-tight uppercase">
            {isEditing ? 'Edit' : 'Register'} Attribute
          </h1>
          <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">
            Define pricing calculation inputs.
          </p>
        </div>
        <button
          onClick={handleCancel}
          className="bg-gray-50 text-gray-400 p-3 rounded-2xl hover:bg-gray-100 transition relative border border-gray-100 shadow-sm"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="bg-white rounded-[2.5rem] shadow-sm overflow-hidden border border-gray-100 p-10">
        <form onSubmit={handleSubmit} className="space-y-8">
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Friendly Display Name</label>
            <input
              type="text"
              required
              className="block w-full border-2 border-gray-100 rounded-2xl p-4 font-bold text-gray-900 transition focus:border-blue-500 shadow-sm"
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
            <p className="mt-3 text-[11px] text-gray-400 font-medium italic">What the business user sees in the pricing dashboard.</p>
          </div>
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Internal Attribute Key</label>
            <input
              type="text"
              required
              disabled={isEditing}
              className={`block w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-bold text-blue-700 transition focus:border-blue-500 shadow-sm ${isEditing ? 'bg-gray-50 cursor-not-allowed' : ''}`}
              value={formData.attributeKey}
              onChange={(e) => {
                setIsKeyEdited(true);
                setFormData({ ...formData, attributeKey: e.target.value.toLowerCase().replace(/\s/g, '_').replace(/[^a-z0-9_-]/g, '') });
              }}
              placeholder="e.g. current_balance"
            />
            {!isEditing && <p className="mt-3 text-[11px] text-gray-400 font-medium italic">Used by developers in rule definitions. Must be unique.</p>}
          </div>
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Attribute Data Type</label>
            <PlexusSelect
              required
              options={dataTypes.map(type => ({ value: type, label: type }))}
              value={dataTypes.includes(formData.dataType) ? { value: formData.dataType, label: formData.dataType } : null}
              onChange={(opt) => setFormData({ ...formData, dataType: opt ? opt.value : 'STRING' })}
            />
            <p className="mt-3 text-[11px] text-gray-400 font-medium italic">Affects how the rule engine processes and validates values.</p>
          </div>
          <div className="pt-6 flex space-x-4">
            <button type="button" onClick={handleCancel} className="flex-1 px-4 py-4 border-2 border-gray-100 rounded-2xl font-black text-gray-500 hover:bg-gray-50 transition uppercase tracking-widest text-xs">Cancel</button>
            <button type="submit" disabled={submitting} className="flex-1 px-4 py-4 bg-blue-600 text-white rounded-2xl font-black hover:bg-blue-700 transition flex items-center justify-center shadow-2xl shadow-blue-200 uppercase tracking-widest text-xs disabled:opacity-50">
              {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
              Save Metadata
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PricingMetadataFormPage;
