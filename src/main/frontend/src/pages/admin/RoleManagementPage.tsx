import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Shield, CheckCircle2, AlertCircle, Info, ChevronDown, ChevronUp } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { AdminDataTableActionButton, AdminDataTableActionContent } from '../../components/AdminDataTable';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

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

  const signal = useAbortSignal();

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const m = await axios.get('/api/v1/roles/mapping', { signal: abortSignal });
      setMappings(m.data.map((r: any) => ({
        name: r.name,
        authorities: r.authorities || []
      })) || []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch role mappings. Ensure you are a Bank Admin.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchInitialData(signal);
    if (location.state?.success) {
      setToast({ message: location.state.success, type: 'success' });
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location, setToast, fetchInitialData, signal, navigate]);

  const handleDelete = async (roleName: string) => {
    try {
      await axios.delete(`/api/v1/roles/${roleName}`);
      setToast({ message: 'Role deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: 'Failed to delete role mapping.', type: 'error' });
    }
  };

  const confirmDelete = (roleName: string) => {
    setRoleToDelete(roleName);
    setShowDeleteConfirm(true);
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Shield}
        title="Roles & Permissions"
        description="Map identity provider roles to granular system authorities."
        actions={
          <HasPermission action="POST" path="/api/v1/roles/mapping">
            <button
              onClick={() => navigate('/roles/register')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> Register New Role
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Internal RBAC Model" tone="indigo">
        <span className="italic">Permissions are aggregated based on the roles present in the user&apos;s token. Only authorities explicitly mapped below will be granted during the session handshake. The <strong>SYSTEM_ADMIN</strong> role is managed globally and cannot be modified here.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
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
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); navigate(`/roles/edit/${mapping.name}`); }}
                          tone="primary"
                          size="compact"
                        >
                          <AdminDataTableActionContent action="edit" />
                        </AdminDataTableActionButton>
                      </HasPermission>
                      <HasPermission action="POST" path="/api/v1/roles/mapping">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); confirmDelete(mapping.name); }}
                          tone="danger"
                          size="compact"
                        >
                          <AdminDataTableActionContent action="delete" />
                        </AdminDataTableActionButton>
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
    </AdminPage>
  );
};

export default RoleManagementPage;
