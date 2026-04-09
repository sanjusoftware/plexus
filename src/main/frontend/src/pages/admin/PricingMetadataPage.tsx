import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Database, Info } from 'lucide-react';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow
} from '../../components/AdminDataTable';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

interface PricingMetadata {
  id: number;
  attributeKey: string;
  displayName: string;
  dataType: string;
}

const PricingMetadataPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [metadata, setMetadata] = useState<PricingMetadata[]>([]);
  const [loading, setLoading] = useState(true);

  const signal = useAbortSignal();

  const fetchMetadata = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-metadata', { signal: abortSignal });
      setMetadata(response.data);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch pricing metadata. Check your permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchMetadata(signal);
    if (location.state?.success) {
      setToast({ message: location.state.success, type: 'success' });
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [fetchMetadata, signal, location, setToast, navigate]);

  const handleDelete = async (attributeKey: string) => {
    if (!window.confirm('Are you sure? Deleting metadata might break existing rules that use this attribute.')) return;
    try {
      await axios.delete(`/api/v1/pricing-metadata/${attributeKey}`);
      setToast({ message: 'Metadata deleted successfully.', type: 'success' });
      fetchMetadata(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Failed to delete.', type: 'error' });
    }
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Database}
        title="Pricing Input Metadata"
        description="Register attributes used in dynamic pricing calculation rules."
        actions={
          <HasPermission action="POST" path="/api/v1/pricing-metadata">
            <button
              onClick={() => navigate('/pricing-metadata/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Attribute
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Pro Tip" tone="amber">
        <span className="italic">The <strong>Attribute Key</strong> is critical. It must exactly match the key used in your Drools rules (for example <code>customer_segment</code>). The data type ensures the rule engine can correctly compare values.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Pricing metadata table">
            <thead>
              <tr>
                <th>Attribute Details</th>
                <th>Type</th>
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {metadata.length === 0 ? (
                <AdminDataTableEmptyRow colSpan={3}>No metadata registered. Rules engine will not recognize dynamic inputs.</AdminDataTableEmptyRow>
              ) : (
                metadata.map((meta) => (
                  <AdminDataTableRow key={meta.id}>
                    <td className="whitespace-nowrap max-w-[250px]">
                      <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={meta.displayName}>{meta.displayName}</div>
                      <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={meta.attributeKey}>{meta.attributeKey}</div>
                    </td>
                    <td className="whitespace-nowrap">
                      <span className="px-2 py-0.5 bg-gray-100 rounded-full text-[10px] font-bold text-gray-600 uppercase tracking-tight">{meta.dataType}</span>
                    </td>
                    <AdminDataTableActionCell>
                      <HasPermission action="PUT" path="/api/v1/pricing-metadata/*">
                        <AdminDataTableActionButton onClick={() => navigate(`/pricing-metadata/edit/${meta.attributeKey}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${meta.displayName}`}>
                          <Edit2 className="h-3.5 w-3.5" />
                          Edit
                        </AdminDataTableActionButton>
                      </HasPermission>
                      <HasPermission action="DELETE" path="/api/v1/pricing-metadata/*">
                        <AdminDataTableActionButton onClick={() => handleDelete(meta.attributeKey)} tone="danger" size="compact" title="Delete" aria-label={`Delete ${meta.displayName}`}>
                          <Trash2 className="h-3.5 w-3.5" />
                          Delete
                        </AdminDataTableActionButton>
                      </HasPermission>
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                ))
              )}
            </tbody>
        </AdminDataTable>
      )}

    </AdminPage>
  );
};

export default PricingMetadataPage;
