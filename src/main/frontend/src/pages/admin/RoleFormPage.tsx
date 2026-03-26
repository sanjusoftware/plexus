import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Save, X, Shield, CheckCircle2, AlertCircle, Loader2, Lock } from 'lucide-react';

interface RoleMapping {
  roleName: string;
  authorities: string[];
}

const RoleFormPage = () => {
  const { roleName } = useParams<{ roleName?: string }>();
  const navigate = useNavigate();
  const isEditing = !!roleName;

  const [availableAuthorities, setAvailableAuthorities] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState<RoleMapping>({ roleName: roleName || '', authorities: [] });
  const [error, setError] = useState('');
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
          const mapping = (mappingsRes.data as RoleMapping[]).find(m => m.roleName === roleName);
          if (mapping) {
            setFormData({ roleName: mapping.roleName, authorities: [...mapping.authorities] });
          } else {
            setError(`Role "${roleName}" not found.`);
          }
        }
      } catch (err: any) {
        setError('Failed to fetch required data.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [roleName, isEditing]);

  const toggleAuthority = (auth: string) => {
    const newAuths = new Set(formData.authorities);
    if (newAuths.has(auth)) newAuths.delete(auth);
    else newAuths.add(auth);
    setFormData({ ...formData, authorities: Array.from(newAuths) });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await axios.post('/api/v1/roles/mapping', formData);
      navigate('/roles', { state: { success: 'Permissions for the role have been successfully synchronized.' } });
    } catch (err: any) {
      setError(err.response?.data?.message || 'Role mapping update failed.');
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
    <div className="max-w-4xl mx-auto pb-20">
      <div className="bg-white rounded-[3.5rem] shadow-2xl overflow-hidden border border-white/20 animate-in zoom-in-95 duration-200">
        <div className="px-12 py-10 bg-blue-900 text-white flex justify-between items-center relative overflow-hidden">
          <div className="absolute top-0 right-0 w-80 h-full bg-blue-800 -skew-x-12 translate-x-32 opacity-30"></div>
          <div className="relative">
            <h2 className="text-4xl font-black tracking-tighter uppercase">
              {isEditing ? 'Authority Configuration' : 'Register New Role'}
            </h2>
            <p className="text-blue-200 font-bold mt-1 text-sm">
              {isEditing ? `Modify system permissions for role ${roleName}.` : 'Define a new role and select its system permissions.'}
            </p>
          </div>
          <button onClick={() => navigate('/roles')} className="hover:bg-blue-800 p-3 rounded-full transition relative border border-white/10 shadow-lg">
            <X className="w-8 h-8" />
          </button>
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
                disabled={isEditing}
                className={`w-full border-2 border-white rounded-2xl p-4 pl-12 font-mono font-black text-lg text-blue-700 transition focus:border-blue-500 shadow-sm uppercase placeholder:text-gray-200 ${isEditing ? 'bg-gray-100 cursor-not-allowed' : ''}`}
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
                 <div className="col-span-full py-12 text-center text-gray-400 font-bold italic">No authorities found in system registry.</div>
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
