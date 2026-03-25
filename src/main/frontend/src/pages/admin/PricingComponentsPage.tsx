import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Save, X, Tag, Layers, ChevronDown, ChevronRight, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import StyledSelect from '../../components/StyledSelect';

interface TierCondition {
  attributeName: string;
  operator: string;
  attributeValue: string;
  connector: string;
}

interface PriceValue {
  priceAmount: number;
  valueType: string;
  currency?: string;
}

interface PricingTier {
  id?: number;
  code: string;
  name: string;
  minThreshold?: number;
  maxThreshold?: number;
  applyChargeOnFullBreach?: boolean;
  conditions: TierCondition[];
  priceValue: PriceValue;
}

interface PricingComponent {
  id: number;
  code: string;
  name: string;
  type: string;
  description: string;
  status: string;
  pricingTiers: PricingTier[];
}

const PricingComponentsPage = () => {
  const [components, setComponents] = useState<PricingComponent[]>([]);
  const [metadata, setMetadata] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingComponent, setEditingComponent] = useState<PricingComponent | null>(null);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [formData, setFormData] = useState<any>({
    code: '',
    name: '',
    type: 'FEE',
    description: '',
    pricingTiers: []
  });

  const fetchComponents = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-components');
      setComponents(response.data);
    } catch (err: any) {
      setError('Failed to fetch pricing components.');
    } finally {
      setLoading(false);
    }
  };

  const fetchMetadata = async () => {
    try {
      const response = await axios.get('/api/v1/pricing-metadata');
      setMetadata(response.data);
    } catch (err) {}
  };

  useEffect(() => {
    fetchComponents();
    fetchMetadata();
  }, []);

  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedRows);
    if (newExpanded.has(id)) newExpanded.delete(id);
    else newExpanded.add(id);
    setExpandedRows(newExpanded);
  };

  const openModal = (comp?: PricingComponent) => {
    if (comp) {
      setEditingComponent(comp);
      setFormData({
        code: comp.code,
        name: comp.name,
        type: comp.type,
        description: comp.description,
        pricingTiers: comp.pricingTiers || []
      });
    } else {
      setEditingComponent(null);
      setFormData({
        code: '',
        name: '',
        type: 'FEE',
        description: '',
        pricingTiers: []
      });
    }
    setIsModalOpen(true);
  };

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
    setSuccess('');
    try {
      if (editingComponent) {
        await axios.patch(`/api/v1/pricing-components/${editingComponent.id}`, formData);
        setSuccess('Pricing component updated successfully.');
      } else {
        await axios.post('/api/v1/pricing-components', formData);
        setSuccess('Pricing component created successfully.');
      }
      setIsModalOpen(false);
      fetchComponents();
    } catch (err: any) {
      setError(err.response?.data?.message || 'An error occurred while saving the component.');
    }
  };

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/pricing-components/${id}/activate`);
      setSuccess('Component activated and is now ready for production use.');
      fetchComponents();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Activation failed.');
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Components cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/pricing-components/${id}`);
      setSuccess('Component deleted.');
      fetchComponents();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Deletion failed.');
    }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6 pb-20">
      <div className="bg-white rounded-[2rem] p-8 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-6">
          <div className="p-4 bg-purple-50 rounded-2xl"><Tag className="w-8 h-8 text-purple-600" /></div>
          <div>
            <h1 className="text-3xl font-black text-gray-900 tracking-tight">Pricing Components</h1>
            <p className="text-gray-500 font-medium mt-1">Manage reusable pricing structures with multi-tier logic.</p>
          </div>
        </div>
        <button
          onClick={() => openModal()}
          className="bg-blue-600 text-white px-7 py-3 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-xs"
        >
          <Plus className="w-5 h-5 mr-2" /> New Component
        </button>
      </div>

      <div className="bg-blue-50/50 border-l-4 border-blue-400 p-6 rounded-r-2xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-blue-100 rounded-lg"><Info className="w-5 h-5 text-blue-700" /></div>
        <p className="text-sm text-blue-900 font-medium leading-relaxed italic">
          <strong>Expert Guidance:</strong> Components define the "what" (e.g., Monthly Fee), while Tiers define the "when" and "how much" (e.g., $10 for RETAIL segment).
          Linking a component to a product enables these rules to execute during bundle pricing.
        </p>
      </div>

      {error && <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-r-xl text-red-700 text-sm font-bold flex items-center shadow-sm"><AlertCircle className="w-4 h-4 mr-3" />{error}</div>}
      {success && <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded-r-xl text-green-700 text-sm font-bold flex items-center shadow-sm"><CheckCircle2 className="w-4 h-4 mr-3" />{success}</div>}

      {loading ? (
        <div className="flex justify-center p-24 bg-white rounded-3xl border border-gray-100"><Loader2 className="w-12 h-12 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="w-12 px-6 py-5"></th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Aggregate Component</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Type</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Segments</th>
                <th className="px-6 py-5 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {components.map((comp) => (
                <React.Fragment key={comp.id}>
                  <tr className="hover:bg-gray-50 transition cursor-pointer group" onClick={() => toggleExpand(comp.id)}>
                    <td className="px-6 py-6 text-center">
                      {expandedRows.has(comp.id) ? <ChevronDown className="w-5 h-5 text-blue-600 font-black" /> : <ChevronRight className="w-5 h-5 text-gray-300 group-hover:text-blue-400" />}
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <div className="text-sm font-black text-gray-900 leading-tight">{comp.name}</div>
                      <div className="text-[10px] text-gray-400 font-mono mt-1 tracking-widest">{comp.code}</div>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${comp.type === 'FEE' ? 'bg-purple-50 text-purple-700 border border-purple-100' : 'bg-amber-50 text-amber-700 border border-amber-100'}`}>
                        {comp.type}
                      </span>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${comp.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                        {comp.status}
                      </span>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap text-xs font-bold text-gray-500">
                      {comp.pricingTiers?.length || 0} Tiered Segments
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap text-right text-sm font-medium space-x-1">
                      {comp.status === 'DRAFT' && (
                        <button onClick={(e) => { e.stopPropagation(); handleActivate(comp.id); }} className="text-green-600 hover:bg-green-50 p-2.5 rounded-xl transition border border-transparent hover:border-green-100" title="Activate Production Mode"><CheckCircle2 className="w-4 h-4" /></button>
                      )}
                      <button onClick={(e) => { e.stopPropagation(); openModal(comp); }} className="text-blue-600 hover:bg-blue-50 p-2.5 rounded-xl transition border border-transparent hover:border-blue-100" title="Edit Structure"><Edit2 className="w-4 h-4" /></button>
                      <button onClick={(e) => { e.stopPropagation(); handleDelete(comp.id); }} className="text-red-600 hover:bg-red-50 p-2.5 rounded-xl transition border border-transparent hover:border-red-100" title="Permanently Delete"><Trash2 className="w-4 h-4" /></button>
                    </td>
                  </tr>
                  {expandedRows.has(comp.id) && (
                    <tr className="bg-gray-50/30">
                      <td colSpan={6} className="px-16 py-8 border-b border-gray-100">
                        <div className="text-sm font-medium text-gray-600 mb-8 italic leading-relaxed border-l-2 border-gray-200 pl-4">{comp.description}</div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                          {comp.pricingTiers?.map((tier, idx) => (
                            <div key={idx} className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm hover:shadow-md transition">
                              <div className="flex justify-between items-start mb-6">
                                <div>
                                  <div className="text-xs font-black text-gray-400 uppercase tracking-widest mb-1">Tier Code: {tier.code}</div>
                                  <h4 className="font-black text-gray-900 text-lg leading-tight">{tier.name}</h4>
                                </div>
                                <div className="text-right">
                                  <div className="text-2xl font-black text-blue-600 tracking-tight">{tier.priceValue.priceAmount}</div>
                                  <div className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{tier.priceValue.valueType}</div>
                                </div>
                              </div>
                              <div className="space-y-2">
                                <div className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2 border-b border-gray-50 pb-1">Calculation Conditions</div>
                                <div className="flex flex-wrap gap-2">
                                  {tier.conditions?.map((c, cidx) => (
                                    <div key={cidx} className="bg-blue-50/50 text-blue-800 px-3 py-1.5 rounded-xl text-[11px] font-bold border border-blue-100/50 flex items-center">
                                      <span className="text-blue-400 mr-2">{c.attributeName}</span>
                                      <span className="bg-white px-1.5 py-0.5 rounded text-blue-600 font-black mx-1">{c.operator}</span>
                                      <span className="text-blue-900">{c.attributeValue}</span>
                                      {cidx < tier.conditions.length - 1 && <span className="ml-2 font-black text-blue-300 uppercase tracking-tighter text-[9px]">{c.connector}</span>}
                                    </div>
                                  ))}
                                  {(!tier.conditions || tier.conditions.length === 0) && <div className="text-xs text-gray-400 italic">No segment conditions (Catch-all Tier)</div>}
                                </div>
                              </div>
                            </div>
                          ))}
                          {(!comp.pricingTiers || comp.pricingTiers.length === 0) && (
                            <div className="col-span-2 py-12 text-center text-gray-400 font-medium italic border-2 border-dashed rounded-3xl">This component has no logic defined. Click Edit to add tiers.</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isModalOpen && (
        <div className="fixed inset-0 bg-gray-900/60 backdrop-blur-md flex items-center justify-center z-50 overflow-y-auto p-4 md:p-10">
          <div className="bg-white rounded-[3rem] max-w-5xl w-full shadow-2xl overflow-hidden my-auto border border-white/20 animate-in zoom-in-95 duration-200">
            <div className="px-10 py-8 bg-blue-900 text-white flex justify-between items-center relative">
              <div className="absolute top-0 right-0 w-64 h-full bg-blue-800 -skew-x-12 translate-x-32 opacity-20"></div>
              <div className="relative">
                <h2 className="text-3xl font-black tracking-tight">{editingComponent ? 'Sync Pricing Aggregate' : 'New Pricing Structure'}</h2>
                <p className="text-blue-200 font-medium mt-1">Configure the core identity and tiered logic in one atomic operation.</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="hover:bg-blue-800 p-3 rounded-full transition relative"><X className="w-8 h-8" /></button>
            </div>
            <form onSubmit={handleSubmit} className="p-10 space-y-10 max-h-[75vh] overflow-y-auto custom-scrollbar">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div>
                  <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Immutable Component Code</label>
                  <input type="text" required className="w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-bold text-blue-700 transition focus:border-blue-500" value={formData.code} onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})} placeholder="e.g. MAINTENANCE_FEE" />
                </div>
                <div>
                  <label className="block text-xs font-black text-gray-400 uppercase tracking-widest mb-3">Product-Facing Name</label>
                  <input type="text" required className="w-full border-2 border-gray-100 rounded-2xl p-4 font-bold transition focus:border-blue-500" value={formData.name} onChange={(e) => setFormData({...formData, name: e.target.value})} placeholder="e.g. Monthly Maintenance Fee" />
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
                  <textarea className="w-full border-2 border-gray-100 rounded-2xl p-4 h-28 font-medium transition focus:border-blue-500" value={formData.description} onChange={(e) => setFormData({...formData, description: e.target.value})} placeholder="Describe the purpose and lifecycle of this pricing component..." />
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
                                <StyledSelect
                                  containerClassName="col-span-5"
                                  className="border-white rounded-xl p-3 text-xs bg-white font-bold shadow-sm focus:border-blue-500"
                                  value={cond.attributeName}
                                  onChange={(e) => handleConditionChange(idx, cidx, 'attributeName', e.target.value)}
                                >
                                  <option value="">Attribute...</option>
                                  {metadata.map(m => <option key={m.id} value={m.attributeKey}>{m.displayName}</option>)}
                                </StyledSelect>
                                <StyledSelect
                                  containerClassName="col-span-2"
                                  className="border-white rounded-xl p-3 text-xs bg-white font-black shadow-sm focus:border-blue-500 text-center"
                                  value={cond.operator}
                                  onChange={(e) => handleConditionChange(idx, cidx, 'operator', e.target.value)}
                                >
                                  <option value="EQ">=</option>
                                  <option value="GT">&gt;</option>
                                  <option value="LT">&lt;</option>
                                  <option value="GE">&gt;=</option>
                                  <option value="LE">&lt;=</option>
                                </StyledSelect>
                                <input type="text" className="col-span-3 border-2 border-white rounded-xl p-3 text-xs bg-white font-bold shadow-sm focus:border-blue-500 transition" placeholder="Value..." value={cond.attributeValue} onChange={(e) => handleConditionChange(idx, cidx, 'attributeValue', e.target.value)} />
                                <div className="col-span-2">
                                  {cidx < tier.conditions.length - 1 ? (
                                    <StyledSelect
                                      containerClassName="w-full"
                                      className="border-white rounded-xl p-3 text-xs bg-blue-50 font-black shadow-sm text-blue-600 text-center"
                                      value={cond.connector}
                                      onChange={(e) => handleConditionChange(idx, cidx, 'connector', e.target.value)}
                                    >
                                      <option value="AND">AND</option>
                                      <option value="OR">OR</option>
                                    </StyledSelect>
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
                            <input type="number" step="0.01" required className="w-full border-2 border-gray-50 rounded-xl p-4 pl-8 font-black text-lg text-blue-900 transition focus:border-blue-500 focus:ring-0" value={tier.priceValue.priceAmount} onChange={(e) => {
                              const newTiers = [...formData.pricingTiers];
                              newTiers[idx].priceValue = { ...newTiers[idx].priceValue, priceAmount: parseFloat(e.target.value) };
                              setFormData({...formData, pricingTiers: newTiers});
                            }} />
                          </div>
                        </div>
                        <div>
                          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Financial Value Type</label>
                          <StyledSelect className="border-gray-50 rounded-xl p-4 font-black text-xs uppercase tracking-widest bg-gray-50 focus:border-blue-500 focus:ring-0" value={tier.priceValue.valueType} onChange={(e) => {
                            const newTiers = [...formData.pricingTiers];
                            newTiers[idx].priceValue = { ...newTiers[idx].priceValue, valueType: e.target.value };
                            setFormData({...formData, pricingTiers: newTiers});
                          }}>
                            <option value="ABSOLUTE">ABSOLUTE VALUE</option>
                            <option value="PERCENTAGE">PERCENTAGE RATE</option>
                            <option value="DISCOUNT_ABSOLUTE">CASH DISCOUNT</option>
                            <option value="DISCOUNT_PERCENTAGE">PERCENTAGE DISCOUNT</option>
                          </StyledSelect>
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
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-2xl font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard Changes</button>
                <button type="submit" className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-2xl font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs">
                  <Save className="w-6 h-6 mr-3" /> Commit Structure
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PricingComponentsPage;
