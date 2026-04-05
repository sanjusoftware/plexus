import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, ShieldCheck, CheckCircle2, Info } from 'lucide-react';
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

  const fetchFeatures = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/features');
      setFeatures(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch feature components.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFeatures();
  }, []);

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
    <div className="max-w-6xl mx-auto space-y-4 pb-10">
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-4">
          <div className="p-3 bg-blue-50 rounded-xl"><ShieldCheck className="w-6 h-6 text-blue-600" /></div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 tracking-tight">Feature Components</h1>
            <p className="text-gray-500 font-medium mt-0.5 text-xs">Manage reusable non-monetary product features and specifications.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/features">
          <button
            onClick={() => navigate('/features/create')}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg flex items-center hover:bg-blue-700 transition font-bold shadow-md shadow-blue-100 text-sm"
          >
            <Plus className="w-4 h-4 mr-1.5" /> New Feature
          </button>
        </HasPermission>
      </div>

      <div className="bg-blue-50/50 border-l-4 border-blue-400 p-4 rounded-r-xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-blue-100 rounded-lg"><Info className="w-5 h-5 text-blue-700" /></div>
        <p className="text-xs text-blue-900 font-medium leading-relaxed italic">
          <strong>Usage Note:</strong> Feature components define attributes like "Max Tenure" or "Insurance Coverage".
          These can be linked to products where concrete values are assigned.
        </p>
      </div>

      {loading ? (
        <div className="flex justify-center p-16 bg-white rounded-xl border border-gray-100"><Loader2 className="w-10 h-10 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Feature Component</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Data Type</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Version</th>
                <th className="px-4 py-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {features.map((feat) => (
                <tr key={feat.id} className="hover:bg-gray-50 transition">
                  <td className="px-4 py-3 whitespace-nowrap">
                    <div className="text-sm font-bold text-gray-900 leading-tight">{feat.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest">{feat.code}</div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-gray-100 text-gray-600 border border-gray-200">
                      {feat.dataType}
                    </span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${feat.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                      {feat.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-xs font-bold text-gray-500">
                    v{feat.version}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-xs font-medium space-x-1">
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
                  <td colSpan={5} className="px-6 py-12 text-center text-gray-400 font-medium italic">No feature components found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default FeatureComponentsPage;
