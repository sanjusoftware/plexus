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
  const [violations, setViolations] = useState<any[]>([]);
  const [formData, setFormData] = useState({
    attributeKey: '',
    displayName: '',
    dataType: 'STRING',
    sourceType: 'CUSTOM_ATTRIBUTE',
    sourceField: ''
  });
  const [isKeyEdited, setIsKeyEdited] = useState(false);

  const dataTypes = ['STRING', 'DECIMAL', 'INTEGER', 'LONG', 'BOOLEAN', 'DATE'];
  const sourceTypes = ['CUSTOM_ATTRIBUTE', 'FACT_FIELD'];
  const signal = useAbortSignal();

  const normalizeIdentifier = (value: string) => value
    .trim()
    .replace(/\s+(.)/g, (_, char: string) => char.toUpperCase())
    .replace(/\s+/g, '')
    .replace(/[^a-zA-Z0-9_]/g, '');

  useEffect(() => {
    if (isEditing) {
      const fetchMetadata = async () => {
        try {
          const response = await axios.get('/api/v1/pricing-metadata', { signal });
          const meta = response.data.find((m: any) => m.attributeKey === attributeKey);
          if (meta) {
            setFormData({
              attributeKey: meta.attributeKey,
              displayName: meta.displayName,
              dataType: meta.dataType,
              sourceType: meta.sourceType || 'CUSTOM_ATTRIBUTE',
              sourceField: meta.sourceField || meta.attributeKey
            });
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

  const clearViolation = (field: string) => {
    setViolations(prev => prev.filter(v => v.field !== field));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setViolations([]);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/pricing-metadata/${attributeKey}`, formData);
      } else {
        await axios.post('/api/v1/pricing-metadata', formData);
      }
      navigate('/pricing-metadata', {
        state: { success: isEditing ? 'Metadata updated successfully.' : 'Metadata registered successfully.' }
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
    const isDirty = isEditing
      ? formData.displayName !== ''
      : formData.attributeKey !== '' || formData.displayName !== '' || formData.dataType !== 'STRING' || formData.sourceType !== 'CUSTOM_ATTRIBUTE' || formData.sourceField !== '';

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
                  key = normalizeIdentifier(name);
                }
                setFormData({
                  ...formData,
                  displayName: name,
                  attributeKey: key,
                  sourceField: formData.sourceType === 'CUSTOM_ATTRIBUTE' && !isKeyEdited ? key : formData.sourceField
                });
                    clearViolation('displayName');
              }}
              placeholder="e.g. Account Balance"
            />
            {renderViolations('displayName')}
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
                const normalizedKey = normalizeIdentifier(e.target.value);
                setIsKeyEdited(true);
                setFormData({
                  ...formData,
                  attributeKey: normalizedKey,
                  sourceField: formData.sourceType === 'CUSTOM_ATTRIBUTE' ? normalizedKey : formData.sourceField
                });
                clearViolation('attributeKey');
              }}
              placeholder="e.g. customerSegment or available_balance"
            />
            {renderViolations('attributeKey')}
            {!isEditing && <p className="admin-help">This is the exact attribute key business users will select in pricing rules. Must be unique.</p>}
          </div>
          <div className="admin-field">
            <label className="admin-label">Attribute Data Type</label>
            <PlexusSelect
              required
              options={dataTypes.map(type => ({ value: type, label: type }))}
              value={dataTypes.includes(formData.dataType) ? { value: formData.dataType, label: formData.dataType } : null}
              onChange={(opt) => {
                setFormData({ ...formData, dataType: opt ? opt.value : 'STRING' });
                clearViolation('dataType');
              }}
            />
            {renderViolations('dataType')}
            <p className="admin-help">Affects how the rule engine processes and validates values.</p>
          </div>
          <div className="admin-field">
            <label className="admin-label">Attribute Source</label>
            <PlexusSelect
              required
              options={sourceTypes.map(type => ({ value: type, label: type === 'FACT_FIELD' ? 'Request / Fact Field' : 'Custom Attribute' }))}
              value={sourceTypes.includes(formData.sourceType) ? { value: formData.sourceType, label: formData.sourceType === 'FACT_FIELD' ? 'Request / Fact Field' : 'Custom Attribute' } : null}
              onChange={(opt) => {
                const sourceType = opt ? opt.value : 'CUSTOM_ATTRIBUTE';
                setFormData({
                  ...formData,
                  sourceType,
                  sourceField: sourceType === 'CUSTOM_ATTRIBUTE' ? (formData.sourceField || formData.attributeKey) : formData.sourceField
                });
                clearViolation('sourceType');
              }}
            />
            {renderViolations('sourceType')}
            <p className="admin-help">Choose whether this attribute reads from a top-level pricing request field or from customAttributes.</p>
          </div>
          <div className="admin-field">
            <label className="admin-label">Underlying Source Field</label>
            <input
              type="text"
              required
              className="admin-input admin-input-mono"
              value={formData.sourceField}
              onChange={(e) => {
                setFormData({ ...formData, sourceField: normalizeIdentifier(e.target.value) });
                clearViolation('sourceField');
              }}
              placeholder={formData.sourceType === 'FACT_FIELD' ? 'e.g. customerSegment' : 'Defaults to attribute key if blank'}
            />
            {renderViolations('sourceField')}
            <p className="admin-help">For request fields, use the exact pricing fact field name. For custom attributes, this is usually the same as the attribute key.</p>
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
