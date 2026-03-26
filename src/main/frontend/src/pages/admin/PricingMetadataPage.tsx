import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Save, X, Database, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import StyledSelect from '../../components/StyledSelect';

interface PricingMetadata {
  id: number;
  attributeKey: string;
  displayName: string;
  dataType: string;
}

const PricingMetadataPage = () => {
  const [metadata, setMetadata] = useState<PricingMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMetadata, setEditingMetadata] = useState<PricingMetadata | null>(null);
  const [formData, setFormData] = useState({ attributeKey: '', displayName: '', dataType: 'STRING' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [modalError, setModalError] = useState('');

  const dataTypes = ['STRING', 'DECIMAL', 'INTEGER', 'BOOLEAN', 'DATE'];

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setModalError('');
    setSuccess('');
    try {
      if (editingMetadata) {
        await axios.put(`/api/v1/pricing-metadata/${editingMetadata.id}`, formData);
        setSuccess('Metadata updated successfully.');
      } else {
        await axios.post('/api/v1/pricing-metadata', formData);
        setSuccess('Metadata created successfully.');
      }
      setIsModalOpen(false);
      setEditingMetadata(null);
      setFormData({ attributeKey: '', displayName: '', dataType: 'STRING' });
      fetchMetadata();
    } catch (err: any) {
      const msg = err.response?.data?.message || 'An error occurred.';
      setError(msg);
      setModalError(msg);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Deleting metadata might break existing rules that use this attribute.')) return;
    try {
      await axios.delete(`/api/v1/pricing-metadata/${id}`);
      setSuccess('Metadata deleted successfully.');
      fetchMetadata();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to delete.');
    }
  };

  const openModal = (meta?: PricingMetadata) => {
    if (meta) {
      setEditingMetadata(meta);
      setFormData({ attributeKey: meta.attributeKey, displayName: meta.displayName, dataType: meta.dataType });
    } else {
      setEditingMetadata(null);
      setFormData({ attributeKey: '', displayName: '', dataType: 'STRING' });
    }
    setIsModalOpen(true);
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
        <button
          onClick={() => openModal()}
          className="bg-blue-600 text-white px-6 py-3 rounded-2xl flex items-center hover:bg-blue-700 transition font-bold shadow-xl shadow-blue-100"
        >
          <Plus className="w-5 h-5 mr-2" /> New Attribute
        </button>
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
                      <button onClick={() => openModal(meta)} className="text-blue-600 hover:bg-blue-50 p-2.5 rounded-xl transition shadow-sm border border-blue-50" title="Edit"><Edit2 className="w-4 h-4" /></button>
                      <button onClick={() => handleDelete(meta.id)} className="text-red-600 hover:bg-red-50 p-2.5 rounded-xl transition shadow-sm border border-red-50" title="Delete"><Trash2 className="w-4 h-4" /></button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {isModalOpen && (
        <div className="fixed inset-0 bg-blue-900/40 backdrop-blur-md flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-[2rem] p-10 max-w-lg w-full shadow-2xl animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center mb-8 pb-4 border-b border-gray-100">
              <h2 className="text-2xl font-black text-gray-900 tracking-tight">{editingMetadata ? 'Edit Attribute' : 'Register Attribute'}</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600 transition p-2.5 hover:bg-gray-100 rounded-full"><X className="w-7 h-7" /></button>
            </div>

            {modalError && (
              <div className="mb-8 bg-red-50 border-l-4 border-red-500 p-4 rounded-r-xl text-red-700 text-sm font-bold flex items-center justify-between animate-in fade-in slide-in-from-top-2 duration-200">
                <div className="flex items-center">
                  <AlertCircle className="w-4 h-4 mr-3 flex-shrink-0" />
                  <span>{modalError}</span>
                </div>
                <button onClick={() => setModalError('')} className="ml-4 hover:bg-red-100 p-1 rounded-full transition text-red-500" title="Dismiss">
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-8">
              <div>
                <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Internal Attribute Key</label>
                <input
                  type="text"
                  required
                  className="block w-full border-2 border-gray-100 rounded-2xl p-4 focus:ring-4 focus:ring-blue-500/10 focus:border-blue-500 font-mono font-bold text-blue-700 transition"
                  value={formData.attributeKey}
                  onChange={(e) => setFormData({ ...formData, attributeKey: e.target.value })}
                  placeholder="e.g. current_balance"
                />
                <p className="mt-3 text-[11px] text-gray-400 font-medium italic">Used by developers in rule definitions. Must be unique.</p>
              </div>
              <div>
                <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Friendly Display Name</label>
                <input
                  type="text"
                  required
                  className="block w-full border-2 border-gray-100 rounded-2xl p-4 focus:ring-4 focus:ring-blue-500/10 focus:border-blue-500 font-bold transition"
                  value={formData.displayName}
                  onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                  placeholder="e.g. Account Balance"
                />
                <p className="mt-3 text-[11px] text-gray-400 font-medium italic">What the business user sees in the pricing dashboard.</p>
              </div>
              <div>
                <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Attribute Data Type</label>
                <StyledSelect
                  required
                  value={formData.dataType}
                  onChange={(e) => setFormData({ ...formData, dataType: e.target.value })}
                >
                  {dataTypes.map(type => (
                    <option key={type} value={type}>{type}</option>
                  ))}
                </StyledSelect>
                <p className="mt-3 text-[11px] text-gray-400 font-medium italic">Affects how the rule engine processes and validates values.</p>
              </div>
              <div className="pt-6 flex space-x-4">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 px-4 py-4 border-2 border-gray-100 rounded-2xl font-black text-gray-500 hover:bg-gray-50 transition uppercase tracking-widest text-xs">Cancel</button>
                <button type="submit" className="flex-1 px-4 py-4 bg-blue-600 text-white rounded-2xl font-black hover:bg-blue-700 transition flex items-center justify-center shadow-2xl shadow-blue-200 uppercase tracking-widest text-xs">
                  <Save className="w-5 h-5 mr-2" /> Save Metadata
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PricingMetadataPage;
