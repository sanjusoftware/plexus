import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Tag, ChevronDown, ChevronUp, Info } from 'lucide-react';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionContent,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow,
  AuditTimestampCell
} from '../../components/AdminDataTable';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

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
  rawValue?: number;
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
  priceValues?: PriceValue[];
}

interface PricingComponent {
  id: number;
  code: string;
  name: string;
  version?: number;
  type: string;
  description: string;
  status: string;
  pricingTiers: PricingTier[];
  createdAt?: string;
  updatedAt?: string;
}

const PricingComponentsPage = () => {
  const navigate = useNavigate();
  const { user, setToast } = useAuth();
  const [components, setComponents] = useState<PricingComponent[]>([]);
  const [metadata, setMetadata] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  const normalizeTier = useCallback((tier: any): PricingTier => {
    const firstValue = tier?.priceValue || (Array.isArray(tier?.priceValues) && tier.priceValues.length > 0 ? tier.priceValues[0] : null);
    return {
      ...tier,
      conditions: tier.conditions || [],
      priceValues: tier.priceValues || (firstValue ? [firstValue] : []),
      priceValue: {
        priceAmount: firstValue?.priceAmount ?? firstValue?.rawValue ?? 0,
        valueType: firstValue?.valueType || '—',
        currency: firstValue?.currency,
        rawValue: firstValue?.rawValue
      }
    };
  }, []);

  const normalizeComponent = useCallback((component: any): PricingComponent => ({
    ...component,
    pricingTiers: (component.pricingTiers || []).map(normalizeTier)
  }), [normalizeTier]);

  const signal = useAbortSignal();

  const fetchData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const [compRes, metaRes] = await Promise.all([
        axios.get('/api/v1/pricing-components', { signal: abortSignal }),
        axios.get('/api/v1/pricing-metadata', { signal: abortSignal })
      ]);
      setComponents((compRes.data || []).map(normalizeComponent));
      setMetadata(metaRes.data || []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch pricing components.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [normalizeComponent, setToast]);

  useEffect(() => {
    fetchData(signal);
  }, [fetchData, signal]);

  const formatCondition = (condition: TierCondition, currency: string) => {
    const meta = metadata.find(m => m.attributeKey === condition.attributeName);
    const label = meta ? meta.displayName : condition.attributeName;
    const value = condition.attributeValue;
    const op = condition.operator;

    const getCurrencySymbol = (code: string) => {
      const symbols: Record<string, string> = {
        'USD': '$', 'EUR': '€', 'GBP': '£', 'JPY': '¥', 'INR': '₹', 'AUD': 'A$', 'CAD': 'C$', 'CHF': 'CHF', 'CNY': '¥', 'HKD': 'HK$', 'NZD': 'NZ$', 'SEK': 'kr', 'KRW': '₩', 'SGD': 'S$', 'NOK': 'kr', 'MXN': '$',
      };
      return symbols[code] || code;
    };

    const formatNumber = (val: string) => {
      const n = parseFloat(val);
      if (isNaN(n)) return val;
      return new Intl.NumberFormat('en-US').format(n);
    };

    const opMap: Record<string, string> = {
      'EQ': 'is',
      'GT': 'is greater than',
      'LT': 'is less than',
      'GE': 'is greater than or equal to',
      'LE': 'is less than or equal to'
    };

    const friendlyOp = opMap[op] || op;

    if (meta?.dataType === 'BOOLEAN') {
      const isTrue = value === 'true';
      return (
        <span>
          User <span className="font-black text-blue-900">{isTrue ? 'is' : 'is NOT'}</span> {label}
        </span>
      );
    }

    let formattedValue = value;
    if (meta?.dataType === 'INTEGER' || meta?.dataType === 'DECIMAL') {
      formattedValue = formatNumber(value);
      if (condition.attributeName.toLowerCase().includes('balance') || condition.attributeName.toLowerCase().includes('income')) {
        formattedValue = `${getCurrencySymbol(currency)}${formattedValue}`;
      }
    }

    return (
      <span>
        <span className="text-blue-500">{label}</span> {friendlyOp} <span className="font-black text-blue-900">{formattedValue}</span>
      </span>
    );
  };

  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedRows);
    if (newExpanded.has(id)) newExpanded.delete(id);
    else newExpanded.add(id);
    setExpandedRows(newExpanded);
  };

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/pricing-components/${id}/activate`);
      setToast({ message: 'Component activated and is now ready for production use.', type: 'success' });
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Activation failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    if (!window.confirm('Are you sure you want to archive this component? This will make it immutable and it will no longer be available for new product links.')) return;
    try {
      await axios.post(`/api/v1/pricing-components/${id}/archive`);
      setToast({ message: 'Component archived successfully.', type: 'success' });
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archiving failed.', type: 'error' });
    }
  };

  const handleVersion = async (comp: PricingComponent) => {
    if (!window.confirm(`Create a new version (v${(comp.version ?? 1) + 1}) for ${comp.name}? The new version will be created as a DRAFT.`)) return;
    try {
      await axios.post(`/api/v1/pricing-components/${comp.id}/create-new-version`, {
        newName: comp.name
      });
      setToast({ message: 'New version created successfully as DRAFT.', type: 'success' });
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Version creation failed.', type: 'error' });
    }
  };

  const handleCopy = async (comp: PricingComponent) => {
    const newCode = window.prompt('Enter a unique code for the new component copy:', `${comp.code}_COPY`);
    if (!newCode) return;

    try {
      await axios.post(`/api/v1/pricing-components/${comp.id}/create-new-version`, {
        newName: `${comp.name} (Copy)`,
        newCode: newCode
      });
      setToast({ message: 'Component copied successfully as DRAFT.', type: 'success' });
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Copy failed.', type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Components cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/pricing-components/${id}`);
      setToast({ message: 'Component deleted successfully.', type: 'success' });
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Tag}
        tone="purple"
        title="Pricing Components"
        description="Manage reusable pricing structures with multi-tier logic."
        actions={
          <HasPermission action="POST" path="/api/v1/pricing-components">
            <button
              onClick={() => navigate('/pricing-components/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Component
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Expert Guidance">
        <span className="italic">Components define the “what” (for example Monthly Fee), while tiers define the “when” and “how much”. Linking a component to a product enables these rules to execute during bundle pricing.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Pricing components table" containerClassName="overflow-x-auto" className="min-w-[1200px]">
            <thead>
              <tr>
                <th>Aggregate Component</th>
                <th>Type</th>
                <th>Status</th>
                <th>Segments</th>
                <th>Created At</th>
                <th>Updated At</th>
                <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
              </tr>
            </thead>
            <tbody>
              {components.map((comp) => (
                <React.Fragment key={comp.id}>
                  <AdminDataTableRow interactive className="group" onClick={() => toggleExpand(comp.id)}>
                    <td className="max-w-[260px]">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <div className="text-sm font-bold text-gray-900 leading-tight truncate flex-1" title={comp.name}>{comp.name}</div>
                        <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                          v{comp.version ?? 1}
                        </span>
                        {expandedRows.has(comp.id) ? (
                          <ChevronUp className="h-4 w-4 text-gray-400 flex-shrink-0" />
                        ) : (
                          <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
                        )}
                      </div>
                      <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest truncate" title={comp.code}>{comp.code}</div>
                    </td>
                    <td className="whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${comp.type === 'FEE' ? 'bg-purple-50 text-purple-700 border border-purple-100' : 'bg-amber-50 text-amber-700 border border-amber-100'}`}>
                        {comp.type}
                      </span>
                    </td>
                    <td className="whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                        comp.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' :
                        comp.status === 'ARCHIVED' ? 'bg-gray-100 text-gray-600 border border-gray-200' :
                        'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                        {comp.status}
                      </span>
                    </td>
                    <td className="whitespace-nowrap font-bold text-gray-500">
                      {comp.pricingTiers?.length || 0} Tiers
                    </td>
                    <td className="min-w-[140px]" title={comp.createdAt || '--'}>
                      <AuditTimestampCell value={comp.createdAt} variant="expanded" />
                    </td>
                    <td className="min-w-[140px]" title={comp.updatedAt || '--'}>
                      <AuditTimestampCell value={comp.updatedAt} variant="expanded" />
                    </td>
                    <AdminDataTableActionCell>
                      {comp.status === 'DRAFT' && (
                        <>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/activate">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleActivate(comp.id); }} tone="success" size="compact" title="Activate Production Mode" aria-label={`Activate ${comp.name}`}>
                              <AdminDataTableActionContent action="activate" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="PATCH" path="/api/v1/pricing-components/*">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); navigate(`/pricing-components/edit/${comp.id}`); }} tone="primary" size="compact" title="Edit Structure" aria-label={`Edit ${comp.name}`}>
                              <AdminDataTableActionContent action="edit" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="DELETE" path="/api/v1/pricing-components/*">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleDelete(comp.id); }} tone="danger" size="compact" title="Permanently Delete" aria-label={`Delete ${comp.name}`}>
                              <AdminDataTableActionContent action="delete" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {comp.status === 'ACTIVE' && (
                        <>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleVersion(comp); }} tone="success" size="compact" title="Create a new draft version of this component" aria-label={`Version ${comp.name}`}>
                              <AdminDataTableActionContent action="version" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleCopy(comp); }} tone="primary" size="compact" title="Copy this component to a new lineage" aria-label={`Copy ${comp.name}`}>
                              <AdminDataTableActionContent action="copy" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="PATCH" path="/api/v1/pricing-components/*">
                            <AdminDataTableActionButton disabled onClick={(e) => { e.stopPropagation(); }} tone="neutral" size="compact" title="Active components cannot be edited. Create a new version instead." aria-label={`Edit ${comp.name} (Disabled)`}>
                              <AdminDataTableActionContent action="edit" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/activate">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleArchive(comp.id); }} tone="danger" size="compact" title="Archive this component" aria-label={`Archive ${comp.name}`}>
                              <AdminDataTableActionContent action="archive" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {comp.status === 'ARCHIVED' && (
                        <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                          <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); handleCopy(comp); }} tone="primary" size="compact" title="Copy this archived component to a new lineage" aria-label={`Copy ${comp.name}`}>
                            <AdminDataTableActionContent action="copy" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                  {expandedRows.has(comp.id) && (
                    <tr className="bg-gray-50/30">
                      <td colSpan={7} className="border-b border-gray-100 px-8 py-3 bg-gray-50/50">
                        <div className="text-xs font-medium text-gray-600 mb-4 italic leading-relaxed border-l-2 border-gray-200 pl-3">{comp.description}</div>
                        <div className="space-y-3">
                          {comp.pricingTiers?.map((tier, idx) => (
                            <div key={idx} className="bg-white p-5 rounded-xl border border-gray-200 shadow-sm hover:shadow-md transition flex flex-col md:flex-row md:items-center justify-between gap-6">
                              <div className="flex-1">
                                <div className="flex items-center space-x-2 mb-2">
                                  <span className="text-[9px] font-black bg-purple-100 text-purple-700 px-1.5 py-0.5 rounded uppercase tracking-tighter">Tier #{idx + 1}</span>
                                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{tier.code}</div>
                                </div>
                                <h4 className="font-bold text-gray-900 text-sm mb-3">{tier.name}</h4>

                                <div className="space-y-1.5">
                                  <div className="flex flex-wrap items-center gap-2">
                                    {tier.conditions?.map((c, cidx) => (
                                      <React.Fragment key={cidx}>
                                        <div className="bg-blue-50/40 text-blue-800 px-3 py-1.5 rounded-lg text-[10px] font-medium border border-blue-100/50 flex items-center shadow-sm">
                                          {formatCondition(c, user?.currencyCode || 'USD')}
                                        </div>
                                        {cidx < tier.conditions.length - 1 && (
                                          <span className="text-[9px] font-black text-gray-300 uppercase tracking-widest">{c.connector}</span>
                                        )}
                                      </React.Fragment>
                                    ))}
                                    {(!tier.conditions || tier.conditions.length === 0) && (
                                      <div className="text-[10px] text-gray-400 italic bg-gray-50 px-3 py-1 rounded-lg border border-gray-100">No segment conditions (Catch-all Tier)</div>
                                    )}
                                  </div>
                                </div>
                              </div>

                              <div className="flex items-center space-x-6 pl-6 border-l border-gray-100 min-w-[200px] justify-end">
                                <div className="text-right">
                                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1">Price Configuration</div>
                                  <div className="flex items-baseline justify-end space-x-1.5">
                                    <span className="text-2xl font-black text-blue-600 tracking-tighter">
                                      {tier.priceValue.valueType.includes('PERCENTAGE') ? '' : (user?.currencyCode || 'USD')}
                                      {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(tier.priceValue.priceAmount)}
                                      {tier.priceValue.valueType.includes('PERCENTAGE') ? '%' : ''}
                                    </span>
                                  </div>
                                  <div className="text-[9px] font-black text-blue-400 uppercase tracking-widest mt-0.5">{tier.priceValue.valueType.replace(/_/g, ' ')}</div>
                                </div>
                              </div>
                            </div>
                          ))}
                          {(!comp.pricingTiers || comp.pricingTiers.length === 0) && (
                            <div className="col-span-2 py-8 text-center text-xs text-gray-400 font-medium italic border border-dashed rounded-xl">This component has no logic defined. Click Edit to add tiers.</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
              {components.length === 0 && (
                <AdminDataTableEmptyRow colSpan={7}>No pricing components found.</AdminDataTableEmptyRow>
              )}
            </tbody>
        </AdminDataTable>
      )}

    </AdminPage>
  );
};

export default PricingComponentsPage;
