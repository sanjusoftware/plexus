import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, ShieldCheck, CheckCircle2, Info } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';

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

  const fetchFeatures = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/features');
      setFeatures(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch feature components.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [setToast]);

  useEffect(() => {
    fetchFeatures();
  }, [fetchFeatures]);

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/features/${id}/activate`);
      setToast({ message: 'Feature activated successfully.', type: 'success' });
      fetchFeatures();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Activation failed.', type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Features cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/features/${id}`);
      setToast({ message: 'Feature deleted successfully.', type: 'success' });
      fetchFeatures();
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
        <div className="admin-table-card">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Feature Component</th>
                <th>Data Type</th>
                <th>Status</th>
                <th>Version</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {features.map((feat) => (
                <tr key={feat.id} className="hover:bg-gray-50 transition">
                  <td className="whitespace-nowrap">
                    <div className="text-sm font-bold text-gray-900 leading-tight">{feat.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest">{feat.code}</div>
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
                  <td className="whitespace-nowrap text-right text-xs font-medium space-x-1">
                    {feat.status === 'DRAFT' && (
                      <HasPermission action="POST" path="/api/v1/features/*/activate">
                        <button onClick={() => handleActivate(feat.id)} className="text-green-600 hover:bg-green-50 p-1.5 rounded-lg transition border border-transparent hover:border-green-100" title="Activate"><CheckCircle2 className="w-4 h-4" /></button>
                      </HasPermission>
                    )}
                    <HasPermission action="PUT" path="/api/v1/features/*">
                      <button onClick={() => navigate(`/features/edit/${feat.id}`)} className="text-blue-600 hover:bg-blue-50 p-1.5 rounded-lg transition border border-transparent hover:border-blue-100" title="Edit"><Edit2 className="w-4 h-4" /></button>
                    </HasPermission>
                    <HasPermission action="DELETE" path="/api/v1/features/*">
                      <button onClick={() => handleDelete(feat.id)} className="text-red-600 hover:bg-red-50 p-1.5 rounded-lg transition border border-transparent hover:border-red-100" title="Delete"><Trash2 className="w-4 h-4" /></button>
                    </HasPermission>
                  </td>
                </tr>
              ))}
              {features.length === 0 && (
                <tr>
                  <td colSpan={5} className="admin-empty-state">No feature components found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </AdminPage>
  );
};

export default FeatureComponentsPage;
