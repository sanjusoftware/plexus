import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, CircleDollarSign, ChevronDown, ChevronUp, Info, AlertTriangle, Layers, Clock } from 'lucide-react';
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
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { PricingService, TierCondition, PricingComponent, PricingTier } from '../../services/PricingService';

const PricingComponentsPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, setToast } = useAuth();
  const [components, setComponents] = useState<PricingComponent[]>([]);
  const [metadata, setMetadata] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());
  const [deleteTarget, setDeleteTarget] = useState<PricingComponent | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<PricingComponent | null>(null);
  const [versionTarget, setVersionTarget] = useState<PricingComponent | null>(null);
  const [copyTarget, setCopyTarget] = useState<PricingComponent | null>(null);
  const [copyCode, setCopyCode] = useState('');
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const normalizeTier = useCallback((tier: any): PricingTier => {
    const firstValue = tier?.priceValue || (Array.isArray(tier?.priceValues) && tier.priceValues.length > 0 ? tier.priceValues[0] : null);
    const normalizedPriceValue = firstValue ? {
        ...firstValue,
        priceAmount: firstValue.priceAmount ?? firstValue.rawValue ?? 0,
        rawValue: firstValue.rawValue ?? firstValue.priceAmount ?? 0
    } : null;

    return {
      ...tier,
      conditions: tier.conditions || [],
      priceValues: tier.priceValues || (normalizedPriceValue ? [normalizedPriceValue] : []),
      priceValue: normalizedPriceValue,
      applyChargeOnFullBreach: tier.applyChargeOnFullBreach ?? normalizedPriceValue?.applyChargeOnFullBreach ?? false
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

  useEffect(() => {
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (!successMessage || consumedSuccessKeyRef.current === location.key) {
      return;
    }

    consumedSuccessKeyRef.current = location.key;
    setToast({ message: successMessage, type: 'success' });
  }, [location.key, location.state, setToast]);

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
      PricingService.clearComponentCache();
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Activation failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    try {
      await axios.post(`/api/v1/pricing-components/${id}/archive`);
      setToast({ message: 'Component archived successfully.', type: 'success' });
      PricingService.clearComponentCache();
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archiving failed.', type: 'error' });
    }
  };

  const handleVersion = async (comp: PricingComponent) => {
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

  const handleCopy = async (comp: PricingComponent, newCode: string) => {
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

  const openCopyModal = (comp: PricingComponent) => {
    setCopyTarget(comp);
    setCopyCode(`${comp.code}_COPY`);
  };

  const handleDelete = async (id: number) => {
    try {
      await axios.delete(`/api/v1/pricing-components/${id}`);
      setToast({ message: 'Component deleted successfully.', type: 'success' });
      PricingService.clearComponentCache();
      await fetchData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={CircleDollarSign}
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
                    <td className="min-w-[140px]" title={(comp.updatedAt || comp.createdAt) || '--'}>
                      <AuditTimestampCell value={comp.updatedAt || comp.createdAt} variant="expanded" />
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
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); setDeleteTarget(comp); }} tone="danger" size="compact" title="Permanently Delete" aria-label={`Delete ${comp.name}`}>
                              <AdminDataTableActionContent action="delete" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {comp.status === 'ACTIVE' && (
                        <>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); setVersionTarget(comp); }} tone="success" size="compact" title="Create a new draft version of this component" aria-label={`Version ${comp.name}`}>
                              <AdminDataTableActionContent action="version" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); openCopyModal(comp); }} tone="primary" size="compact" title="Copy this component to a new lineage" aria-label={`Copy ${comp.name}`}>
                              <AdminDataTableActionContent action="copy" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/pricing-components/*/activate">
                            <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); setArchiveTarget(comp); }} tone="danger" size="compact" title="Archive this component" aria-label={`Archive ${comp.name}`}>
                              <AdminDataTableActionContent action="archive" />
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {comp.status === 'ARCHIVED' && (
                        <HasPermission action="POST" path="/api/v1/pricing-components/*/create-new-version">
                          <AdminDataTableActionButton onClick={(e) => { e.stopPropagation(); openCopyModal(comp); }} tone="primary" size="compact" title="Copy this archived component to a new lineage" aria-label={`Copy ${comp.name}`}>
                            <AdminDataTableActionContent action="copy" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                  {expandedRows.has(comp.id) && (
                    <tr className="bg-gray-50/30">
                      <td colSpan={6} className="border-b border-gray-100 px-8 py-5 bg-gray-50/50">
                        {/* Component Detail Summary */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                            <div className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm flex items-center space-x-3">
                                <div className="p-2 bg-purple-50 rounded-lg">
                                    <Layers className="w-5 h-5 text-purple-600" />
                                </div>
                                <div>
                                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Financial Type</p>
                                    <p className="text-sm font-bold text-gray-900">{comp.type}</p>
                                </div>
                            </div>
                            <div className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm flex items-center space-x-3">
                                <div className="p-2 bg-blue-50 rounded-lg">
                                    <Clock className="w-5 h-5 text-blue-600" />
                                </div>
                                <div>
                                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Pro-Rata Status</p>
                                    <p className={`text-sm font-bold ${comp.proRataApplicable ? 'text-green-600' : 'text-gray-500'}`}>
                                        {comp.proRataApplicable ? 'APPLICABLE' : 'NOT APPLICABLE'}
                                    </p>
                                </div>
                            </div>
                             <div className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm flex items-center space-x-3">
                                <div className="p-2 bg-amber-50 rounded-lg">
                                    <Info className="w-5 h-5 text-amber-600" />
                                </div>
                                <div>
                                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Pricing Strategy</p>
                                    <p className="text-sm font-bold text-gray-900">
                                        {comp.pricingTiers?.length > 1 ? 'MULTI-TIER DYNAMIC' : 'FLAT PRICE'}
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="text-xs font-medium text-gray-600 mb-6 italic leading-relaxed border-l-4 border-gray-200 pl-4 bg-white p-3 rounded-r-xl border border-gray-100 shadow-sm">
                            {comp.description || 'No detailed description provided for this component.'}
                        </div>

                        <div className="space-y-4">
                          {[...comp.pricingTiers].sort((a, b) => (b.priority || 0) - (a.priority || 0)).map((tier, idx) => (
                            <div key={idx} className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm hover:shadow-md transition flex flex-col md:flex-row md:items-center justify-between gap-6 relative group/tier">
                              <div className="flex-1">
                                <div className="flex items-center space-x-2 mb-3">
                                  <span className="text-[9px] font-black bg-purple-100 text-purple-700 px-2 py-1 rounded uppercase tracking-tighter">Tier #{idx + 1}</span>
                                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest border-l border-gray-200 pl-2">{tier.code}</div>
                                  <div className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter flex items-center">
                                      PRIORITY: {tier.priority ?? 0}
                                  </div>
                                </div>
                                <h4 className="font-black text-gray-900 text-base mb-4">{tier.name}</h4>

                                <div className="space-y-3">
                                    {/* Thresholds */}
                                    {(tier.minThreshold !== null && tier.minThreshold !== undefined || tier.maxThreshold !== null && tier.maxThreshold !== undefined) && (
                                        <div className="flex items-center gap-4 mb-2">
                                            {tier.minThreshold !== null && tier.minThreshold !== undefined && (
                                                <div className="text-[10px] font-bold text-gray-500 bg-gray-50 px-2 py-1 rounded border border-gray-100">
                                                    MIN: <span className="text-blue-600 font-black">{tier.minThreshold}</span>
                                                </div>
                                            )}
                                            {tier.maxThreshold !== null && tier.maxThreshold !== undefined && (
                                                <div className="text-[10px] font-bold text-gray-500 bg-gray-50 px-2 py-1 rounded border border-gray-100">
                                                    MAX: <span className="text-blue-600 font-black">{tier.maxThreshold}</span>
                                                </div>
                                            )}
                                        </div>
                                    )}

                                  <div className="flex flex-wrap items-center gap-2">
                                    {tier.conditions?.map((c, cidx) => (
                                      <React.Fragment key={cidx}>
                                        <div className="bg-blue-50/60 text-blue-900 px-3 py-2 rounded-xl text-[10px] font-bold border border-blue-100 flex items-center shadow-sm">
                                          {formatCondition(c, user?.currencyCode || 'USD')}
                                        </div>
                                        {cidx < tier.conditions.length - 1 && (
                                          <span className="text-[10px] font-black text-gray-300 uppercase tracking-widest mx-1">{c.connector}</span>
                                        )}
                                      </React.Fragment>
                                    ))}
                                    {(!tier.conditions || tier.conditions.length === 0) && (
                                      <div className="text-[10px] text-gray-400 font-bold uppercase tracking-widest bg-gray-50 px-4 py-2 rounded-xl border border-gray-100 border-dashed">Catch-all / Unconditional Tier</div>
                                    )}
                                  </div>
                                </div>
                              </div>

                              <div className="flex flex-col items-end space-y-2 pl-8 border-l border-gray-100 min-w-[220px] justify-center">
                                  <div className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Price Configuration</div>
                                  <div className="flex items-baseline justify-end space-x-1.5">
                                    <span className="text-3xl font-black text-blue-600 tracking-tighter">
                                      {tier.priceValue?.valueType.includes('PERCENTAGE') ? '' : (user?.currencyCode || 'USD')}
                                      {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(tier.priceValue?.priceAmount || 0)}
                                      {tier.priceValue?.valueType.includes('PERCENTAGE') ? '%' : ''}
                                    </span>
                                  </div>
                                  <div className={`text-[10px] font-black px-2 py-0.5 rounded uppercase tracking-widest mt-1 ${tier.priceValue ? PricingService.getValueTypeColor(tier.priceValue.valueType) : 'text-gray-500 bg-gray-50'}`}>
                                      {tier.priceValue?.valueType.replace(/_/g, ' ') || '—'}
                                  </div>
                                  {tier.applyChargeOnFullBreach && (
                                      <div className="flex items-center gap-1 text-[9px] font-black text-amber-600 uppercase tracking-tighter mt-2 bg-amber-50 px-2 py-1 rounded border border-amber-100">
                                          <AlertTriangle className="w-3 h-3" /> Full Breach Applied
                                      </div>
                                  )}
                              </div>
                            </div>
                          ))}
                          {(!comp.pricingTiers || comp.pricingTiers.length === 0) && (
                            <div className="col-span-2 py-12 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest border-2 border-dashed rounded-3xl bg-white">This component has no logic defined. Click Edit to add tiers.</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
              {components.length === 0 && (
                <AdminDataTableEmptyRow colSpan={6}>No pricing components found.</AdminDataTableEmptyRow>
              )}
            </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && handleDelete(deleteTarget.id)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete component "${deleteTarget?.name || deleteTarget?.code}"? Components cannot be deleted if they are linked to active products.`}
        confirmText="Confirm & Delete"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={!!archiveTarget}
        onClose={() => setArchiveTarget(null)}
        onConfirm={() => archiveTarget && handleArchive(archiveTarget.id)}
        title="Confirm Archival"
        message={`Are you sure you want to archive component "${archiveTarget?.name || archiveTarget?.code}"? This will make it immutable and unavailable for new product links.`}
        confirmText="Confirm & Archive"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={!!versionTarget}
        onClose={() => setVersionTarget(null)}
        onConfirm={() => versionTarget && handleVersion(versionTarget)}
        title="Confirm Version Creation"
        message={`Create a new version (v${((versionTarget?.version ?? 1) + 1)}) for "${versionTarget?.name || versionTarget?.code}"? The new version will be created as DRAFT.`}
        confirmText="Confirm & Create Version"
        variant="info"
      />

      <ConfirmationModal
        isOpen={!!copyTarget}
        onClose={() => {
          setCopyTarget(null);
          setCopyCode('');
        }}
        onConfirm={() => copyTarget && handleCopy(copyTarget, copyCode.trim())}
        title="Confirm Component Copy"
        message={`Create a DRAFT copy of "${copyTarget?.name || copyTarget?.code}" with a new unique code.`}
        confirmText="Confirm & Copy"
        variant="info"
        confirmDisabled={!copyCode.trim()}
      >
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1.5">New Component Code</label>
          <input
            type="text"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            value={copyCode}
            onChange={(e) => setCopyCode(e.target.value)}
            placeholder="Enter unique code"
          />
        </div>
      </ConfirmationModal>
    </AdminPage>
  );
};

export default PricingComponentsPage;
