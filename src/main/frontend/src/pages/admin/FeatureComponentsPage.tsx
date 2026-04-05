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
    <div className="max-w-7xl mx-auto space-y-6 pb-20">
      <div className="bg-white rounded-[2rem] p-8 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-6">
          <div className="p-4 bg-blue-50 rounded-2xl"><ShieldCheck className="w-8 h-8 text-blue-600" /></div>
          <div>
            <h1 className="text-3xl font-black text-gray-900 tracking-tight">Feature Components</h1>
            <p className="text-gray-500 font-medium mt-1">Manage reusable non-monetary product features and specifications.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/features">
          <button
            onClick={() => navigate('/features/create')}
            className="bg-blue-600 text-white px-7 py-3 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-xs"
          >
            <Plus className="w-5 h-5 mr-2" /> New Feature
          </button>
        </HasPermission>
      </div>

      <div className="bg-blue-50/50 border-l-4 border-blue-400 p-6 rounded-r-2xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-blue-100 rounded-lg"><Info className="w-5 h-5 text-blue-700" /></div>
        <p className="text-sm text-blue-900 font-medium leading-relaxed italic">
          <strong>Usage Note:</strong> Feature components define attributes like "Max Tenure" or "Insurance Coverage".
          These can be linked to products where concrete values are assigned.
        </p>
      </div>

      {loading ? (
        <div className="flex justify-center p-24 bg-white rounded-3xl border border-gray-100"><Loader2 className="w-12 h-12 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Feature Component</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Data Type</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Version</th>
                <th className="px-6 py-5 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {features.map((feat) => (
                <tr key={feat.id} className="hover:bg-gray-50 transition">
                  <td className="px-6 py-6 whitespace-nowrap">
                    <div className="text-sm font-black text-gray-900 leading-tight">{feat.name}</div>
                    <div className="text-[10px] text-gray-400 font-mono mt-1 tracking-widest">{feat.code}</div>
                  </td>
                  <td className="px-6 py-6 whitespace-nowrap">
                    <span className="px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider bg-gray-100 text-gray-600 border border-gray-200">
                      {feat.dataType}
                    </span>
                  </td>
                  <td className="px-6 py-6 whitespace-nowrap">
                    <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${feat.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                      {feat.status}
                    </span>
                  </td>
                  <td className="px-6 py-6 whitespace-nowrap text-xs font-bold text-gray-500">
                    v{feat.version}
                  </td>
                  <td className="px-6 py-6 whitespace-nowrap text-right text-sm font-medium space-x-1">
                    {feat.status === 'DRAFT' && (
                      <HasPermission action="POST" path="/api/v1/features/*/activate">
                        <button onClick={() => handleActivate(feat.id)} className="text-green-600 hover:bg-green-50 p-2.5 rounded-xl transition border border-transparent hover:border-green-100" title="Activate"><CheckCircle2 className="w-4 h-4" /></button>
                      </HasPermission>
                    )}
                    <HasPermission action="PUT" path="/api/v1/features/*">
                      <button onClick={() => navigate(`/features/edit/${feat.id}`)} className="text-blue-600 hover:bg-blue-50 p-2.5 rounded-xl transition border border-transparent hover:border-blue-100" title="Edit"><Edit2 className="w-4 h-4" /></button>
                    </HasPermission>
                    <HasPermission action="DELETE" path="/api/v1/features/*">
                      <button onClick={() => handleDelete(feat.id)} className="text-red-600 hover:bg-red-50 p-2.5 rounded-xl transition border border-transparent hover:border-red-100" title="Delete"><Trash2 className="w-4 h-4" /></button>
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
