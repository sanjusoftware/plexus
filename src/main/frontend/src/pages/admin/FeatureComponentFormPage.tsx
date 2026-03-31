import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, X, AlertCircle } from 'lucide-react';
import StyledSelect from '../../components/StyledSelect';
import { useBreadcrumb } from '../../context/BreadcrumbContext';

const FeatureComponentFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const isEditing = !!id;

  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ code: '', name: '', dataType: 'STRING' });
  const [isCodeEdited, setIsCodeEdited] = useState(false);
  const [error, setError] = useState('');

  const dataTypes = ['STRING', 'INTEGER', 'BOOLEAN', 'DECIMAL', 'DATE'];

  useEffect(() => {
    if (isEditing) {
      const fetchFeature = async () => {
        try {
          const response = await axios.get(`/api/v1/features/${id}`);
          const feat = response.data;
          setFormData({ code: feat.code, name: feat.name, dataType: feat.dataType });
          setEntityName(feat.name);
          setIsCodeEdited(true);
        } catch (err: any) {
          setError('Failed to fetch feature component.');
        } finally {
          setLoading(false);
        }
      };
      fetchFeature();
    }
  }, [id, isEditing, setEntityName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/features/${id}`, formData);
      } else {
        await axios.post('/api/v1/features', formData);
      }
      navigate('/features');
    } catch (err: any) {
      setError(err.response?.data?.message || 'An error occurred while saving.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate('/features');
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
            {isEditing ? 'Edit' : 'Register'} Feature
          </h1>
          <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">
            Define reusable product features.
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
          {error && (
            <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
              <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
              <p className="text-xs font-bold">{error}</p>
            </div>
          )}
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Internal Feature Code</label>
            <input
              type="text"
              required
              disabled={isEditing}
              className={`block w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-bold text-blue-700 transition focus:border-blue-500 shadow-sm ${isEditing ? 'bg-gray-50 cursor-not-allowed' : ''}`}
              value={formData.code}
              onChange={(e) => {
                setIsCodeEdited(true);
                setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_').replace(/[^A-Z0-9_-]/g, '') });
              }}
              placeholder="e.g. LOUNGE_ACCESS"
            />
            {!isEditing && <p className="mt-3 text-[11px] text-gray-400 font-medium italic">Unique business code for the feature.</p>}
          </div>
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Display Name</label>
            <input
              type="text"
              required
              className="block w-full border-2 border-gray-100 rounded-2xl p-4 font-bold text-gray-900 transition focus:border-blue-500 shadow-sm"
              value={formData.name}
              onChange={(e) => {
                const name = e.target.value;
                let code = formData.code;
                if (!isEditing && !isCodeEdited) {
                  code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                }
                setFormData({ ...formData, name: name, code: code });
              }}
              placeholder="e.g. Airport Lounge Access"
            />
          </div>
          <div>
            <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Data Type</label>
            <StyledSelect
              required
              value={formData.dataType}
              onChange={(e) => setFormData({ ...formData, dataType: e.target.value })}
            >
              {dataTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </StyledSelect>
          </div>
          <div className="pt-6 flex space-x-4">
            <button type="button" onClick={handleCancel} className="flex-1 px-4 py-4 border-2 border-gray-100 rounded-2xl font-black text-gray-500 hover:bg-gray-50 transition uppercase tracking-widest text-xs">Cancel</button>
            <button type="submit" disabled={submitting} className="flex-1 px-4 py-4 bg-blue-600 text-white rounded-2xl font-black hover:bg-blue-700 transition flex items-center justify-center shadow-2xl shadow-blue-200 uppercase tracking-widest text-xs disabled:opacity-50">
              {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
              Save Feature
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default FeatureComponentFormPage;
