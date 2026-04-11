import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Save, ShieldCheck } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import PlexusSelect from '../../components/PlexusSelect';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard';

const FeatureComponentFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const isEditing = !!id;

  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [violations, setViolations] = useState<any[]>([]);
  const [formData, setFormData] = useState({ code: '', name: '', dataType: 'STRING' });
  const [isCodeEdited, setIsCodeEdited] = useState(false);

  const dataTypes = ['STRING', 'INTEGER', 'BOOLEAN', 'DECIMAL', 'DATE'];
  const signal = useAbortSignal();
  const { resetDirtyBaseline, confirmDiscardChanges } = useUnsavedChangesGuard(formData);

  useEffect(() => {
    if (isEditing) {
      const fetchFeature = async () => {
        try {
          const response = await axios.get(`/api/v1/features/${id}`, { signal });
          const feat = response.data;
          setFormData({ code: feat.code, name: feat.name, dataType: feat.dataType });
          resetDirtyBaseline({ code: feat.code, name: feat.name, dataType: feat.dataType });
          setEntityName(feat.name);
          setIsCodeEdited(true);
        } catch (err: any) {
          if (axios.isCancel(err)) return;
          setToast({ message: 'Failed to fetch feature component.', type: 'error' });
        } finally {
          if (!signal.aborted) {
            setLoading(false);
          }
        }
      };
      fetchFeature();
    }
  }, [id, isEditing, setEntityName, setToast, signal, resetDirtyBaseline]);

  const clearViolation = (field: string) => {
    setViolations(prev => prev.filter(v => v.field !== field));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setViolations([]);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/features/${id}`, formData);
      } else {
        await axios.post('/api/v1/features', formData);
      }
      navigate('/features', {
        state: {
          success: isEditing ? 'Feature updated successfully.' : 'Feature registered successfully.'
        }
      });
    } catch (err: any) {
      if (err.response?.status === 422 && err.response?.data?.errors) {
        setViolations(err.response.data.errors);
      }
      setToast({ message: err.response?.data?.message || 'An error occurred while saving.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    if (!confirmDiscardChanges()) {
      return;
    }
    navigate('/features');
  };

  if (loading) {
    return (
      <div className="flex justify-center p-20">
        <Loader2 className="h-12 w-12 animate-spin text-blue-600" />
      </div>
    );
  }

  const renderViolations = (field: string) => {
    return violations
      .filter(v => v.field === field)
      .map((v, i) => (
        <div key={i} className={v.severity === 'WARNING' ? 'admin-warning-text' : 'admin-error-text'}>
          {v.reason}
        </div>
      ));
  };

  return (
    <AdminPage width="narrow">
      <AdminFormHeader
        icon={ShieldCheck}
        title={`${isEditing ? 'Edit' : 'Register'} Feature`}
        description="Define reusable product features."
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
                setFormData({ ...formData, name: name, code: code });
                clearViolation('name');
              }}
              placeholder="e.g. Airport Lounge Access"
            />
            {renderViolations('name')}
          </div>
          <div className="admin-field">
            <label className="admin-label">Internal Feature Code</label>
            <input
              type="text"
              required
              disabled={isEditing}
              className={`admin-input admin-input-mono ${isEditing ? 'cursor-not-allowed bg-gray-50' : ''}`}
              value={formData.code}
              onChange={(e) => {
                setIsCodeEdited(true);
                setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_').replace(/[^A-Z0-9_-]/g, '') });
                clearViolation('code');
              }}
              placeholder="e.g. LOUNGE_ACCESS"
            />
            {renderViolations('code')}
            {!isEditing && <p className="admin-help">Unique business code for the feature.</p>}
          </div>
          <div className="admin-field">
            <label className="admin-label">Data Type</label>
            <PlexusSelect
              required
              options={dataTypes.map(type => ({ value: type, label: type }))}
              value={{ value: formData.dataType, label: formData.dataType }}
              onChange={(opt) => {
                setFormData({ ...formData, dataType: opt ? opt.value : 'STRING' });
                clearViolation('dataType');
              }}
            />
            {renderViolations('dataType')}
          </div>
          <div className="admin-actions">
            <button type="button" onClick={handleCancel} className="admin-secondary-btn sm:min-w-[140px]">Cancel</button>
            <button type="submit" disabled={submitting} className="admin-primary-btn sm:min-w-[160px] sm:justify-center">
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save Feature
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default FeatureComponentFormPage;
