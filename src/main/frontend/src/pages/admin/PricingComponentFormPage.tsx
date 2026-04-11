import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Trash2, Loader2, Save, Tag, Layers, X, HelpCircle } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import PlexusSelect from '../../components/PlexusSelect';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard';

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
  const [violations, setViolations] = useState<any[]>([]);
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
      case 'LONG':
      case 'DECIMAL':
      case 'DATE':
        return allOps;
      default:
        return allOps;
    }
  };

  const getDefaultValueType = useCallback((componentType: string) => {
    const discountTypes = ['WAIVER', 'BENEFIT', 'DISCOUNT'];
    return discountTypes.includes(componentType) ? 'DISCOUNT_PERCENTAGE' : 'FEE_ABSOLUTE';
  }, []);

  // Backend default for omitted priority. We hide it in UI and treat it as fallback.
  const LOWEST_PRIORITY_SENTINEL = -2147483648;

  const getUsedPriorities = (tiers: any[], excludeIndex?: number) => {
    const numeric = (tiers || [])
      .filter((_: any, idx: number) => idx !== excludeIndex)
      .map((t: any) => t.priority)
      .filter((p: any) => p !== '' && p !== null && p !== undefined)
      .map((p: any) => Number(p))
      .filter((p: number) => Number.isInteger(p) && p >= 0);

    return Array.from(new Set(numeric)).sort((a, b) => b - a);
  };

  const getSuggestedPriority = (tiers: any[]) => {
    const used = getUsedPriorities(tiers);
    if (used.length === 0) {
      return 100;
    }
    return Math.max(...used) + 10;
  };

  const normalizePriorityForForm = useCallback((priority: any) => {
    if (priority === null || priority === undefined || priority === '') {
      return '';
    }
    const numeric = Number(priority);
    if (!Number.isInteger(numeric) || numeric === LOWEST_PRIORITY_SENTINEL) {
      return '';
    }
    return numeric;
  }, [LOWEST_PRIORITY_SENTINEL]);

  const sortTiersByPriority = (tiers: any[]) => {
    const toSortValue = (priority: any) => {
      if (priority === '' || priority === null || priority === undefined) {
        return Number.NEGATIVE_INFINITY;
      }
      const numeric = Number(priority);
      return Number.isFinite(numeric) ? numeric : Number.NEGATIVE_INFINITY;
    };

    return [...tiers].sort((a, b) => toSortValue(b.priority) - toSortValue(a.priority));
  };

  const getDynamicPriorityChipOptions = (tiers: any[], excludeIndex?: number) => {
    const existing = getUsedPriorities(tiers, excludeIndex);
    const suggested = getSuggestedPriority(tiers);
    const values = Array.from(new Set([suggested, ...existing])).slice(0, 6);

    return values.map((value, idx) => ({
      label: idx === 0 ? `Suggested (${value})` : `P${value}`,
      value
    }));
  };

  const mapTierFromApi = useCallback((tier: any, componentType: string) => {
    const firstValue = tier?.priceValue || (Array.isArray(tier?.priceValues) ? tier.priceValues[0] : null);
    return {
      id: tier.id,
      code: tier.code || '',
      name: tier.name || '',
      priority: normalizePriorityForForm(tier.priority),
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
  }, [getDefaultValueType, normalizePriorityForForm]);

  const buildSubmitPayload = (data: any) => ({
    code: data.code,
    name: data.name,
    type: data.type,
    description: data.description,
    pricingTiers: (data.pricingTiers || []).map((tier: any) => ({
      code: tier.code,
      name: tier.name,
      priority: tier.priority === '' || tier.priority === null || tier.priority === undefined ? null : Number(tier.priority),
      minThreshold: tier.minThreshold === '' || tier.minThreshold === null || tier.minThreshold === undefined ? null : Number(tier.minThreshold),
      maxThreshold: tier.maxThreshold === '' || tier.maxThreshold === null || tier.maxThreshold === undefined ? null : Number(tier.maxThreshold),
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

  const signal = useAbortSignal();
  const { resetDirtyBaseline, confirmDiscardChanges } = useUnsavedChangesGuard(formData);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [metaRes, compRes] = await Promise.all([
          axios.get('/api/v1/pricing-metadata', { signal }),
          isEditing ? axios.get(`/api/v1/pricing-components/${id}`, { signal }) : Promise.resolve({ data: null })
        ]);

        setMetadata(metaRes.data || []);

        if (isEditing) {
          const comp = compRes.data;
          if (comp) {
            if (comp.status !== 'DRAFT') {
              setToast({ message: `Cannot edit ${comp.status} pricing component. Please create a new version.`, type: 'error' });
              navigate('/pricing-components');
              return;
            }
            const loadedFormData = {
              code: comp.code,
              name: comp.name,
              type: comp.type,
              description: comp.description,
              pricingTiers: sortTiersByPriority((comp.pricingTiers || []).map((t: any) => mapTierFromApi(t, comp.type)))
            };
            setFormData(loadedFormData);
            resetDirtyBaseline(loadedFormData);
            setEntityName(comp.name);
            setIsCodeEdited(true);
          } else {
            setToast({ message: 'Pricing component not found.', type: 'error' });
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
  }, [id, isEditing, navigate, setEntityName, setToast, mapTierFromApi, signal, resetDirtyBaseline]);

  const handleCancel = () => {
    if (!confirmDiscardChanges()) {
      return;
    }
    navigate('/pricing-components');
  };

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
    setFormData({
      ...formData,
      pricingTiers: field === 'priority' ? sortTiersByPriority(newTiers) : newTiers
    });
  };

  const addTier = () => {
    const newTier = {
      code: '',
      name: '',
      priority: getSuggestedPriority(formData.pricingTiers),
      minThreshold: '',
      maxThreshold: '',
      applyChargeOnFullBreach: false,
      isCodeEdited: false,
      conditions: [],
      priceValue: { priceAmount: 0, valueType: getDefaultValueType(formData.type) }
    };

    setFormData({
      ...formData,
      pricingTiers: sortTiersByPriority([...formData.pricingTiers, newTier])
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

  const clearViolation = (field: string) => {
    setViolations(prev => prev.filter(v => v.field !== field));
  };

  const validateTierControls = (data: any) => {
    const nextViolations: Array<{ field: string; reason: string; severity: 'ERROR' }> = [];

    (data.pricingTiers || []).forEach((tier: any, idx: number) => {
      const priorityRaw = tier.priority;
      if (priorityRaw !== '' && priorityRaw !== null && priorityRaw !== undefined) {
        const parsedPriority = Number(priorityRaw);
        if (!Number.isInteger(parsedPriority)) {
          nextViolations.push({
            field: `pricingTiers[${idx}].priority`,
            reason: 'Priority must be a whole number.',
            severity: 'ERROR'
          });
        }
      }

      const minRaw = tier.minThreshold;
      const maxRaw = tier.maxThreshold;
      const hasMin = minRaw !== '' && minRaw !== null && minRaw !== undefined;
      const hasMax = maxRaw !== '' && maxRaw !== null && maxRaw !== undefined;
      const parsedMin = hasMin ? Number(minRaw) : null;
      const parsedMax = hasMax ? Number(maxRaw) : null;

      if (hasMin && (Number.isNaN(parsedMin) || parsedMin! < 0)) {
        nextViolations.push({
          field: `pricingTiers[${idx}].minThreshold`,
          reason: 'Minimum threshold must be a number greater than or equal to 0.',
          severity: 'ERROR'
        });
      }

      if (hasMax && (Number.isNaN(parsedMax) || parsedMax! < 0)) {
        nextViolations.push({
          field: `pricingTiers[${idx}].maxThreshold`,
          reason: 'Maximum threshold must be a number greater than or equal to 0.',
          severity: 'ERROR'
        });
      }

      if (parsedMin !== null && parsedMax !== null && !Number.isNaN(parsedMin) && !Number.isNaN(parsedMax) && parsedMin > parsedMax) {
        nextViolations.push({
          field: `pricingTiers[${idx}].maxThreshold`,
          reason: 'Maximum threshold must be greater than or equal to minimum threshold.',
          severity: 'ERROR'
        });
      }
    });

    return nextViolations;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setViolations([]);

    const tierValidationViolations = validateTierControls(formData);
    if (tierValidationViolations.length > 0) {
      setViolations(tierValidationViolations);
      setToast({ message: 'Please fix tier evaluation fields before saving.', type: 'error' });
      setSubmitting(false);
      return;
    }

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
      if (err.response?.status === 422 && err.response?.data?.errors) {
        setViolations(err.response.data.errors);
      }
      const message = err.response?.data?.message || 'An error occurred while saving the component.';
      setToast({ message, type: 'error' });
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

  const renderViolations = (field: string) => {
    return violations
      .filter(v => v.field === field)
      .map((v, i) => (
        <div key={i} className={v.severity === 'WARNING' ? 'admin-warning-text' : 'admin-error-text'}>
          {v.reason}
        </div>
      ));
  };

  const renderTierFieldLabel = (title: string, tooltip: string) => (
    <label className="flex items-center gap-1 text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">
      <span>{title}</span>
      <span title={tooltip} className="text-gray-400 cursor-help inline-flex items-center">
        <HelpCircle className="w-3.5 h-3.5" />
      </span>
    </label>
  );

  return (
    <AdminPage width="medium">
      <AdminFormHeader
        icon={Tag}
        tone="purple"
        title={isEditing ? 'Update Pricing' : 'New Pricing'}
        description="Configure the core identity and tiered logic in one atomic operation."
        onClose={handleCancel}
      />

      <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100">
        <form onSubmit={handleSubmit} className="space-y-6 p-5">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
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
                  clearViolation('name');
                }}
                placeholder="e.g. Monthly Maintenance Fee"
              />
              {renderViolations('name')}
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
                  clearViolation('code');
                }}
                placeholder="e.g. MAINTENANCE_FEE"
              />
              {renderViolations('code')}
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Financial Type</label>
              <div className="flex space-x-3">
                {[
                  { value: 'FEE', label: 'FEE' },
                  { value: 'INTEREST_RATE', label: 'RATE' },
                  { value: 'DISCOUNT', label: 'DISCOUNT' }
                ].map(t => (
                  <button key={t.value} type="button" onClick={() => {
                    setFormData({...formData, type: t.value});
                    clearViolation('type');
                  }} className={`flex-1 py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest transition border ${formData.type === t.value ? 'bg-blue-600 border-blue-600 text-white shadow-md shadow-blue-100' : 'border-gray-200 text-gray-400 hover:border-gray-300'}`}>{t.label}</button>
                ))}
              </div>
              {renderViolations('type')}
            </div>
            <div className="md:col-span-2">
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Business Description</label>
              <textarea className="w-full border border-gray-200 rounded-xl p-3 h-20 font-medium text-sm transition focus:border-blue-500 shadow-sm" value={formData.description} onChange={(e) => {
                setFormData({...formData, description: e.target.value});
                clearViolation('description');
              }} placeholder="Describe the purpose and lifecycle of this pricing component..." />
              {renderViolations('description')}
            </div>
          </div>

          <div className="border-t border-gray-100 pt-6">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-purple-50 rounded-xl"><Layers className="w-5 h-5 text-purple-600" /></div>
                <h3 className="text-lg font-bold text-gray-900 tracking-tight">Segment Pricing Tiers</h3>
              </div>
              <button type="button" onClick={addTier} className="bg-purple-600 text-white px-4 py-2 rounded-lg flex items-center font-bold text-[10px] uppercase tracking-widest hover:bg-purple-700 transition shadow-md shadow-purple-50">
                <Plus className="w-4 h-4 mr-1.5" /> Add Logic Tier
              </button>
            </div>

            <div className="space-y-4">
              {formData.pricingTiers.map((tier: any, idx: number) => {
                const existingPriorities = getUsedPriorities(formData.pricingTiers, idx);
                const dynamicPriorityChipOptions = getDynamicPriorityChipOptions(formData.pricingTiers, idx);

                return (
                <div key={idx} className="rounded-xl p-6 bg-gray-50/50 border border-gray-100 relative group transition hover:border-blue-200">
                  <button type="button" onClick={() => removeTier(idx)} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Display Label</label>
                      <input type="text" required className="w-full border border-white rounded-lg p-2.5 font-bold text-[11px] bg-white shadow-sm focus:border-blue-500 transition" value={tier.name} onChange={(e) => {
                        handleTierChange(idx, 'name', e.target.value);
                        clearViolation(`pricingTiers[${idx}].name`);
                      }} />
                      {renderViolations(`pricingTiers[${idx}].name`)}
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Tier ID Code</label>
                      <input type="text" required className="w-full border border-white rounded-lg p-2.5 font-mono font-bold text-[11px] bg-white shadow-sm focus:border-blue-500 transition" value={tier.code} onChange={(e) => {
                        handleTierChange(idx, 'code', e.target.value);
                        clearViolation(`pricingTiers[${idx}].code`);
                      }} />
                      {renderViolations(`pricingTiers[${idx}].code`)}
                    </div>
                  </div>

                  <div className="mb-6 bg-white p-4 rounded-xl border border-gray-100">
                    <p className="mb-3 text-[10px] text-blue-900 leading-relaxed">
                      <span className="font-bold uppercase tracking-wider">How these controls affect pricing:</span>{' '}
                      <span className="font-medium">Priority decides which matching tier is applied first. Min/Max threshold restricts when this tier can match by amount. Full-breach controls whether charge applies to the full eligible amount once breached.</span>
                    </p>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                      {renderTierFieldLabel('Priority', 'Controls evaluation order. Higher priority tiers are checked first. If multiple tiers match, higher priority wins.')}
                      <input
                        type="number"
                        step="1"
                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                        placeholder="Optional"
                        value={tier.priority ?? ''}
                        onChange={(e) => {
                          handleTierChange(idx, 'priority', e.target.value === '' ? '' : Number(e.target.value));
                          clearViolation(`pricingTiers[${idx}].priority`);
                        }}
                      />
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {dynamicPriorityChipOptions.map((preset) => {
                          const isSelected = Number(tier.priority) === preset.value;
                          return (
                            <button
                              key={preset.label}
                              type="button"
                              onClick={() => {
                                handleTierChange(idx, 'priority', preset.value);
                                clearViolation(`pricingTiers[${idx}].priority`);
                              }}
                              className={`px-2 py-1 rounded-md text-[9px] font-bold uppercase tracking-wider border transition ${isSelected ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-gray-200 text-gray-500 hover:border-blue-300 hover:text-blue-600'}`}
                            >
                              {preset.label}
                            </button>
                          );
                        })}
                        <button
                          type="button"
                          onClick={() => {
                            handleTierChange(idx, 'priority', '');
                            clearViolation(`pricingTiers[${idx}].priority`);
                          }}
                          className={`px-2 py-1 rounded-md text-[9px] font-bold uppercase tracking-wider border transition ${tier.priority === '' || tier.priority === null || tier.priority === undefined ? 'bg-gray-700 border-gray-700 text-white' : 'bg-white border-gray-200 text-gray-500 hover:border-gray-400 hover:text-gray-700'}`}
                        >
                          Fallback
                        </button>
                      </div>
                      <div className="mt-1 text-[9px] text-gray-400 font-semibold">
                        Existing priorities in this component: {existingPriorities.length > 0 ? existingPriorities.join(', ') : 'none'}
                      </div>
                      {renderViolations(`pricingTiers[${idx}].priority`)}
                    </div>

                    <div>
                      {renderTierFieldLabel('Minimum Threshold', 'Optional lower bound for amount matching. Tier matches only when amount is greater than or equal to this value.')}
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                        placeholder="Optional"
                        value={tier.minThreshold ?? ''}
                        onChange={(e) => {
                          handleTierChange(idx, 'minThreshold', e.target.value === '' ? '' : Number(e.target.value));
                          clearViolation(`pricingTiers[${idx}].minThreshold`);
                        }}
                      />
                      {renderViolations(`pricingTiers[${idx}].minThreshold`)}
                    </div>

                    <div>
                      {renderTierFieldLabel('Maximum Threshold', 'Optional upper bound for amount matching. Tier matches only when amount is less than or equal to this value.')}
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                        placeholder="Optional"
                        value={tier.maxThreshold ?? ''}
                        onChange={(e) => {
                          handleTierChange(idx, 'maxThreshold', e.target.value === '' ? '' : Number(e.target.value));
                          clearViolation(`pricingTiers[${idx}].maxThreshold`);
                        }}
                      />
                      {renderViolations(`pricingTiers[${idx}].maxThreshold`)}
                    </div>

                    <div>
                      {renderTierFieldLabel('Apply Charge On Full Breach', 'When enabled, charge applies to the full eligible amount once this tier is breached, not just a partial portion.')}
                      <div className="h-[42px] flex items-center px-3 bg-white border border-gray-200 rounded-lg">
                        <button
                          type="button"
                          onClick={() => {
                            handleTierChange(idx, 'applyChargeOnFullBreach', !tier.applyChargeOnFullBreach);
                            clearViolation(`pricingTiers[${idx}].applyChargeOnFullBreach`);
                          }}
                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${tier.applyChargeOnFullBreach ? 'bg-blue-600' : 'bg-gray-200'}`}
                        >
                          <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${tier.applyChargeOnFullBreach ? 'translate-x-5' : 'translate-x-1'}`} />
                        </button>
                        <span className="ml-2 text-[9px] font-bold uppercase tracking-widest text-gray-500">
                          {tier.applyChargeOnFullBreach ? 'ENABLED' : 'DISABLED'}
                        </span>
                      </div>
                      {renderViolations(`pricingTiers[${idx}].applyChargeOnFullBreach`)}
                    </div>
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
                                onChange={(opt) => {
                                  handleConditionChange(idx, cidx, 'attributeName', opt ? opt.value : '');
                                  clearViolation(`pricingTiers[${idx}].conditions[${cidx}].attributeName`);
                                }}
                                menuPlacement="auto"
                              />
                              {renderViolations(`pricingTiers[${idx}].conditions[${cidx}].attributeName`)}
                            </div>
                            <div className="col-span-2">
                              <PlexusSelect
                                options={getOperatorsForDataType(metadata.find(m => m.attributeKey === cond.attributeName)?.dataType || 'STRING')}
                                value={{ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' }[cond.operator as string] ? { value: cond.operator, label: ({ EQ: '=', GT: '>', LT: '<', GE: '>=', LE: '<=' } as any)[cond.operator] } : null}
                                onChange={(opt) => {
                                  handleConditionChange(idx, cidx, 'operator', opt ? opt.value : 'EQ');
                                  clearViolation(`pricingTiers[${idx}].conditions[${cidx}].operator`);
                                }}
                                menuPlacement="auto"
                              />
                              {renderViolations(`pricingTiers[${idx}].conditions[${cidx}].operator`)}
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
                                          onClick={() => {
                                            handleConditionChange(idx, cidx, 'attributeValue', cond.attributeValue === 'true' ? 'false' : 'true');
                                            clearViolation(`pricingTiers[${idx}].conditions[${cidx}].attributeValue`);
                                          }}
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
                                        onChange={(e) => {
                                          handleConditionChange(idx, cidx, 'attributeValue', e.target.value);
                                          clearViolation(`pricingTiers[${idx}].conditions[${cidx}].attributeValue`);
                                        }}
                                      />
                                    );
                                  case 'INTEGER':
                                  case 'LONG':
                                  case 'DECIMAL':
                                    return (
                                      <input
                                        type="number"
                                        step={dataType === 'INTEGER' || dataType === 'LONG' ? '1' : 'any'}
                                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                        placeholder="Value..."
                                        value={cond.attributeValue}
                                        onChange={(e) => {
                                          handleConditionChange(idx, cidx, 'attributeValue', e.target.value);
                                          clearViolation(`pricingTiers[${idx}].conditions[${cidx}].attributeValue`);
                                        }}
                                      />
                                    );
                                  default:
                                    return (
                                      <input
                                        type="text"
                                        className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                        placeholder="Value..."
                                        value={cond.attributeValue}
                                        onChange={(e) => {
                                          handleConditionChange(idx, cidx, 'attributeValue', e.target.value);
                                          clearViolation(`pricingTiers[${idx}].conditions[${cidx}].attributeValue`);
                                        }}
                                      />
                                    );
                                }
                              })()}
                              {renderViolations(`pricingTiers[${idx}].conditions[${cidx}].attributeValue`)}
                            </div>
                            <div className="col-span-2">
                              {cidx < tier.conditions.length - 1 ? (
                                <PlexusSelect
                                  options={[
                                    { value: 'AND', label: 'AND' },
                                    { value: 'OR', label: 'OR' }
                                  ]}
                                  value={{ AND: 'AND', OR: 'OR' }[cond.connector as string] ? { value: cond.connector, label: cond.connector } : null}
                                  onChange={(opt) => {
                                    handleConditionChange(idx, cidx, 'connector', opt ? opt.value : 'AND');
                                    clearViolation(`pricingTiers[${idx}].conditions[${cidx}].connector`);
                                  }}
                                  menuPlacement="auto"
                                />
                              ) : <div className="w-full p-2.5"></div>}
                              {cidx < tier.conditions.length - 1 && renderViolations(`pricingTiers[${idx}].conditions[${cidx}].connector`)}
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
                          clearViolation(`pricingTiers[${idx}].priceValue.priceAmount`);
                          clearViolation(`pricingTiers[${idx}].priceValue.price_amount`);
                        }} />
                      </div>
                      {renderViolations(`pricingTiers[${idx}].priceValue.priceAmount`)}
                      {renderViolations(`pricingTiers[${idx}].priceValue.price_amount`)}
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
                          clearViolation(`pricingTiers[${idx}].priceValue.valueType`);
                          clearViolation(`pricingTiers[${idx}].priceValue.value_type`);
                        }}
                      />
                      {renderViolations(`pricingTiers[${idx}].priceValue.valueType`)}
                      {renderViolations(`pricingTiers[${idx}].priceValue.value_type`)}
                    </div>
                  </div>
                </div>
              )})}
              {formData.pricingTiers.length === 0 && (
                <div className="text-center py-16 border-4 border-dashed rounded-[2rem] text-gray-300 font-black uppercase tracking-widest text-xs">
                  No Segment Logic Defined
                </div>
              )}
            </div>
          </div>

          <div className="flex space-x-4 border-t border-gray-100 pt-6">
            <button type="button" onClick={handleCancel} className="flex-1 px-6 py-3 border border-gray-200 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]">Discard Changes</button>
            <button type="submit" disabled={submitting} className="flex-1 px-6 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50">
              {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
              Commit Structure
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default PricingComponentFormPage;
