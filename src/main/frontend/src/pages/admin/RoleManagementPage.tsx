import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Plus, Trash2, Loader2, Save, X, Shield, CheckCircle2, AlertCircle, Info, Lock } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';

interface RoleMapping {
  roleName: string;
  authorities: string[];
}

const RoleManagementPage = () => {
  const [mappings, setMappings] = useState<RoleMapping[]>([]);
  const [availableAuthorities, setAvailableAuthorities] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState<RoleMapping>({ roleName: '', authorities: [] });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<string | null>(null);

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const [m, a] = await Promise.all([
        axios.get('/api/v1/roles/mapping'),
        axios.get('/api/v1/roles/authorities')
      ]);
      setMappings(m.data || []);
      setAvailableAuthorities(a.data || []);
    } catch (err: any) {
      setError('Failed to fetch role mappings. Ensure you are a Bank Admin.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  const openModal = (mapping?: RoleMapping) => {
    if (mapping) {
      setFormData({ roleName: mapping.roleName, authorities: [...mapping.authorities] });
    } else {
      setFormData({ roleName: '', authorities: [] });
    }
    setIsModalOpen(true);
  };

  const toggleAuthority = (auth: string) => {
    const newAuths = new Set(formData.authorities);
    if (newAuths.has(auth)) newAuths.delete(auth);
    else newAuths.add(auth);
    setFormData({ ...formData, authorities: Array.from(newAuths) });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      await axios.post('/api/v1/roles/mapping', formData);
      setSuccess('Permissions for the role have been successfully synchronized.');
      setIsModalOpen(false);
      fetchInitialData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Role mapping update failed.');
    }
  };

  const handleDelete = async (roleName: string) => {
    try {
      await axios.post('/api/v1/roles/mapping', { roleName, authorities: [] });
      setSuccess('Permissions cleared for role.');
      fetchInitialData();
    } catch (err: any) {
      setError('Failed to clear role mapping.');
    }
  };

  const confirmDelete = (roleName: string) => {
    setRoleToDelete(roleName);
    setShowDeleteConfirm(true);
  };

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-20">
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center overflow-hidden relative">
        <div className="absolute top-0 right-0 w-80 h-full bg-blue-50 -skew-x-12 translate-x-40 opacity-30"></div>
        <div className="flex items-center space-x-6 relative">
          <div className="p-4 bg-blue-100 rounded-2xl shadow-inner shadow-blue-200"><Shield className="w-10 h-10 text-blue-700" /></div>
          <div>
            <h1 className="text-4xl font-black text-gray-900 tracking-tight">Roles & Permissions</h1>
            <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">Map Identity Provider roles to granular system authorities</p>
          </div>
        </div>
        <button
          onClick={() => openModal()}
          className="bg-blue-600 text-white px-8 py-3.5 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-[11px] relative"
        >
          <Plus className="w-5 h-5 mr-3" /> Register New Role
        </button>
      </div>

      <div className="bg-indigo-50 border-l-4 border-indigo-400 p-8 rounded-r-3xl shadow-sm flex items-start space-x-6">
        <div className="p-3 bg-indigo-100 rounded-xl"><Info className="w-6 h-6 text-indigo-700" /></div>
        <div>
           <p className="text-sm text-indigo-900 font-black leading-relaxed uppercase tracking-wide mb-2">Internal RBAC Model</p>
           <p className="text-sm text-indigo-800 font-medium leading-relaxed italic max-w-4xl">
              Permissions are aggregated based on the roles present in the user's token.
              Only authorities explicitly mapped below will be granted during the session handshake.
              The <strong>SYSTEM_ADMIN</strong> role is managed globally and cannot be modified here.
           </p>
        </div>
      </div>

      {error && <div className="bg-red-50 border-l-4 border-red-500 p-6 rounded-r-3xl text-red-700 text-sm font-bold flex items-center shadow-md"><AlertCircle className="w-5 h-5 mr-4" />{error}</div>}
      {success && <div className="bg-green-50 border-l-4 border-green-500 p-6 rounded-r-3xl text-green-700 text-sm font-bold flex items-center shadow-md"><CheckCircle2 className="w-5 h-5 mr-4" />{success}</div>}

      {loading ? (
        <div className="flex justify-center p-32 bg-white rounded-[3rem] border border-gray-100"><Loader2 className="w-16 h-16 animate-spin text-blue-600" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {mappings.map((mapping) => (
            <div key={mapping.roleName} className="bg-white rounded-[2.5rem] shadow-sm border border-gray-100 p-8 flex flex-col hover:shadow-2xl transition duration-500 group">
              <div className="flex justify-between items-start mb-8 border-b border-gray-50 pb-6 group-hover:border-blue-100 transition duration-500">
                <div className="flex items-center space-x-4">
                  <div className="p-3 bg-blue-50 rounded-2xl group-hover:bg-blue-600 transition duration-500 shadow-sm shadow-blue-50"><Shield className="w-6 h-6 text-blue-600 group-hover:text-white transition duration-500" /></div>
                  <div>
                    <h3 className="text-xl font-black text-gray-900 leading-tight uppercase group-hover:text-blue-900 transition">{mapping.roleName}</h3>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mt-1 group-hover:text-blue-400 transition">{mapping.authorities.length} Active Permissions</p>
                  </div>
                </div>
                <div className="flex space-x-1 opacity-40 group-hover:opacity-100 transition duration-500">
                  <button onClick={() => openModal(mapping)} className="p-2.5 text-blue-600 hover:bg-blue-50 rounded-xl transition border border-transparent hover:border-blue-100" title="Modify Permissions"><Plus className="w-5 h-5" /></button>
                  <button onClick={() => confirmDelete(mapping.roleName)} className="p-2.5 text-red-600 hover:bg-red-50 rounded-xl transition border border-transparent hover:border-red-100" title="Revoke All Access"><Trash2 className="w-5 h-5" /></button>
                </div>
              </div>

              <div className="flex-1 space-y-2 max-h-48 overflow-y-auto pr-2 custom-scrollbar">
                {mapping.authorities.map((auth, idx) => (
                  <div key={idx} className="flex items-center text-[11px] text-gray-600 bg-gray-50/50 px-3 py-2.5 rounded-xl border border-gray-100 font-mono font-bold group-hover:bg-white group-hover:border-blue-50 transition duration-300">
                    <CheckCircle2 className="w-3.5 h-3.5 mr-3 text-green-500 flex-shrink-0" />
                    <span className="truncate">{auth}</span>
                  </div>
                ))}
                {mapping.authorities.length === 0 && (
                   <div className="flex items-center text-[10px] text-amber-600 p-4 bg-amber-50 rounded-2xl border border-amber-100 italic font-bold">
                      <AlertCircle className="w-4 h-4 mr-3" /> Warning: Empty role provides no system access.
                   </div>
                )}
              </div>
            </div>
          ))}
          {mappings.length === 0 && (
             <div className="col-span-full py-24 text-center text-gray-400 bg-white border-4 border-dashed rounded-[3rem] font-black uppercase tracking-widest text-xs">No role mappings defined for this bank.</div>
          )}
        </div>
      )}

      {isModalOpen && (
        <div className="fixed inset-0 bg-blue-900/40 backdrop-blur-xl flex items-center justify-center z-50 overflow-y-auto p-4 md:p-12">
          <div className="bg-white rounded-[3.5rem] max-w-4xl w-full shadow-2xl overflow-hidden my-auto border border-white/20 animate-in zoom-in-95 duration-200">
            <div className="px-12 py-10 bg-blue-900 text-white flex justify-between items-center relative overflow-hidden">
               <div className="absolute top-0 right-0 w-80 h-full bg-blue-800 -skew-x-12 translate-x-32 opacity-30"></div>
              <div className="relative">
                <h2 className="text-4xl font-black tracking-tighter uppercase">Authority Configuration</h2>
                <p className="text-blue-200 font-bold mt-1 text-sm">Select system permissions to grant to the IDP role.</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="hover:bg-blue-800 p-3 rounded-full transition relative border border-white/10 shadow-lg"><X className="w-8 h-8" /></button>
            </div>
            <form onSubmit={handleSubmit} className="p-12 space-y-10">
              {error && (
                <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
                  <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
                  <p className="text-sm font-bold">{error}</p>
                </div>
              )}
              <div className="bg-gray-50 p-8 rounded-3xl border border-gray-100">
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-4">Role Name (Key)</label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-300" />
                  <input
                    type="text"
                    required
                    className="w-full border-2 border-white rounded-2xl p-4 pl-12 font-mono font-black text-lg text-blue-700 transition focus:border-blue-500 shadow-sm uppercase placeholder:text-gray-200"
                    value={formData.roleName}
                    onChange={(e) => setFormData({...formData, roleName: e.target.value.toUpperCase()})}
                    placeholder="e.g. CUSTOMER_SUPPORT"
                  />
                </div>
                <p className="mt-4 text-[11px] text-gray-400 font-bold italic">This must match the exact string sent by your Identity Provider in the <code>roles</code> claim.</p>
              </div>

              <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-6">Select Authorized Operations</label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-h-[40vh] overflow-y-auto p-8 bg-gray-50 rounded-[2.5rem] border border-gray-100 custom-scrollbar shadow-inner">
                  {availableAuthorities.map((auth) => (
                    <label key={auth} className={`flex items-center p-4 rounded-2xl border-2 transition cursor-pointer hover:shadow-md ${formData.authorities.includes(auth) ? 'bg-white border-blue-500 shadow-blue-100' : 'bg-white/50 border-white hover:border-blue-200'}`}>
                      <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition ${formData.authorities.includes(auth) ? 'bg-blue-600 border-blue-600' : 'bg-white border-gray-200'}`}>
                        {formData.authorities.includes(auth) && <CheckCircle2 className="w-4 h-4 text-white" />}
                      </div>
                      <input
                        type="checkbox"
                        className="hidden"
                        checked={formData.authorities.includes(auth)}
                        onChange={() => toggleAuthority(auth)}
                      />
                      <span className="ml-4 text-[10px] font-black font-mono text-gray-700 tracking-tighter uppercase">{auth}</span>
                    </label>
                  ))}
                  {availableAuthorities.length === 0 && (
                     <div className="col-span-full py-12 text-center text-gray-400 font-bold italic">No authorities found in system registry. Registry is built during startup scan of @PreAuthorize annotations.</div>
                  )}
                </div>
              </div>

              <div className="pt-8 border-t border-gray-50 flex space-x-6">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-[2rem] font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard</button>
                <button type="submit" className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-[2rem] font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs">
                  <Save className="w-6 h-6 mr-3" /> Commit Authority Mapping
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <ConfirmationModal
        isOpen={showDeleteConfirm}
        onClose={() => { setShowDeleteConfirm(false); setRoleToDelete(null); }}
        onConfirm={() => roleToDelete && handleDelete(roleToDelete)}
        title="Confirm Revocation"
        message={`Warning: This will strip all permissions from the role "${roleToDelete}". Continue?`}
        confirmText="Confirm & Revoke"
        variant="danger"
      />
    </div>
  );
};

export default RoleManagementPage;
