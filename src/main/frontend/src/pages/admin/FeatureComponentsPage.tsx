import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Puzzle, Info } from 'lucide-react';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionContent,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow,
  AuditTimestampCell
} from '../../components/AdminDataTable';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import ConfirmationModal from '../../components/ConfirmationModal';
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
  createdAt?: string;
  updatedAt?: string;
}

const FeatureComponentsPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [features, setFeatures] = useState<FeatureComponent[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<FeatureComponent | null>(null);
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();

  const fetchFeatures = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/features', { signal: abortSignal });
      setFeatures(response.data);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch product features.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchFeatures(signal);
  }, [fetchFeatures, signal]);

  useEffect(() => {
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (!successMessage || consumedSuccessKeyRef.current === location.key) {
      return;
    }

    consumedSuccessKeyRef.current = location.key;
    setToast({ message: successMessage, type: 'success' });
  }, [location.key, location.state, setToast]);

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
        icon={Puzzle}
        title="Product Features"
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
        <AdminDataTable aria-label="Product features table">
            <thead>
              <tr>
                <th>Product Feature</th>
                <th>Data Type</th>
                <th>Status</th>
                <th>Updated At</th>
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {features.map((feat) => (
                <AdminDataTableRow key={feat.id}>
                  <td className="whitespace-nowrap max-w-[250px]">
                    <div className="flex items-center gap-1.5 min-w-0">
                      <div className="text-sm font-bold text-gray-900 leading-tight truncate" title={feat.name}>{feat.name}</div>
                      <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                        v{feat.version ?? 1}
                      </span>
                    </div>
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
                   <AuditTimestampCell value={feat.updatedAt || feat.createdAt} />
                  <AdminDataTableActionCell>
                    {feat.status === 'DRAFT' && (
                      <>
                        <HasPermission action="POST" path="/api/v1/features/*/activate">
                          <AdminDataTableActionButton onClick={() => handleActivate(feat.id)} tone="success" size="compact" title="Activate" aria-label={`Activate ${feat.name}`}>
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                        <HasPermission action="PUT" path="/api/v1/features/*">
                          <AdminDataTableActionButton onClick={() => navigate(`/features/edit/${feat.id}`)} tone="primary" size="compact" title="Edit" aria-label={`Edit ${feat.name}`}>
                            <AdminDataTableActionContent action="edit" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                        <HasPermission action="DELETE" path="/api/v1/features/*">
                          <AdminDataTableActionButton onClick={() => setDeleteTarget(feat)} tone="danger" size="compact" title="Delete" aria-label={`Delete ${feat.name}`}>
                            <AdminDataTableActionContent action="delete" />
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
                          <AdminDataTableActionContent action="edit" />
                        </AdminDataTableActionButton>
                      </HasPermission>
                    )}
                  </AdminDataTableActionCell>
                </AdminDataTableRow>
              ))}
              {features.length === 0 && (
                <AdminDataTableEmptyRow colSpan={5}>No product features found.</AdminDataTableEmptyRow>
              )}
            </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && handleDelete(deleteTarget.id)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete feature "${deleteTarget?.name || deleteTarget?.code}"? This action cannot be undone if the feature is not linked to active products.`}
        confirmText="Confirm & Delete"
        variant="danger"
      />
    </AdminPage>
  );
};

export default FeatureComponentsPage;
