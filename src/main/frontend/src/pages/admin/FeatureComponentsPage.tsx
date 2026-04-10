import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, ShieldCheck, CheckCircle2, Info } from 'lucide-react';
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

interface FeatureComponent {
  id: number;
  code: string;
  name: string;
  dataType: string;
  status: string;
  version: number;
}

const FeatureComponentsPage = () => {
  const navigate = useNavigate();
  const { setToast } = useAuth();
  const [features, setFeatures] = useState<FeatureComponent[]>([]);
  const [loading, setLoading] = useState(true);

  const signal = useAbortSignal();

  const fetchFeatures = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/features', { signal: abortSignal });
      setFeatures(response.data);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch feature components.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchFeatures(signal);
  }, [fetchFeatures, signal]);

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/features/${id}/activate`);
      setToast({ message: 'Feature activated successfully.', type: 'success' });
      await fetchFeatures(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Activation failed.', type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Features cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/features/${id}`);
      setToast({ message: 'Feature deleted successfully.', type: 'success' });
      await fetchFeatures(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={ShieldCheck}
        title="Feature Components"
        description="Manage reusable non-monetary product features and specifications."
        actions={
          <HasPermission action="POST" path="/api/v1/features">
            <button
              onClick={() => navigate('/features/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Feature
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Usage Note">
        <span className="italic">Feature components define attributes like “Max Tenure” or “Insurance Coverage”. These can be linked to products where concrete values are assigned.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Feature components table">
            <thead>
              <tr>
                <th>Feature Component</th>
                <th>Data Type</th>
                <th>Status</th>
                <th>Version</th>
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {features.map((feat) => (
                <AdminDataTableRow key={feat.id}>
                  <td className="whitespace-nowrap max-w-[250px]">
                    <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={feat.name}>{feat.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={feat.code}>{feat.code}</div>
                  </td>
                  <td className="whitespace-nowrap">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-gray-100 text-gray-600 border border-gray-200">
                      {feat.dataType}
                    </span>
                  </td>
                  <td className="whitespace-nowrap">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${feat.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                      {feat.status}
                    </span>
                  </td>
                  <td className="whitespace-nowrap font-bold text-gray-500">
                    v{feat.version}
                  </td>
                  <AdminDataTableActionCell>
                    {feat.status === 'DRAFT' && (
                      <>
                        <HasPermission action="POST" path="/api/v1/features/*/activate">
                          <AdminDataTableActionButton onClick={() => handleActivate(feat.id)} tone="success" size="compact" title="Activate" aria-label={`Activate ${feat.name}`}>
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            Activate
                          </AdminDataTableActionButton>
                        </HasPermission>
                        <HasPermission action="PUT" path="/api/v1/features/*">
                          <AdminDataTableActionButton onClick={() => navigate(`/features/edit/${feat.id}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${feat.name}`}>
                            <Edit2 className="h-3.5 w-3.5" />
                            Edit
                          </AdminDataTableActionButton>
                        </HasPermission>
                        <HasPermission action="DELETE" path="/api/v1/features/*">
                          <AdminDataTableActionButton onClick={() => handleDelete(feat.id)} tone="danger" size="compact" title="Delete" aria-label={`Delete ${feat.name}`}>
                            <Trash2 className="h-3.5 w-3.5" />
                            Delete
                          </AdminDataTableActionButton>
                        </HasPermission>
                      </>
                    )}
                    {feat.status === 'ACTIVE' && (
                      <HasPermission action="PUT" path="/api/v1/features/*">
                        <AdminDataTableActionButton
                          tone="primary"
                          size="compact"
                          disabled
                          title="Direct editing is not allowed for active features. Linked products must be versioned to apply feature metadata changes."
                          aria-label={`Edit ${feat.name} (Disabled)`}
                        >
                          <Edit2 className="h-3.5 w-3.5" />
                          Edit
                        </AdminDataTableActionButton>
                      </HasPermission>
                    )}
                  </AdminDataTableActionCell>
                </AdminDataTableRow>
              ))}
              {features.length === 0 && (
                <AdminDataTableEmptyRow colSpan={5}>No feature components found.</AdminDataTableEmptyRow>
              )}
            </tbody>
        </AdminDataTable>
      )}
    </AdminPage>
  );
};

export default FeatureComponentsPage;
