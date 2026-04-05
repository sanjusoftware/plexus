import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Database, Info } from 'lucide-react';
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

  const fetchMetadata = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-metadata');
      setMetadata(response.data);
    } catch (err: any) {
      setToast({ message: 'Failed to fetch pricing metadata. Check your permissions.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMetadata();
  }, []);

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
    <div className="max-w-6xl mx-auto space-y-4">
      <div className="flex justify-between items-center bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <div className="flex items-center space-x-4">
          <div className="p-3 bg-blue-50 rounded-xl"><Database className="w-6 h-6 text-blue-600" /></div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 tracking-tight">Pricing Input Metadata</h1>
            <p className="text-gray-500 font-medium mt-0.5 text-xs">Register attributes used in dynamic pricing calculation rules.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/pricing-metadata">
          <button
            onClick={() => navigate('/pricing-metadata/create')}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg flex items-center hover:bg-blue-700 transition font-bold shadow-md shadow-blue-100 text-sm"
          >
            <Plus className="w-4 h-4 mr-1.5" /> New Attribute
          </button>
        </HasPermission>
      </div>

      <div className="bg-amber-50 border-l-4 border-amber-400 p-4 rounded-r-xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-amber-100 rounded-lg"><Info className="w-5 h-5 text-amber-700" /></div>
        <p className="text-xs text-amber-900 font-medium leading-relaxed italic">
          <strong>Pro-Tip:</strong> The <strong>Attribute Key</strong> is critical. It must exactly match the key used in your Drools rules (e.g., <code>customer_segment</code>).
          The data type ensures the rule engine can correctly compare values (e.g., numeric comparison for DECIMAL).
        </p>
      </div>

      {loading ? (
        <div className="flex justify-center p-16 bg-white rounded-xl border border-gray-100 shadow-sm"><Loader2 className="w-10 h-10 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="px-6 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Key</th>
                <th className="px-6 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Display</th>
                <th className="px-6 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Type</th>
                <th className="px-6 py-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {metadata.length === 0 ? (
                <tr><td colSpan={4} className="px-6 py-12 text-center text-gray-500 text-sm italic font-medium italic">No metadata registered. Rules engine will not recognize dynamic inputs.</td></tr>
              ) : (
                metadata.map((meta) => (
                  <tr key={meta.id} className="hover:bg-gray-50/50 transition cursor-default">
                    <td className="px-6 py-3 whitespace-nowrap text-xs font-mono font-bold text-blue-700 bg-blue-50/20">{meta.attributeKey}</td>
                    <td className="px-6 py-3 whitespace-nowrap text-xs text-gray-900 font-bold">{meta.displayName}</td>
                    <td className="px-6 py-3 whitespace-nowrap">
                      <span className="px-2 py-0.5 bg-gray-100 rounded-full text-[10px] font-bold text-gray-600 uppercase tracking-tight">{meta.dataType}</span>
                    </td>
                    <td className="px-6 py-3 whitespace-nowrap text-right text-xs font-medium space-x-1">
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

    </div>
  );
};

export default PricingMetadataPage;
