import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Shield, CheckCircle2, AlertCircle, Info, ChevronDown, ChevronUp } from 'lucide-react';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';

interface RoleMapping {
  name: string;
  authorities: string[];
}

const RoleManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [mappings, setMappings] = useState<RoleMapping[]>([]);
  const [loading, setLoading] = useState(true);
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
    const groups: { [category: string]: { [subCategory: string]: string[] } } = {};
    authorities.forEach(auth => {
      const parts = auth.split(':');
      const category = parts.length > 1 ? parts[0] : 'other';

      let subCategory = '';
      if (parts.length > 2) {
        subCategory = parts.slice(1, -1).join(':');
      }

      if (!groups[category]) groups[category] = {};
      if (!groups[category][subCategory]) groups[category][subCategory] = [];
      groups[category][subCategory].push(auth);
    });
    return groups;
  };

  const formatCategory = (category: string) => {
    return category.charAt(0).toUpperCase() + category.slice(1).toLowerCase() + " Management Permissions";
  };

  const formatSubCategory = (subCategory: string) => {
    return subCategory.split(':').map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase()).join(' ');
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
      setToast({ message: 'Failed to fetch role mappings. Ensure you are a Bank Admin.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
    if (location.state?.success) {
      setToast({ message: location.state.success, type: 'success' });
      window.history.replaceState({}, document.title);
    }
  }, [location, setToast]);

  const handleDelete = async (roleName: string) => {
    try {
      await axios.delete(`/api/v1/roles/${roleName}`);
      setToast({ message: 'Role deleted successfully.', type: 'success' });
      fetchInitialData();
    } catch (err: any) {
      setToast({ message: 'Failed to delete role mapping.', type: 'error' });
    }
  };

  const confirmDelete = (roleName: string) => {
    setRoleToDelete(roleName);
    setShowDeleteConfirm(true);
  };

  return (
    <div className="max-w-6xl mx-auto space-y-4 pb-10">
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 flex justify-between items-center overflow-hidden relative">
        <div className="flex items-center space-x-4 relative">
          <div className="p-3 bg-blue-100 rounded-xl shadow-inner shadow-blue-200"><Shield className="w-6 h-6 text-blue-700" /></div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 tracking-tight">Roles & Permissions</h1>
            <p className="text-gray-500 font-bold mt-0.5 uppercase tracking-widest text-[10px]">Map Identity Provider roles to granular system authorities</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/roles/mapping">
          <button
            onClick={() => navigate('/roles/register')}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg flex items-center hover:bg-blue-700 transition font-bold shadow-md shadow-blue-100 uppercase tracking-widest text-[10px] relative"
          >
            <Plus className="w-4 h-4 mr-1.5" /> Register New Role
          </button>
        </HasPermission>
      </div>

      <div className="bg-indigo-50 border-l-4 border-indigo-400 p-4 rounded-r-xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-indigo-100 rounded-lg"><Info className="w-5 h-5 text-indigo-700" /></div>
        <div>
           <p className="text-xs text-indigo-900 font-bold leading-relaxed uppercase tracking-wide mb-1">Internal RBAC Model</p>
           <p className="text-xs text-indigo-800 font-medium leading-relaxed italic max-w-4xl">
              Permissions are aggregated based on the roles present in the user's token.
              Only authorities explicitly mapped below will be granted during the session handshake.
              The <strong>SYSTEM_ADMIN</strong> role is managed globally and cannot be modified here.
           </p>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center p-16 bg-white rounded-xl border border-gray-100"><Loader2 className="w-10 h-10 animate-spin text-blue-600" /></div>
      ) : (
        <div className="space-y-4">
          {mappings.map((mapping) => {
            const grouped = groupAuthorities(mapping.authorities);
            const isExpanded = expandedRoles.has(mapping.name);

            return (
              <div key={mapping.name} className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md transition duration-300 group">
                {/* Collapsed Row / Header */}
                <div
                  className="p-4 flex items-center justify-between cursor-pointer"
                  onClick={() => toggleExpand(mapping.name)}
                >
                  <div className="flex items-center space-x-4">
                    <div className="p-2.5 bg-blue-50 rounded-xl group-hover:bg-blue-600 transition duration-300">
                      <Shield className="w-5 h-5 text-blue-600 group-hover:text-white transition duration-300" />
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-gray-900 uppercase">{mapping.name}</h3>
                      <div className="flex items-center space-x-3 mt-0.5">
                        <span className="text-[9px] font-bold text-gray-400 uppercase tracking-widest">{mapping.authorities.length} Permissions</span>
                        <div className="flex space-x-1.5">
                          {Object.keys(grouped).map(cat => (
                            <span key={cat} className="text-[8px] bg-gray-50 text-gray-500 px-1.5 py-0.5 rounded-full font-bold border border-gray-100">
                              {cat.toUpperCase()}: {Object.values(grouped[cat]).flat().length}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center space-x-3">
                    <div className="flex space-x-1.5 opacity-0 group-hover:opacity-100 transition duration-300">
                      <HasPermission action="POST" path="/api/v1/roles/mapping">
                        <button
                          onClick={(e) => { e.stopPropagation(); navigate(`/roles/edit/${mapping.name}`); }}
                          className="px-2 py-1 text-blue-600 hover:bg-blue-50 rounded-lg transition border border-transparent hover:border-blue-100 font-bold text-[10px] uppercase flex items-center"
                        >
                          Edit
                        </button>
                      </HasPermission>
                      <HasPermission action="POST" path="/api/v1/roles/mapping">
                        <button
                          onClick={(e) => { e.stopPropagation(); confirmDelete(mapping.name); }}
                          className="p-1.5 text-red-600 hover:bg-red-50 rounded-lg transition border border-transparent hover:border-red-100"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </HasPermission>
                    </div>
                    {isExpanded ? <ChevronUp className="w-5 h-5 text-gray-400" /> : <ChevronDown className="w-5 h-5 text-gray-400" />}
                  </div>
                </div>

                {/* Expanded Content */}
                {isExpanded && (
                  <div className="px-6 pb-6 pt-2 border-t border-gray-50 bg-gray-50/30 animate-in slide-in-from-top-2 duration-300">
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-2">
                      {Object.keys(grouped).length > 0 ? Object.entries(grouped).map(([category, subGroups]) => (
                        <div key={category} className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
                          <h4 className="text-[9px] font-bold text-blue-600 uppercase tracking-widest mb-3 border-b border-blue-50 pb-1.5">
                            {formatCategory(category)}
                          </h4>
                          <div className="space-y-3">
                            {Object.entries(subGroups).map(([subCategory, auths]) => (
                              <div key={subCategory}>
                                {subCategory !== '' && (
                                  <div className="text-[8px] font-bold text-gray-400 uppercase mb-1.5 ml-1">{formatSubCategory(subCategory)}</div>
                                )}
                                <div className="space-y-1">
                                  {auths.map((auth, idx) => (
                                    <div key={idx} className="flex items-center text-[10px] text-gray-600 font-mono font-bold pl-1">
                                      <CheckCircle2 className="w-2.5 h-2.5 mr-1.5 text-green-500 flex-shrink-0" />
                                      <span className="truncate">{auth}</span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )) : (
                        <div className="col-span-full py-6 text-center text-amber-600 bg-amber-50 rounded-xl border border-amber-100 italic font-bold text-xs">
                          <AlertCircle className="w-3.5 h-3.5 inline mr-1.5" /> Warning: Empty role provides no system access.
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
          {mappings.length === 0 && (
             <div className="col-span-full py-12 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-xs">No role mappings defined for this bank.</div>
          )}
        </div>
      )}


      <ConfirmationModal
        isOpen={showDeleteConfirm}
        onClose={() => { setShowDeleteConfirm(false); setRoleToDelete(null); }}
        onConfirm={() => roleToDelete && handleDelete(roleToDelete)}
        title="Confirm Deletion"
        message={`Warning: This will permanently delete the role "${roleToDelete}" and all its associated permission mappings. This action cannot be undone. Continue?`}
        confirmText="Confirm & Delete"
        variant="danger"
      />
    </div>
  );
};

export default RoleManagementPage;
