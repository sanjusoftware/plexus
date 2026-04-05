import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Save, X, Shield, CheckCircle2, Loader2, Lock, CheckSquare, Square, MinusSquare, Circle } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';

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

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [authsRes, mappingsRes] = await Promise.all([
          axios.get('/api/v1/roles/authorities'),
          isEditing ? axios.get('/api/v1/roles/mapping') : Promise.resolve({ data: [] })
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
        setToast({ message: 'Failed to fetch required data.', type: 'error' });
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [roleName, isEditing, setEntityName]);

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
      <div className="flex justify-center p-32">
        <Loader2 className="w-16 h-16 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto pb-20 space-y-8">
      {/* Header */}
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center overflow-hidden relative">
        <div className="absolute top-0 right-0 w-80 h-full bg-blue-50 -skew-x-12 translate-x-40 opacity-30"></div>
        <div className="flex items-center space-x-6 relative">
          <div className="p-4 bg-blue-100 rounded-2xl shadow-inner shadow-blue-200">
            <Shield className="w-10 h-10 text-blue-700" />
          </div>
          <div>
            <h1 className="text-4xl font-black text-gray-900 tracking-tight uppercase">
              {isEditing ? 'Authority Configuration' : 'Register New Role'}
            </h1>
            <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">
              {isEditing ? `Modifying system permissions for role ${roleName}` : 'Define a new role and select its system permissions'}
            </p>
          </div>
        </div>
        <button
          onClick={() => navigate('/roles')}
          className="bg-gray-100 text-gray-500 p-4 rounded-2xl hover:bg-gray-200 transition relative z-10"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="bg-white rounded-[3rem] shadow-sm border border-gray-100 overflow-hidden">
        <form onSubmit={handleSubmit} className="p-12 space-y-12">
          <div className="bg-blue-50/50 p-10 rounded-[2.5rem] border border-blue-100/50 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-64 h-full bg-blue-100/20 -skew-x-12 translate-x-32"></div>
            <div className="relative">
              <label className="block text-[10px] font-black text-blue-400 uppercase tracking-widest mb-4">Role Name (Key)</label>
              <div className="relative max-w-2xl">
                <Lock className="absolute left-6 top-1/2 -translate-y-1/2 w-6 h-6 text-blue-300" />
                <input
                  type="text"
                  required
                  disabled={isEditing}
                  className={`w-full border-2 border-white rounded-[1.5rem] p-6 pl-16 font-mono font-black text-2xl text-blue-700 transition focus:border-blue-500 shadow-sm uppercase placeholder:text-blue-100 ${isEditing ? 'bg-blue-100/30 cursor-not-allowed border-transparent' : 'bg-white'}`}
                  value={formData.name}
                  onChange={(e) => {
                    setFormData({...formData, name: e.target.value.toUpperCase()});
                  }}
                  placeholder="e.g. CUSTOMER_SUPPORT"
                />
              </div>
              <p className="mt-4 text-[11px] text-blue-400/80 font-bold italic">This must match the exact string sent by your Identity Provider in the <code>roles</code> claim.</p>
            </div>
          </div>

          <div>
            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-8 px-4">Select Authorized Operations</label>
            <div className="space-y-10">
              {Object.entries(groupAuthorities(availableAuthorities)).map(([category, subGroups]) => {
                const allAuthsInCategory = Object.values(subGroups).flat();
                const selectedInCat = allAuthsInCategory.filter(a => formData.authorities.includes(a));
                const isAllSelected = selectedInCat.length === allAuthsInCategory.length;
                const isSomeSelected = selectedInCat.length > 0 && !isAllSelected;

                return (
                  <div key={category} className="bg-gray-50/50 rounded-[2.5rem] border border-gray-100 p-8">
                    <div className="flex items-center justify-between mb-8 pb-4 border-b border-gray-200/50">
                      <h3 className="text-sm font-black text-gray-900 uppercase tracking-tight flex items-center">
                        <span className="w-2 h-2 bg-blue-500 rounded-full mr-3"></span>
                        {formatCategory(category)}
                        <span className="ml-4 text-[10px] text-gray-400 bg-white px-3 py-1 rounded-full border border-gray-200">
                          {selectedInCat.length} / {allAuthsInCategory.length} Selected
                        </span>
                      </h3>
                      <button
                        type="button"
                        onClick={() => toggleCategory(category, allAuthsInCategory)}
                        className="flex items-center space-x-2 text-[10px] font-black uppercase tracking-widest text-blue-600 hover:text-blue-700 transition px-4 py-2 bg-white rounded-xl border border-gray-100 shadow-sm"
                      >
                        {isAllSelected ? <CheckSquare className="w-4 h-4" /> : isSomeSelected ? <MinusSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                        <span>{isAllSelected ? 'Deselect All' : 'Select All'}</span>
                      </button>
                    </div>

                    <div className="space-y-6">
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
                            <div key="no-sub" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                              {auths.map((auth) => (
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
                                  <span className="ml-4 text-[10px] font-black font-mono text-gray-700 tracking-tighter uppercase truncate" title={auth}>{auth}</span>
                                </label>
                              ))}
                            </div>
                          );
                        }

                        const selectedInSub = auths.filter(a => formData.authorities.includes(a));
                        const isAllSubSelected = selectedInSub.length === auths.length;
                        const isSomeSubSelected = selectedInSub.length > 0 && !isAllSubSelected;

                        return (
                          <div key={subCategory} className="bg-white rounded-3xl border border-gray-100 p-6 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-6">
                            <div className="flex items-center">
                              <div className="p-3 bg-blue-50 rounded-xl mr-4">
                                <Circle className="w-4 h-4 text-blue-500 fill-blue-500" />
                              </div>
                              <div>
                                <h4 className="text-xs font-black text-gray-900 uppercase tracking-tight">{formatSubCategory(subCategory)}</h4>
                                <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mt-0.5">{selectedInSub.length} / {auths.length} Selected</p>
                              </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-3">
                              {auths.map((auth) => (
                                <button
                                  key={auth}
                                  type="button"
                                  onClick={() => toggleAuthority(auth)}
                                  className={`flex items-center px-4 py-2 rounded-xl border-2 transition text-[10px] font-black uppercase tracking-tight ${formData.authorities.includes(auth) ? 'bg-blue-50 border-blue-200 text-blue-700' : 'bg-gray-50 border-transparent text-gray-400 hover:border-gray-200'}`}
                                >
                                  <div className={`w-4 h-4 rounded-md border flex items-center justify-center mr-2 transition ${formData.authorities.includes(auth) ? 'bg-blue-600 border-blue-600' : 'bg-white border-gray-300'}`}>
                                    {formData.authorities.includes(auth) && <CheckCircle2 className="w-3 h-3 text-white" />}
                                  </div>
                                  {formatAction(auth)}
                                </button>
                              ))}

                              <div className="h-6 w-px bg-gray-100 mx-2 hidden md:block"></div>

                              <button
                                type="button"
                                onClick={() => toggleSubCategory(auths)}
                                className="flex items-center space-x-1.5 text-[9px] font-black uppercase tracking-widest text-blue-500 hover:text-blue-600 transition"
                              >
                                {isAllSubSelected ? <CheckSquare className="w-3.5 h-3.5" /> : isSomeSubSelected ? <MinusSquare className="w-3.5 h-3.5" /> : <Square className="w-3.5 h-3.5" />}
                                <span>{isAllSubSelected ? 'Deselect' : 'Select All'}</span>
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
              {availableAuthorities.length === 0 && (
                 <div className="py-24 text-center text-gray-400 bg-white border-4 border-dashed rounded-[3rem] font-black uppercase tracking-widest text-xs">No authorities found in system registry.</div>
              )}
            </div>
          </div>

          <div className="pt-8 border-t border-gray-50 flex space-x-6">
            <button type="button" onClick={() => navigate('/roles')} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-[2rem] font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard</button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-[2rem] font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs disabled:opacity-50"
            >
              {submitting ? <Loader2 className="w-6 h-6 animate-spin mr-3" /> : <Save className="w-6 h-6 mr-3" />}
              {isEditing ? 'Commit Authority Mapping' : 'Register Role'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RoleFormPage;
