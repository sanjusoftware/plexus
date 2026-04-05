import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Database, Info } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';

interface PricingMetadata {
  id: number;
  attributeKey: string;
  displayName: string;
  dataType: string;
}

const PricingMetadataPage = () => {
  const navigate = useNavigate();
  const { setToast } = useAuth();
  const [metadata, setMetadata] = useState<PricingMetadata[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchMetadata = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-metadata');
      setMetadata(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch pricing metadata. Check your permissions.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [setToast]);

  useEffect(() => {
    fetchMetadata();
  }, [fetchMetadata]);

  const handleDelete = async (attributeKey: string) => {
    if (!window.confirm('Are you sure? Deleting metadata might break existing rules that use this attribute.')) return;
    try {
      await axios.delete(`/api/v1/pricing-metadata/${attributeKey}`);
      setToast({ message: 'Metadata deleted successfully.', type: 'success' });
      fetchMetadata();
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
        <div className="admin-table-card">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Display</th>
                <th>Type</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {metadata.length === 0 ? (
                <tr><td colSpan={4} className="admin-empty-state">No metadata registered. Rules engine will not recognize dynamic inputs.</td></tr>
              ) : (
                metadata.map((meta) => (
                  <tr key={meta.id} className="hover:bg-gray-50/50 transition cursor-default">
                    <td className="whitespace-nowrap bg-blue-50/20 font-mono font-bold text-blue-700">{meta.attributeKey}</td>
                    <td className="whitespace-nowrap text-sm font-bold text-gray-900">{meta.displayName}</td>
                    <td className="whitespace-nowrap">
                      <span className="px-2 py-0.5 bg-gray-100 rounded-full text-[10px] font-bold text-gray-600 uppercase tracking-tight">{meta.dataType}</span>
                    </td>
                    <td className="whitespace-nowrap text-right text-xs font-medium space-x-1">
                      <HasPermission action="PUT" path="/api/v1/pricing-metadata/*">
                        <button onClick={() => navigate(`/pricing-metadata/edit/${meta.attributeKey}`)} className="text-blue-600 hover:bg-blue-50 p-2.5 rounded-xl transition shadow-sm border border-blue-50" title="Edit"><Edit2 className="w-4 h-4" /></button>
                      </HasPermission>
                      <HasPermission action="DELETE" path="/api/v1/pricing-metadata/*">
                        <button onClick={() => handleDelete(meta.attributeKey)} className="text-red-600 hover:bg-red-50 p-2.5 rounded-xl transition shadow-sm border border-red-50" title="Delete"><Trash2 className="w-4 h-4" /></button>
                      </HasPermission>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

    </AdminPage>
  );
};

export default PricingMetadataPage;
