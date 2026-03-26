import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Shield, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';

interface RoleMapping {
  roleName: string;
  authorities: string[];
}

const RoleManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [mappings, setMappings] = useState<RoleMapping[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<string | null>(null);

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const m = await axios.get('/api/v1/roles/mapping');
      setMappings(m.data || []);
    } catch (err: any) {
      setError('Failed to fetch role mappings. Ensure you are a Bank Admin.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
    if (location.state?.success) {
      setSuccess(location.state.success);
      window.history.replaceState({}, document.title);
    }
  }, [location]);

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
          onClick={() => navigate('/roles/register')}
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
                  <button onClick={() => navigate(`/roles/edit/${mapping.roleName}`)} className="p-2.5 text-blue-600 hover:bg-blue-50 rounded-xl transition border border-transparent hover:border-blue-100" title="Modify Permissions"><Plus className="w-5 h-5" /></button>
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
