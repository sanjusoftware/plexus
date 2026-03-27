import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Shield, CheckCircle2, AlertCircle, Info, ChevronDown, ChevronUp } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';

interface RoleMapping {
  name: string;
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
  const [expandedRoles, setExpandedRoles] = useState<Set<string>>(new Set());

  const toggleExpand = (roleName: string) => {
    const newExpanded = new Set(expandedRoles);
    if (newExpanded.has(roleName)) {
      newExpanded.delete(roleName);
    } else {
      newExpanded.add(roleName);
    }
    setExpandedRoles(newExpanded);
  };

  const groupAuthorities = (authorities: string[]) => {
    const groups: { [key: string]: string[] } = {};
    authorities.forEach(auth => {
      const parts = auth.split(':');
      const category = parts.length > 1 ? parts[0] : 'other';
      if (!groups[category]) groups[category] = [];
      groups[category].push(auth);
    });
    return groups;
  };

  const formatCategory = (category: string) => {
    return category.charAt(0).toUpperCase() + category.slice(1).toLowerCase() + " Management Permissions";
  };

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const m = await axios.get('/api/v1/roles/mapping');
      setMappings(m.data.map((r: any) => ({
        name: r.name,
        authorities: r.authorities || []
      })) || []);
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
        <HasPermission action="POST" path="/api/v1/roles/mapping">
          <button
            onClick={() => navigate('/roles/register')}
            className="bg-blue-600 text-white px-8 py-3.5 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-[11px] relative"
          >
            <Plus className="w-5 h-5 mr-3" /> Register New Role
          </button>
        </HasPermission>
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
        <div className="space-y-6">
          {mappings.map((mapping) => {
            const grouped = groupAuthorities(mapping.authorities);
            const isExpanded = expandedRoles.has(mapping.name);

            return (
              <div key={mapping.name} className="bg-white rounded-[2rem] shadow-sm border border-gray-100 overflow-hidden hover:shadow-lg transition duration-300 group">
                {/* Collapsed Row / Header */}
                <div
                  className="p-6 flex items-center justify-between cursor-pointer"
                  onClick={() => toggleExpand(mapping.name)}
                >
                  <div className="flex items-center space-x-6">
                    <div className="p-3 bg-blue-50 rounded-2xl group-hover:bg-blue-600 transition duration-300">
                      <Shield className="w-6 h-6 text-blue-600 group-hover:text-white transition duration-300" />
                    </div>
                    <div>
                      <h3 className="text-xl font-black text-gray-900 uppercase">{mapping.name}</h3>
                      <div className="flex items-center space-x-4 mt-1">
                        <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{mapping.authorities.length} Total Permissions</span>
                        <div className="flex space-x-2">
                          {Object.keys(grouped).map(cat => (
                            <span key={cat} className="text-[9px] bg-gray-50 text-gray-500 px-2 py-0.5 rounded-full font-bold border border-gray-100">
                              {cat.toUpperCase()}: {grouped[cat].length}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center space-x-4">
                    <div className="flex space-x-2 opacity-0 group-hover:opacity-100 transition duration-300">
                      <HasPermission action="POST" path="/api/v1/roles/mapping">
                        <button
                          onClick={(e) => { e.stopPropagation(); navigate(`/roles/edit/${mapping.name}`); }}
                          className="p-2 text-blue-600 hover:bg-blue-50 rounded-xl transition border border-transparent hover:border-blue-100 font-bold text-xs uppercase flex items-center"
                        >
                          Edit
                        </button>
                      </HasPermission>
                      <HasPermission action="POST" path="/api/v1/roles/mapping">
                        <button
                          onClick={(e) => { e.stopPropagation(); confirmDelete(mapping.name); }}
                          className="p-2 text-red-600 hover:bg-red-50 rounded-xl transition border border-transparent hover:border-red-100"
                        >
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </HasPermission>
                    </div>
                    {isExpanded ? <ChevronUp className="w-6 h-6 text-gray-400" /> : <ChevronDown className="w-6 h-6 text-gray-400" />}
                  </div>
                </div>

                {/* Expanded Content */}
                {isExpanded && (
                  <div className="px-8 pb-8 pt-2 border-t border-gray-50 bg-gray-50/30 animate-in slide-in-from-top-2 duration-300">
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-4">
                      {Object.keys(grouped).length > 0 ? Object.keys(grouped).map(category => (
                        <div key={category} className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm">
                          <h4 className="text-[10px] font-black text-blue-600 uppercase tracking-widest mb-4 border-b border-blue-50 pb-2">
                            {formatCategory(category)}
                          </h4>
                          <div className="space-y-2">
                            {grouped[category].map((auth, idx) => (
                              <div key={idx} className="flex items-center text-[11px] text-gray-600 font-mono font-bold">
                                <CheckCircle2 className="w-3.5 h-3.5 mr-2 text-green-500 flex-shrink-0" />
                                <span className="truncate">{auth}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )) : (
                        <div className="col-span-full py-8 text-center text-amber-600 bg-amber-50 rounded-2xl border border-amber-100 italic font-bold text-sm">
                          <AlertCircle className="w-4 h-4 inline mr-2" /> Warning: Empty role provides no system access.
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
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
