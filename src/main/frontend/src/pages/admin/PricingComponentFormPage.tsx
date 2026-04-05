import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Save, X, Layers } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import PlexusSelect from '../../components/PlexusSelect';
import { useAuth } from '../../context/AuthContext';

const PricingComponentFormPage = () => {
  const { user, setToast } = useAuth();
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const isEditing = !!id;
  const currencyCode = user?.currencyCode || 'USD';

  const getCurrencySymbol = (code: string) => {
    const symbols: Record<string, string> = {
      'USD': '$',
      'EUR': '€',
      'GBP': '£',
      'JPY': '¥',
      'INR': '₹',
      'AUD': 'A$',
      'CAD': 'C$',
      'CHF': 'CHF',
      'CNY': '¥',
      'HKD': 'HK$',
      'NZD': 'NZ$',
      'SEK': 'kr',
      'KRW': '₩',
      'SGD': 'S$',
      'NOK': 'kr',
      'MXN': '$',
    };
    return symbols[code] || code;
  };

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [metadata, setMetadata] = useState<any[]>([]);
  const [isCodeEdited, setIsCodeEdited] = useState(false);

  const [formData, setFormData] = useState<any>({
    code: '',
    name: '',
    type: 'FEE',
    description: '',
    pricingTiers: []
  });

  const getOperatorsForDataType = (dataType: string) => {
    const allOps = [
      { value: 'EQ', label: '=' },
      { value: 'GT', label: '>' },
      { value: 'LT', label: '<' },
      { value: 'GE', label: '>=' },
      { value: 'LE', label: '<=' }
    ];

    switch (dataType) {
      case 'BOOLEAN':
      case 'STRING':
        return allOps.filter(op => op.value === 'EQ');
      case 'INTEGER':
      case 'DECIMAL':
      case 'DATE':
        return allOps;
      default:
        return allOps;
    }
  };

  const getDefaultValueType = (componentType: string) => {
    const discountTypes = ['WAIVER', 'BENEFIT', 'DISCOUNT'];
    return discountTypes.includes(componentType) ? 'DISCOUNT_PERCENTAGE' : 'FEE_ABSOLUTE';
  };

  const mapTierFromApi = (tier: any, componentType: string) => {
    const firstValue = tier?.priceValue || (Array.isArray(tier?.priceValues) ? tier.priceValues[0] : null);
    return {
      id: tier.id,
      code: tier.code || '',
      name: tier.name || '',
      priority: tier.priority,
      minThreshold: tier.minThreshold,
      maxThreshold: tier.maxThreshold,
      applyChargeOnFullBreach: tier.applyChargeOnFullBreach || false,
      isCodeEdited: true,
      conditions: (tier.conditions || []).map((c: any) => ({
        attributeName: c.attributeName || '',
        operator: c.operator || 'EQ',
        attributeValue: c.attributeValue || '',
        connector: c.connector || 'AND'
      })),
      priceValue: {
        priceAmount: firstValue?.rawValue ?? firstValue?.priceAmount ?? 0,
        valueType: firstValue?.valueType || getDefaultValueType(componentType)
      }
    };
  };

  const buildSubmitPayload = (data: any) => ({
    code: data.code,
    name: data.name,
    type: data.type,
    description: data.description,
    pricingTiers: (data.pricingTiers || []).map((tier: any) => ({
      code: tier.code,
      name: tier.name,
      priority: tier.priority,
      minThreshold: tier.minThreshold,
      maxThreshold: tier.maxThreshold,
      applyChargeOnFullBreach: !!tier.applyChargeOnFullBreach,
      conditions: (tier.conditions || []).map((cond: any, index: number, arr: any[]) => ({
        attributeName: cond.attributeName,
        operator: cond.operator,
        attributeValue: cond.attributeValue,
        connector: index < arr.length - 1 ? (cond.connector || 'AND') : null
      })),
      priceValue: {
        priceAmount: Number(tier.priceValue?.priceAmount || 0),
        valueType: tier.priceValue?.valueType || getDefaultValueType(data.type)
      }
    }))
  });

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [metaRes, compRes] = await Promise.all([
          axios.get('/api/v1/pricing-metadata'),
          isEditing ? axios.get(`/api/v1/pricing-components/${id}`) : Promise.resolve({ data: null })
        ]);

        setMetadata(metaRes.data || []);

        if (isEditing) {
          const comp = compRes.data;
          if (comp) {
            setFormData({
              code: comp.code,
              name: comp.name,
              type: comp.type,
              description: comp.description,
              pricingTiers: (comp.pricingTiers || []).map((t: any) => mapTierFromApi(t, comp.type))
            });
            setEntityName(comp.name);
            setIsCodeEdited(true);
          } else {
            setToast({ message: 'Pricing component not found.', type: 'error' });
          }
        }
      } catch (err: any) {
        setToast({ message: 'Failed to fetch required data.', type: 'error' });
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id, isEditing, setEntityName]);

  const handleTierChange = (index: number, field: string, value: any) => {
    const newTiers = [...formData.pricingTiers];
    let updatedTier = { ...newTiers[index], [field]: value };

    if (field === 'name') {
      if (!updatedTier.isCodeEdited && !updatedTier.id) {
        updatedTier.code = value.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
      }
    } else if (field === 'code') {
      updatedTier.isCodeEdited = true;
      updatedTier.code = value.toUpperCase().replace(/\s/g, '_').replace(/[^A-Z0-9_-]/g, '');
    }

    newTiers[index] = updatedTier;
    setFormData({ ...formData, pricingTiers: newTiers });
  };

  const addTier = () => {
    setFormData({
      ...formData,
      pricingTiers: [...formData.pricingTiers, {
        code: '',
        name: '',
        isCodeEdited: false,
        conditions: [],
        priceValue: { priceAmount: 0, valueType: getDefaultValueType(formData.type) }
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

    if (field === 'attributeName') {
      const oldAttr = newConds[condIdx].attributeName;
      const oldMeta = metadata.find(m => m.attributeKey === oldAttr);
      const newMeta = metadata.find(m => m.attributeKey === value);

      if (oldMeta?.dataType !== newMeta?.dataType) {
        newConds[condIdx] = {
          ...newConds[condIdx],
          attributeName: value,
          attributeValue: '',
          operator: 'EQ'
        };
      } else {
        newConds[condIdx] = { ...newConds[condIdx], attributeName: value };
      }
    } else {
      newConds[condIdx] = { ...newConds[condIdx], [field]: value };
    }

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
    setSubmitting(true);
    try {
      const payload = buildSubmitPayload(formData);
      if (isEditing) {
        await axios.patch(`/api/v1/pricing-components/${id}`, payload);
      } else {
        await axios.post('/api/v1/pricing-components', payload);
      }
      setToast({
        message: isEditing ? 'Pricing component updated successfully.' : 'Pricing component created successfully.',
        type: 'success'
      });
      navigate('/pricing-components', { state: { success: isEditing ? 'Pricing component updated successfully.' : 'Pricing component created successfully.' } });
    } catch (err: any) {
      const message = err.response?.data?.message || 'An error occurred while saving the component.';
      setToast({ message, type: 'error' });
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
    <div className="max-w-5xl mx-auto space-y-4 pb-10">
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="relative">
          <h1 className="text-xl font-bold text-gray-900 tracking-tight uppercase">
            {isEditing ? 'Update Pricing' : 'New Pricing'}
          </h1>
          <p className="text-gray-500 font-bold mt-0.5 uppercase tracking-widest text-[10px]">
            Configure the core identity and tiered logic in one atomic operation.
          </p>
        </div>
        <button
          onClick={() => navigate('/pricing-components')}
          className="bg-gray-50 text-gray-400 p-2 rounded-xl hover:bg-gray-100 transition relative border border-gray-100 shadow-sm"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100">
        <form onSubmit={handleSubmit} className="p-8 space-y-8">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Product-Facing Name</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-bold text-sm transition focus:border-blue-500 shadow-sm"
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
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Immutable Component Code</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-mono font-bold text-blue-700 text-sm transition focus:border-blue-500 shadow-sm"
                value={formData.code}
                onChange={(e) => {
                  setIsCodeEdited(true);
                  setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                }}
                placeholder="e.g. MAINTENANCE_FEE"
              />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Financial Type</label>
              <div className="flex space-x-3">
                {[
                  { value: 'FEE', label: 'FEE' },
                  { value: 'INTEREST_RATE', label: 'RATE' },
                  { value: 'DISCOUNT', label: 'DISCOUNT' }
                ].map(t => (
                  <button key={t.value} type="button" onClick={() => setFormData({...formData, type: t.value})} className={`flex-1 py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest transition border ${formData.type === t.value ? 'bg-blue-600 border-blue-600 text-white shadow-md shadow-blue-100' : 'border-gray-200 text-gray-400 hover:border-gray-300'}`}>{t.label}</button>
                ))}
              </div>
            </div>
            <div className="md:col-span-2">
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Business Description</label>
              <textarea className="w-full border border-gray-200 rounded-xl p-3 h-20 font-medium text-sm transition focus:border-blue-500 shadow-sm" value={formData.description} onChange={(e) => setFormData({...formData, description: e.target.value})} placeholder="Describe the purpose and lifecycle of this pricing component..." />
            </div>
          </div>

          <div className="border-t border-gray-100 pt-8">
            <div className="flex justify-between items-center mb-6">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-purple-50 rounded-xl"><Layers className="w-5 h-5 text-purple-600" /></div>
                <h3 className="text-lg font-bold text-gray-900 tracking-tight">Segment Pricing Tiers</h3>
              </div>
              <button type="button" onClick={addTier} className="bg-purple-600 text-white px-4 py-2 rounded-lg flex items-center font-bold text-[10px] uppercase tracking-widest hover:bg-purple-700 transition shadow-md shadow-purple-50">
                <Plus className="w-4 h-4 mr-1.5" /> Add Logic Tier
              </button>
            </div>

            <div className="space-y-6">
              {formData.pricingTiers.map((tier: any, idx: number) => (
                <div key={idx} className="rounded-xl p-6 bg-gray-50/50 border border-gray-100 relative group transition hover:border-blue-200">
                  <button type="button" onClick={() => removeTier(idx)} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Display Label</label>
                      <input type="text" required className="w-full border border-white rounded-lg p-2.5 font-bold text-[11px] bg-white shadow-sm focus:border-blue-500 transition" value={tier.name} onChange={(e) => handleTierChange(idx, 'name', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Tier ID Code</label>
                      <input type="text" required className="w-full border border-white rounded-lg p-2.5 font-mono font-bold text-[11px] bg-white shadow-sm focus:border-blue-500 transition" value={tier.code} onChange={(e) => handleTierChange(idx, 'code', e.target.value)} />
                    </div>
                  </div>

                  <div className="mb-6">
                    <div className="flex justify-between items-center mb-3">
                      <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Pricing Rules (Conditions)</label>
                      <button type="button" onClick={() => addCondition(idx)} className="text-blue-600 hover:text-blue-700 text-[10px] font-bold uppercase tracking-widest bg-white px-2 py-1 rounded-lg shadow-sm border border-blue-50">+ New Condition</button>
                    </div>
                    <div className="space-y-2">
                      {tier.conditions?.map((cond: any, cidx: number) => (
                        <div key={cidx} className="flex items-center space-x-2 animate-in slide-in-from-left-2 duration-200">
                          <div className="flex-1 grid grid-cols-12 gap-1.5">
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
                                options={getOperatorsForDataType(metadata.find(m => m.attributeKey === cond.attributeName)?.dataType || 'STRING')}
                                value={{ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' }[cond.operator as string] ? { value: cond.operator, label: ({ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' } as any)[cond.operator] } : null}
                                onChange={(opt) => handleConditionChange(idx, cidx, 'operator', opt ? opt.value : 'EQ')}
                                menuPlacement="auto"
                              />
                            </div>
                            <div className="col-span-3">
                              {(() => {
                                const dataType = metadata.find(m => m.attributeKey === cond.attributeName)?.dataType || 'STRING';
                                switch (dataType) {
                                  case 'BOOLEAN':
                                    return (
                                      <div className="h-[42px] flex items-center px-3 bg-white border border-gray-200 rounded-lg">
                                        <button
                                          type="button"
                                          onClick={() => handleConditionChange(idx, cidx, 'attributeValue', cond.attributeValue === 'true' ? 'false' : 'true')}
                                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${cond.attributeValue === 'true' ? 'bg-blue-600' : 'bg-gray-200'}`}
                                        >
                                          <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${cond.attributeValue === 'true' ? 'translate-x-5' : 'translate-x-1'}`} />
                                        </button>
                                        <span className="ml-2 text-[9px] font-bold uppercase tracking-widest text-gray-500">
                                          {cond.attributeValue === 'true' ? 'TRUE' : 'FALSE'}
                                        </span>
                                      </div>
                                    );
                                  case 'DATE':
                                    return (
                                      <input
                                        type="date"
                                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                        value={cond.attributeValue}
                                        onChange={(e) => handleConditionChange(idx, cidx, 'attributeValue', e.target.value)}
                                      />
                                    );
                                  case 'INTEGER':
                                  case 'DECIMAL':
                                    return (
                                      <input
                                        type="number"
                                        step={dataType === 'INTEGER' ? '1' : 'any'}
                                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                        placeholder="Value..."
                                        value={cond.attributeValue}
                                        onChange={(e) => handleConditionChange(idx, cidx, 'attributeValue', e.target.value)}
                                      />
                                    );
                                  default:
                                    return (
                                      <input
                                        type="text"
                                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                        placeholder="Value..."
                                        value={cond.attributeValue}
                                        onChange={(e) => handleConditionChange(idx, cidx, 'attributeValue', e.target.value)}
                                      />
                                    );
                                }
                              })()}
                            </div>
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
                              ) : <div className="w-full p-2.5"></div>}
                            </div>
                          </div>
                          <button type="button" onClick={() => removeCondition(idx, cidx)} className="p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-lg transition"><X className="w-4 h-4" /></button>
                        </div>
                      ))}
                      {(!tier.conditions || tier.conditions.length === 0) && (
                        <div className="bg-white/50 border border-dashed border-gray-200 rounded-lg p-3 text-center text-[11px] text-gray-400 font-medium">Default Tier: This price applies if no other tiered segments match.</div>
                      )}
                    </div>
                  </div>

                  <div className="bg-white p-4 rounded-xl border border-blue-50 grid grid-cols-2 gap-6 shadow-sm">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Calculated Amount ({currencyCode})</label>
                      <div className="relative">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-blue-600 text-sm">{getCurrencySymbol(currencyCode)}</span>
                        <input type="number" step="0.01" required className="w-full border border-gray-200 rounded-lg p-2.5 pl-7 font-bold text-base text-blue-900 transition focus:border-blue-500 focus:ring-0 shadow-sm" value={tier.priceValue.priceAmount} onChange={(e) => {
                          const newTiers = [...formData.pricingTiers];
                          newTiers[idx].priceValue = { ...newTiers[idx].priceValue, priceAmount: parseFloat(e.target.value) };
                          setFormData({...formData, pricingTiers: newTiers});
                        }} />
                      </div>
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Financial Value Type</label>
                      <PlexusSelect
                        options={['WAIVER', 'BENEFIT', 'DISCOUNT'].includes(formData.type)
                          ? [
                            { value: 'DISCOUNT_ABSOLUTE', label: 'CASH DISCOUNT' },
                            { value: 'DISCOUNT_PERCENTAGE', label: 'PERCENTAGE DISCOUNT' },
                            { value: 'FREE_COUNT', label: 'FREE COUNT' }
                          ]
                          : [
                            { value: 'FEE_ABSOLUTE', label: 'ABSOLUTE VALUE' },
                            { value: 'FEE_PERCENTAGE', label: 'PERCENTAGE RATE' }
                          ]}
                        value={{
                          FEE_ABSOLUTE: 'ABSOLUTE VALUE',
                          FEE_PERCENTAGE: 'PERCENTAGE RATE',
                          DISCOUNT_ABSOLUTE: 'CASH DISCOUNT',
                          DISCOUNT_PERCENTAGE: 'PERCENTAGE DISCOUNT',
                          FREE_COUNT: 'FREE COUNT'
                        }[tier.priceValue.valueType as string] ? {
                          value: tier.priceValue.valueType,
                          label: ({ FEE_ABSOLUTE: 'ABSOLUTE VALUE', FEE_PERCENTAGE: 'PERCENTAGE RATE', DISCOUNT_ABSOLUTE: 'CASH DISCOUNT', DISCOUNT_PERCENTAGE: 'PERCENTAGE DISCOUNT', FREE_COUNT: 'FREE COUNT' } as any)[tier.priceValue.valueType]
                        } : null}
                        onChange={(opt) => {
                          const newTiers = [...formData.pricingTiers];
                          newTiers[idx].priceValue = { ...newTiers[idx].priceValue, valueType: opt ? opt.value : getDefaultValueType(formData.type) };
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

          <div className="pt-8 border-t border-gray-100 flex space-x-4">
            <button type="button" onClick={() => navigate('/pricing-components')} className="flex-1 px-6 py-3 border border-gray-200 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]">Discard Changes</button>
            <button type="submit" disabled={submitting} className="flex-1 px-6 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50">
              {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
              Commit Structure
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PricingComponentFormPage;
