import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, X, Database, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import { HasPermission } from '../../components/HasPermission';

interface PricingMetadata {
  id: number;
  attributeKey: string;
  displayName: string;
  dataType: string;
}

const PricingMetadataPage = () => {
  const navigate = useNavigate();
  const [metadata, setMetadata] = useState<PricingMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchMetadata = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-metadata');
      setMetadata(response.data);
    } catch (err: any) {
      setError('Failed to fetch pricing metadata. Check your permissions.');
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
      setSuccess('Metadata deleted successfully.');
      fetchMetadata();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to delete.');
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex justify-between items-center bg-white p-8 rounded-3xl shadow-sm border border-gray-100">
        <div className="flex items-center space-x-5">
          <div className="p-4 bg-blue-50 rounded-2xl"><Database className="w-8 h-8 text-blue-600" /></div>
          <div>
            <h1 className="text-3xl font-black text-gray-900 tracking-tight">Pricing Input Metadata</h1>
            <p className="text-gray-500 font-medium mt-1">Register attributes used in dynamic pricing calculation rules.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/pricing-metadata">
          <button
            onClick={() => navigate('/pricing-metadata/create')}
            className="bg-blue-600 text-white px-6 py-3 rounded-2xl flex items-center hover:bg-blue-700 transition font-bold shadow-xl shadow-blue-100"
          >
            <Plus className="w-5 h-5 mr-2" /> New Attribute
          </button>
        </HasPermission>
      </div>

      <div className="bg-amber-50 border-l-4 border-amber-400 p-6 rounded-r-2xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-amber-100 rounded-lg"><Info className="w-5 h-5 text-amber-700" /></div>
        <p className="text-sm text-amber-900 font-medium leading-relaxed italic">
          <strong>Pro-Tip:</strong> The <strong>Attribute Key</strong> is critical. It must exactly match the key used in your Drools rules (e.g., <code>customer_segment</code>).
          The data type ensures the rule engine can correctly compare values (e.g., numeric comparison for DECIMAL).
        </p>
      </div>

      {error && (
        <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-r-xl text-red-700 text-sm font-bold flex items-center justify-between">
          <div className="flex items-center">
            <AlertCircle className="w-4 h-4 mr-3 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError('')} className="ml-4 hover:bg-red-100 p-1 rounded-full transition text-red-500" title="Dismiss">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}
      {success && (
        <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded-r-xl text-green-700 text-sm font-bold flex items-center justify-between">
          <div className="flex items-center">
            <CheckCircle2 className="w-4 h-4 mr-3 flex-shrink-0" />
            <span>{success}</span>
          </div>
          <button onClick={() => setSuccess('')} className="ml-4 hover:bg-green-100 p-1 rounded-full transition text-green-500" title="Dismiss">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {loading ? (
        <div className="flex justify-center p-24 bg-white rounded-3xl border border-gray-100 shadow-sm"><Loader2 className="w-12 h-12 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Key</th>
                <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Display</th>
                <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Type</th>
                <th className="px-8 py-5 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {metadata.length === 0 ? (
                <tr><td colSpan={4} className="px-8 py-20 text-center text-gray-400 font-medium italic">No metadata registered. Rules engine will not recognize dynamic inputs.</td></tr>
              ) : (
                metadata.map((meta) => (
                  <tr key={meta.id} className="hover:bg-gray-50/50 transition cursor-default">
                    <td className="px-8 py-6 whitespace-nowrap text-sm font-mono font-bold text-blue-700 bg-blue-50/20">{meta.attributeKey}</td>
                    <td className="px-8 py-6 whitespace-nowrap text-sm text-gray-900 font-bold">{meta.displayName}</td>
                    <td className="px-8 py-6 whitespace-nowrap">
                      <span className="px-3 py-1 bg-gray-100 rounded-full text-[10px] font-black text-gray-600 uppercase tracking-tight">{meta.dataType}</span>
                    </td>
                    <td className="px-8 py-6 whitespace-nowrap text-right text-sm font-medium space-x-1">
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
