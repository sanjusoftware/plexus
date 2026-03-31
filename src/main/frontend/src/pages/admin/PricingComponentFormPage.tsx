import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Save, X, Layers, AlertCircle } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import PlexusSelect from '../../components/PlexusSelect';

const PricingComponentFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const isEditing = !!id;

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [metadata, setMetadata] = useState<any[]>([]);
  const [isCodeEdited, setIsCodeEdited] = useState(false);
  const [error, setError] = useState('');

  const [formData, setFormData] = useState<any>({
    code: '',
    name: '',
    type: 'FEE',
    description: '',
    pricingTiers: []
  });

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [metaRes, compRes] = await Promise.all([
          axios.get('/api/v1/pricing-metadata'),
          isEditing ? axios.get('/api/v1/pricing-components') : Promise.resolve({ data: [] })
        ]);

        setMetadata(metaRes.data || []);

        if (isEditing) {
          const comp = (compRes.data as any[]).find(c => c.id.toString() === id);
          if (comp) {
            setFormData({
              code: comp.code,
              name: comp.name,
              type: comp.type,
              description: comp.description,
              pricingTiers: comp.pricingTiers || []
            });
            setEntityName(comp.name);
            setIsCodeEdited(true);
          } else {
            setError('Pricing component not found.');
          }
        }
      } catch (err: any) {
        setError('Failed to fetch required data.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id, isEditing, setEntityName]);

  const handleTierChange = (index: number, field: string, value: any) => {
    const newTiers = [...formData.pricingTiers];
    newTiers[index] = { ...newTiers[index], [field]: value };
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const addTier = () => {
    setFormData({
      ...formData,
      pricingTiers: [...formData.pricingTiers, {
        code: '',
        name: '',
        conditions: [],
        priceValue: { priceAmount: 0, valueType: 'ABSOLUTE' }
      }]
    });
  };

  const removeTier = (index: number) => {
    const newTiers = [...formData.pricingTiers];
    newTiers.splice(index, 1);
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const handleConditionChange = (tierIdx: number, condIdx: number, field: string, value: any) => {
    const newTiers = [...formData.pricingTiers];
    const newConds = [...newTiers[tierIdx].conditions];
    newConds[condIdx] = { ...newConds[condIdx], [field]: value };
    newTiers[tierIdx].conditions = newConds;
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const addCondition = (tierIdx: number) => {
    const newTiers = [...formData.pricingTiers];
    newTiers[tierIdx].conditions = [...(newTiers[tierIdx].conditions || []), {
      attributeName: '', operator: 'EQ', attributeValue: '', connector: 'AND'
    }];
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const removeCondition = (tierIdx: number, condIdx: number) => {
    const newTiers = [...formData.pricingTiers];
    const newConds = [...newTiers[tierIdx].conditions];
    newConds.splice(condIdx, 1);
    newTiers[tierIdx].conditions = newConds;
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      if (isEditing) {
        await axios.patch(`/api/v1/pricing-components/${id}`, formData);
      } else {
        await axios.post('/api/v1/pricing-components', formData);
      }
      navigate('/pricing-components', { state: { success: isEditing ? 'Pricing component updated successfully.' : 'Pricing component created successfully.' } });
    } catch (err: any) {
      setError(err.response?.data?.message || 'An error occurred while saving the component.');
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
    <div className="max-w-5xl mx-auto space-y-8 pb-20">
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-full bg-blue-50 -skew-x-12 translate-x-32 opacity-30"></div>
        <div className="relative">
          <h1 className="text-3xl font-black text-gray-900 tracking-tight uppercase">
            {isEditing ? 'Update Pricing' : 'New Pricing'}
          </h1>
          <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">
            Configure the core identity and tiered logic in one atomic operation.
          </p>
        </div>
        <button
          onClick={() => navigate('/pricing-components')}
          className="bg-gray-50 text-gray-400 p-3 rounded-2xl hover:bg-gray-100 transition relative border border-gray-100 shadow-sm"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="bg-white rounded-[2.5rem] shadow-sm overflow-hidden border border-gray-100">
        <form onSubmit={handleSubmit} className="p-10 space-y-10">
          {error && (
            <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
              <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
              <p className="text-sm font-bold">{error}</p>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <div>
              <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Immutable Component Code</label>
              <input
                type="text"
                required
                className="w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-bold text-blue-700 transition focus:border-blue-500 shadow-sm"
                value={formData.code}
                onChange={(e) => {
                  setIsCodeEdited(true);
                  setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                }}
                placeholder="e.g. MAINTENANCE_FEE"
              />
            </div>
            <div>
              <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Product-Facing Name</label>
              <input
                type="text"
                required
                className="w-full border-2 border-gray-100 rounded-2xl p-4 font-bold transition focus:border-blue-500 shadow-sm"
                value={formData.name}
                onChange={(e) => {
                  const name = e.target.value;
                  let code = formData.code;
                  if (!isEditing && !isCodeEdited) {
                    code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                  }
                  setFormData({ ...formData, name, code });
                }}
                placeholder="e.g. Monthly Maintenance Fee"
              />
            </div>
            <div>
              <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Financial Type</label>
              <div className="flex space-x-4">
                {['FEE', 'RATE'].map(t => (
                  <button key={t} type="button" onClick={() => setFormData({...formData, type: t})} className={`flex-1 py-4 rounded-2xl font-black text-xs uppercase tracking-widest transition border-2 ${formData.type === t ? 'bg-blue-600 border-blue-600 text-white shadow-lg shadow-blue-100' : 'border-gray-100 text-gray-400 hover:border-gray-200'}`}>{t}</button>
                ))}
              </div>
            </div>
            <div className="md:col-span-2">
              <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Business Description</label>
              <textarea className="w-full border-2 border-gray-100 rounded-2xl p-4 h-28 font-medium transition focus:border-blue-500 shadow-sm" value={formData.description} onChange={(e) => setFormData({...formData, description: e.target.value})} placeholder="Describe the purpose and lifecycle of this pricing component..." />
            </div>
          </div>

          <div className="border-t border-gray-100 pt-10">
            <div className="flex justify-between items-center mb-8">
              <div className="flex items-center space-x-3">
                <div className="p-2.5 bg-purple-50 rounded-xl"><Layers className="w-6 h-6 text-purple-600" /></div>
                <h3 className="text-2xl font-black text-gray-900 tracking-tight">Segment Pricing Tiers</h3>
              </div>
              <button type="button" onClick={addTier} className="bg-purple-600 text-white px-5 py-2.5 rounded-xl flex items-center font-bold text-xs uppercase tracking-widest hover:bg-purple-700 transition shadow-lg shadow-purple-50">
                <Plus className="w-4 h-4 mr-2" /> Add Logic Tier
              </button>
            </div>

            <div className="space-y-8">
              {formData.pricingTiers.map((tier: any, idx: number) => (
                <div key={idx} className="rounded-3xl p-8 bg-gray-50/50 border-2 border-gray-100 relative group transition hover:border-blue-200">
                  <button type="button" onClick={() => removeTier(idx)} className="absolute top-6 right-6 p-2 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-5 h-5" /></button>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
                    <div>
                      <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Tier ID Code</label>
                      <input type="text" required className="w-full border-2 border-white rounded-xl p-3 font-mono font-bold text-xs bg-white shadow-sm focus:border-blue-500 transition" value={tier.code} onChange={(e) => handleTierChange(idx, 'code', e.target.value.toUpperCase())} />
                    </div>
                    <div>
                      <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Display Label</label>
                      <input type="text" required className="w-full border-2 border-white rounded-xl p-3 font-bold text-xs bg-white shadow-sm focus:border-blue-500 transition" value={tier.name} onChange={(e) => handleTierChange(idx, 'name', e.target.value)} />
                    </div>
                  </div>

                  <div className="mb-8">
                    <div className="flex justify-between items-center mb-4">
                      <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Pricing Rules (Conditions)</label>
                      <button type="button" onClick={() => addCondition(idx)} className="text-blue-600 hover:text-blue-700 text-[10px] font-black uppercase tracking-widest bg-white px-3 py-1.5 rounded-lg shadow-sm border border-blue-50">+ New Condition</button>
                    </div>
                    <div className="space-y-3">
                      {tier.conditions?.map((cond: any, cidx: number) => (
                        <div key={cidx} className="flex items-center space-x-3 animate-in slide-in-from-left-2 duration-200">
                          <div className="flex-1 grid grid-cols-12 gap-2">
                            <div className="col-span-5">
                              <PlexusSelect
                                placeholder="Attribute..."
                                options={metadata.map(m => ({ value: m.attributeKey, label: m.displayName }))}
                                value={metadata.find(m => m.attributeKey === cond.attributeName) ? { value: cond.attributeName, label: metadata.find(m => m.attributeKey === cond.attributeName).displayName } : null}
                                onChange={(opt) => handleConditionChange(idx, cidx, 'attributeName', opt ? opt.value : '')}
                                menuPlacement="auto"
                              />
                            </div>
                            <div className="col-span-2">
                              <PlexusSelect
                                options={[
                                  { value: 'EQ', label: '=' },
                                  { value: 'GT', label: '>' },
                                  { value: 'LT', label: '<' },
                                  { value: 'GE', label: '>=' },
                                  { value: 'LE', label: '<=' }
                                ]}
                                value={{ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' }[cond.operator as string] ? { value: cond.operator, label: ({ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' } as any)[cond.operator] } : null}
                                onChange={(opt) => handleConditionChange(idx, cidx, 'operator', opt ? opt.value : 'EQ')}
                                menuPlacement="auto"
                              />
                            </div>
                            <input type="text" className="col-span-3 border-2 border-gray-100 rounded-2xl p-4 text-xs bg-white font-bold shadow-sm focus:border-blue-500 transition h-[60px]" placeholder="Value..." value={cond.attributeValue} onChange={(e) => handleConditionChange(idx, cidx, 'attributeValue', e.target.value)} />
                            <div className="col-span-2">
                              {cidx < tier.conditions.length - 1 ? (
                                <PlexusSelect
                                  options={[
                                    { value: 'AND', label: 'AND' },
                                    { value: 'OR', label: 'OR' }
                                  ]}
                                  value={{ AND: 'AND', OR: 'OR' }[cond.connector as string] ? { value: cond.connector, label: cond.connector } : null}
                                  onChange={(opt) => handleConditionChange(idx, cidx, 'connector', opt ? opt.value : 'AND')}
                                  menuPlacement="auto"
                                />
                              ) : <div className="w-full p-3"></div>}
                            </div>
                          </div>
                          <button type="button" onClick={() => removeCondition(idx, cidx)} className="p-2 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-lg transition"><X className="w-4 h-4" /></button>
                        </div>
                      ))}
                      {(!tier.conditions || tier.conditions.length === 0) && (
                        <div className="bg-white/50 border-2 border-dashed border-gray-200 rounded-2xl p-4 text-center text-xs text-gray-400 font-medium">Default Tier: This price applies if no other tiered segments match.</div>
                      )}
                    </div>
                  </div>

                  <div className="bg-white p-6 rounded-3xl border-2 border-blue-50 grid grid-cols-2 gap-8 shadow-sm">
                    <div>
                      <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Calculated Price Amount</label>
                      <div className="relative">
                        <span className="absolute left-4 top-1/2 -translate-y-1/2 font-black text-blue-600">$</span>
                        <input type="number" step="0.01" required className="w-full border-2 border-gray-50 rounded-xl p-4 pl-8 font-black text-lg text-blue-900 transition focus:border-blue-500 focus:ring-0 shadow-sm" value={tier.priceValue.priceAmount} onChange={(e) => {
                          const newTiers = [...formData.pricingTiers];
                          newTiers[idx].priceValue = { ...newTiers[idx].priceValue, priceAmount: parseFloat(e.target.value) };
                          setFormData({...formData, pricingTiers: newTiers});
                        }} />
                      </div>
                    </div>
                    <div>
                      <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Financial Value Type</label>
                      <PlexusSelect
                        options={[
                          { value: 'ABSOLUTE', label: 'ABSOLUTE VALUE' },
                          { value: 'PERCENTAGE', label: 'PERCENTAGE RATE' },
                          { value: 'DISCOUNT_ABSOLUTE', label: 'CASH DISCOUNT' },
                          { value: 'DISCOUNT_PERCENTAGE', label: 'PERCENTAGE DISCOUNT' }
                        ]}
                        value={{
                          ABSOLUTE: 'ABSOLUTE VALUE',
                          PERCENTAGE: 'PERCENTAGE RATE',
                          DISCOUNT_ABSOLUTE: 'CASH DISCOUNT',
                          DISCOUNT_PERCENTAGE: 'PERCENTAGE DISCOUNT'
                        }[tier.priceValue.valueType as string] ? {
                          value: tier.priceValue.valueType,
                          label: ({ ABSOLUTE: 'ABSOLUTE VALUE', PERCENTAGE: 'PERCENTAGE RATE', DISCOUNT_ABSOLUTE: 'CASH DISCOUNT', DISCOUNT_PERCENTAGE: 'PERCENTAGE DISCOUNT' } as any)[tier.priceValue.valueType]
                        } : null}
                        onChange={(opt) => {
                          const newTiers = [...formData.pricingTiers];
                          newTiers[idx].priceValue = { ...newTiers[idx].priceValue, valueType: opt ? opt.value : 'ABSOLUTE' };
                          setFormData({...formData, pricingTiers: newTiers});
                        }}
                      />
                    </div>
                  </div>
                </div>
              ))}
              {formData.pricingTiers.length === 0 && (
                <div className="text-center py-16 border-4 border-dashed rounded-[2rem] text-gray-300 font-black uppercase tracking-widest text-xs">
                  No Segment Logic Defined
                </div>
              )}
            </div>
          </div>

          <div className="pt-10 border-t border-gray-100 flex space-x-6">
            <button type="button" onClick={() => navigate('/pricing-components')} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-2xl font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard Changes</button>
            <button type="submit" disabled={submitting} className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-2xl font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs disabled:opacity-50">
              {submitting ? <Loader2 className="w-6 h-6 animate-spin mr-3" /> : <Save className="w-6 h-6 mr-3" />}
              Commit Structure
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PricingComponentFormPage;
