import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Save, Shield, CheckCircle2, Loader2, Lock, CheckSquare, Square, MinusSquare, Circle } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

interface RoleMapping {
  name: string;
  authorities: string[];
}

const RoleFormPage = () => {
  const { roleName } = useParams<{ roleName?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const isEditing = !!roleName;

  const [availableAuthorities, setAvailableAuthorities] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState<RoleMapping>({ name: roleName || '', authorities: [] });
  const [submitting, setSubmitting] = useState(false);
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());
  const signal = useAbortSignal();

  useEffect(() => {
    const categories = Array.from(new Set(
      availableAuthorities.map(auth => {
        const parts = auth.split(':');
        return parts.length > 1 ? parts[0] : 'other';
      })
    ));

    // Edit mode starts collapsed by default; create mode remains expanded.
    setExpandedCategories(isEditing ? new Set() : new Set(categories));
  }, [availableAuthorities, isEditing]);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [authsRes, mappingsRes] = await Promise.all([
          axios.get('/api/v1/roles/authorities', { signal }),
          isEditing ? axios.get('/api/v1/roles/mapping', { signal }) : Promise.resolve({ data: [] })
        ]);

        setAvailableAuthorities(authsRes.data || []);

        if (isEditing) {
          const mappings = mappingsRes.data as RoleMapping[];
          const mapping = mappings.find(m => m.name === roleName);
          if (mapping) {
            setFormData({ name: mapping.name, authorities: [...mapping.authorities] });
            setEntityName(mapping.name);
          } else {
            setToast({ message: `Role "${roleName}" not found.`, type: 'error' });
          }
        }
      } catch (err: any) {
        if (axios.isCancel(err)) return;
        setToast({ message: 'Failed to fetch required data.', type: 'error' });
      } finally {
        if (!signal.aborted) {
          setLoading(false);
        }
      }
    };

    fetchData();
  }, [roleName, isEditing, setEntityName, setToast, signal]);

  const toggleAuthority = (auth: string) => {
    const newAuths = new Set(formData.authorities);
    if (newAuths.has(auth)) newAuths.delete(auth);
    else newAuths.add(auth);
    setFormData({ ...formData, authorities: Array.from(newAuths) });
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

  const toggleCategory = (category: string, auths: string[]) => {
    const newAuths = new Set(formData.authorities);
    const allSelected = auths.every(a => newAuths.has(a));
    if (allSelected) {
      auths.forEach(a => newAuths.delete(a));
    } else {
      auths.forEach(a => newAuths.add(a));
    }
    setFormData({ ...formData, authorities: Array.from(newAuths) });
  };

  const toggleSubCategory = (auths: string[]) => {
    const newAuths = new Set(formData.authorities);
    const allSelected = auths.every(a => newAuths.has(a));
    if (allSelected) {
      auths.forEach(a => newAuths.delete(a));
    } else {
      auths.forEach(a => newAuths.add(a));
    }
    setFormData({ ...formData, authorities: Array.from(newAuths) });
  };

  const toggleCategoryExpansion = (category: string) => {
    setExpandedCategories(prev => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  };

  const formatCategory = (category: string) => {
    return category.charAt(0).toUpperCase() + category.slice(1).toLowerCase() + " Management Permissions";
  };

  const formatSubCategory = (subCategory: string) => {
    return subCategory.split(':').map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase()).join(' ');
  };

  const formatAction = (auth: string) => {
    const parts = auth.split(':');
    return parts[parts.length - 1].toUpperCase();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await axios.post('/api/v1/roles/mapping', { roleName: formData.name, authorities: formData.authorities });
      setToast({ message: 'Permissions for the role have been successfully synchronized.', type: 'success' });
      navigate('/roles', { state: { success: 'Permissions for the role have been successfully synchronized.' } });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Role mapping update failed.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center p-20">
        <Loader2 className="h-12 w-12 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <AdminPage>
      <AdminFormHeader
        icon={Shield}
        title={isEditing ? 'Authority Configuration' : 'Register New Role'}
        description={isEditing ? <span>Modifying system permissions for role <strong className="text-gray-900">{roleName}</strong></span> : 'Define a new role and select its system permissions'}
        onClose={() => navigate('/roles')}
      />

      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <form onSubmit={handleSubmit} className="space-y-6 p-5">
          {!isEditing && (
            <div className="relative overflow-hidden rounded-xl border border-blue-100/50 bg-blue-50/50 p-5">
              <div className="relative">
                <label className="block text-[10px] font-bold text-blue-400 uppercase tracking-widest mb-3">Role Name (Key)</label>
                <div className="relative max-w-xl">
                  <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-blue-300" />
                  <input
                    type="text"
                    required
                    className="w-full border border-white rounded-xl p-3 pl-10 font-mono font-bold text-lg text-blue-700 transition focus:border-blue-500 shadow-sm uppercase placeholder:text-blue-100 bg-white"
                    value={formData.name}
                    onChange={(e) => {
                      setFormData({...formData, name: e.target.value.toUpperCase()});
                    }}
                    placeholder="e.g. CUSTOMER_SUPPORT"
                  />
                </div>
                <p className="mt-2.5 text-[10px] text-blue-400/80 font-bold italic">This must match the exact string sent by your Identity Provider in the <code>roles</code> claim.</p>
              </div>
            </div>
          )}

          <div>
            <label className="mb-4 block px-2 text-[10px] font-bold uppercase tracking-widest text-gray-400">Select Authorized Operations</label>
            <div className="space-y-4">
              {Object.entries(groupAuthorities(availableAuthorities)).map(([category, subGroups]) => {
                const allAuthsInCategory = Object.values(subGroups).flat();
                const selectedInCat = allAuthsInCategory.filter(a => formData.authorities.includes(a));
                const isAllSelected = selectedInCat.length === allAuthsInCategory.length;
                const isSomeSelected = selectedInCat.length > 0 && !isAllSelected;
                const isExpanded = expandedCategories.has(category);

                return (
                  <div key={category} className="rounded-xl border border-gray-100 bg-gray-50/50 p-4">
                    <div
                      className={`flex items-center justify-between rounded-lg px-2 py-2 transition-colors hover:bg-gray-100/70 ${isExpanded ? 'mb-2 border-b border-gray-200/50 pb-2' : 'mb-0'} cursor-pointer`}
                      onClick={() => toggleCategoryExpansion(category)}
                      role="button"
                      tabIndex={0}
                      aria-expanded={isExpanded}
                      aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${formatCategory(category)}`}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          toggleCategoryExpansion(category);
                        }
                      }}
                    >
                      <div className="flex items-center text-xs font-bold text-gray-900 uppercase tracking-tight">
                        <span className="w-1.5 h-1.5 bg-blue-500 rounded-full mr-2"></span>
                        {formatCategory(category)}
                        <span className="ml-3 text-[9px] text-gray-400 bg-white px-2 py-0.5 rounded-full border border-gray-200">
                          {selectedInCat.length} / {allAuthsInCategory.length} Selected
                        </span>
                      </div>
                      <div className="flex items-center space-x-2">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            toggleCategory(category, allAuthsInCategory);
                          }}
                          className="flex items-center space-x-1.5 text-[9px] font-bold uppercase tracking-widest text-blue-600 hover:text-blue-700 transition px-3 py-1.5 bg-white rounded-lg border border-gray-100 shadow-sm"
                        >
                          {isAllSelected ? <CheckSquare className="w-3.5 h-3.5" /> : isSomeSelected ? <MinusSquare className="w-3.5 h-3.5" /> : <Square className="w-3.5 h-3.5" />}
                          <span>{isAllSelected ? 'Deselect All' : 'Select All'}</span>
                        </button>
                      </div>
                    </div>

                    {isExpanded && (
                     <div className="space-y-3">
                      {/* Sub-groups */}
                      {Object.entries(subGroups)
                        .sort(([a], [b]) => {
                          if (a === '') return -1;
                          if (b === '') return 1;
                          return a.localeCompare(b);
                        })
                        .map(([subCategory, auths]) => {
                        if (subCategory === '') {
                          // Render 2-part permissions (no subcategory) in a grid
                          return (
                            <div key="no-sub" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                              {auths.map((auth) => (
                                <label key={auth} className={`flex items-center p-3 rounded-xl border transition cursor-pointer hover:shadow-sm ${formData.authorities.includes(auth) ? 'bg-white border-blue-500 shadow-blue-50' : 'bg-white/50 border-gray-200 hover:border-blue-200'}`}>
                                  <div className={`w-5 h-5 rounded border flex items-center justify-center transition ${formData.authorities.includes(auth) ? 'bg-blue-600 border-blue-600' : 'bg-white border-gray-300'}`}>
                                    {formData.authorities.includes(auth) && <CheckCircle2 className="w-3.5 h-3.5 text-white" />}
                                  </div>
                                  <input
                                    type="checkbox"
                                    className="hidden"
                                    checked={formData.authorities.includes(auth)}
                                    onChange={() => toggleAuthority(auth)}
                                  />
                                  <span className="ml-3 text-[9px] font-bold font-mono text-gray-700 tracking-tighter uppercase truncate" title={auth}>{auth}</span>
                                </label>
                              ))}
                            </div>
                          );
                        }

                        const selectedInSub = auths.filter(a => formData.authorities.includes(a));
                        const isAllSubSelected = selectedInSub.length === auths.length;
                        const isSomeSubSelected = selectedInSub.length > 0 && !isAllSubSelected;

                        return (
                          <div key={subCategory} className="bg-white rounded-xl border border-gray-100 p-4 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                            <div className="flex items-center">
                              <div className="p-2 bg-blue-50 rounded-lg mr-3">
                                <Circle className="w-3 h-3 text-blue-500 fill-blue-500" />
                              </div>
                              <div>
                                <h4 className="text-[11px] font-bold text-gray-900 uppercase tracking-tight">{formatSubCategory(subCategory)}</h4>
                                <p className="text-[8px] text-gray-400 font-bold uppercase tracking-widest mt-0.5">{selectedInSub.length} / {auths.length} Selected</p>
                              </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-2">
                              {auths.map((auth) => (
                                <button
                                  key={auth}
                                  type="button"
                                  onClick={() => toggleAuthority(auth)}
                                  className={`flex items-center px-3 py-1.5 rounded-lg border transition text-[9px] font-bold uppercase tracking-tight ${formData.authorities.includes(auth) ? 'bg-blue-50 border-blue-200 text-blue-700' : 'bg-gray-50 border-transparent text-gray-400 hover:border-gray-200'}`}
                                >
                                  <div className={`w-3.5 h-3.5 rounded border flex items-center justify-center mr-1.5 transition ${formData.authorities.includes(auth) ? 'bg-blue-600 border-blue-600' : 'bg-white border-gray-300'}`}>
                                    {formData.authorities.includes(auth) && <CheckCircle2 className="w-2.5 h-2.5 text-white" />}
                                  </div>
                                  {formatAction(auth)}
                                </button>
                              ))}

                              <div className="h-5 w-px bg-gray-100 mx-1.5 hidden md:block"></div>

                              <button
                                type="button"
                                onClick={() => toggleSubCategory(auths)}
                                className="flex items-center space-x-1 text-[8px] font-bold uppercase tracking-widest text-blue-500 hover:text-blue-600 transition"
                              >
                                {isAllSubSelected ? <CheckSquare className="w-3 h-3" /> : isSomeSubSelected ? <MinusSquare className="w-3 h-3" /> : <Square className="w-3 h-3" />}
                                <span>{isAllSubSelected ? 'Deselect' : 'Select All'}</span>
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                    )}
                  </div>
                );
              })}
              {availableAuthorities.length === 0 && (
                 <div className="py-12 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-xs">No authorities found in system registry.</div>
              )}
            </div>
          </div>

          <div className="flex space-x-4 border-t border-gray-50 pt-6">
            <button type="button" onClick={() => navigate('/roles')} className="flex-1 px-4 py-3 border border-gray-200 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]">Discard</button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50"
            >
              {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
              {isEditing ? 'Commit Authority Mapping' : 'Register Role'}
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default RoleFormPage;
