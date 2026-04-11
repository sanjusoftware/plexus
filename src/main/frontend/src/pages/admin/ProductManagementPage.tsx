import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Package, ShieldCheck, Tag, Info, ChevronDown, ChevronUp, Play, RefreshCw, Zap } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
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
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { PriceComponentDetail, PricingMetadata, PricingService } from '../../services/PricingService';
import PlexusSelect from '../../components/PlexusSelect';

interface FeatureLink {
  featureComponentCode: string;
  featureName?: string;
  featureValue: string;
}

interface PricingLink {
  pricingComponentCode: string;
  pricingComponentName?: string;
  fixedValue?: number;
  fixedValueType?: string;
  useRulesEngine: boolean;
  targetComponentCode?: string;
}

interface Product {
  id: number;
  code: string;
  name: string;
  version?: number;
  productType?: {
    code: string;
    name: string;
  };
  category: string;
  status: string;
  activationDate: string;
  tagline: string;
  features: FeatureLink[];
  pricing: PricingLink[];
  fullDescription: string;
  createdAt?: string;
  updatedAt?: string;
}

interface SimulationResult {
  price: number;
  loading: boolean;
  error?: string;
  breakdown?: PriceComponentDetail[];
}

const HIDDEN_METADATA_KEYS = new Set(['bankId', 'productId', 'productBundleId']);

const ProductManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [simulationOpenIds, setSimulationOpenIds] = useState<Set<number>>(new Set());
  const [calculatedPrices, setCalculatedPrices] = useState<Record<number, SimulationResult>>({});
  const [calcMetadata, setCalcMetadata] = useState<PricingMetadata[]>([]);
  const [calcInputs, setCalcInputs] = useState<Record<string, any>>({
    transactionAmount: 1000,
    customerSegment: 'RETAIL',
    effectiveDate: new Date().toISOString().split('T')[0],
    loyalty_score: 0,
    isSalaryAccount: false,
  });

  // Modal states
  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; productId?: number }>({ isOpen: false });
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean; productId?: number; productName?: string }>({ isOpen: false });
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();


  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedIds);
    if (newExpanded.has(id)) {
      newExpanded.delete(id);
      setSimulationOpenIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    } else {
      newExpanded.add(id);
    }
    setExpandedIds(newExpanded);
  };

  const toggleSimulation = (id: number) => {
    setSimulationOpenIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const [p, metadata] = await Promise.all([
        axios.get('/api/v1/products', { signal: abortSignal }),
        PricingService.getPricingMetadata(abortSignal).catch(() => []),
      ]);
      setProducts(p.data.content || []);

      const dynamicFields = (metadata || [])
        .filter((m: PricingMetadata) => (m.sourceType || 'CUSTOM_ATTRIBUTE') === 'CUSTOM_ATTRIBUTE')
        .filter((m: PricingMetadata) => !HIDDEN_METADATA_KEYS.has(m.attributeKey));
      setCalcMetadata(dynamicFields);

      setCalcInputs(prev => {
        const next = { ...prev };
        dynamicFields.forEach((m: PricingMetadata) => {
          if (next[m.attributeKey] !== undefined) return;
          switch ((m.dataType || '').toUpperCase()) {
            case 'DECIMAL':
            case 'INTEGER':
            case 'LONG':
              next[m.attributeKey] = m.attributeKey === 'transactionAmount' ? 1000 : 0;
              break;
            case 'BOOLEAN':
              next[m.attributeKey] = false;
              break;
            case 'DATE':
              next[m.attributeKey] = new Date().toISOString().split('T')[0];
              break;
            default:
              next[m.attributeKey] = m.attributeKey === 'customerSegment' ? 'RETAIL' : '';
          }
        });
        return next;
      });
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch products. Please check your role permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  const parseInputValue = (dataType: string, rawValue: any) => {
    const normalizedType = (dataType || '').toUpperCase();
    if (rawValue === '' || rawValue === undefined || rawValue === null) return undefined;

    if (normalizedType === 'DECIMAL') return Number(rawValue);
    if (normalizedType === 'INTEGER' || normalizedType === 'LONG') return parseInt(rawValue, 10);
    if (normalizedType === 'BOOLEAN') return rawValue === true || rawValue === 'true';
    return rawValue;
  };

  const renderDynamicField = (meta: PricingMetadata) => {
    const key = meta.attributeKey;
    const value = calcInputs[key] ?? '';
    const dataType = (meta.dataType || '').toUpperCase();
    const label = (meta.displayName || key).toUpperCase();

    if (dataType === 'BOOLEAN') {
      return (
        <div key={key}>
          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{label}</label>
          <PlexusSelect
            compact
            options={[{ value: 'true', label: 'TRUE' }, { value: 'false', label: 'FALSE' }]}
            value={{ value: String(value), label: String(value).toUpperCase() }}
            onChange={(opt) => setCalcInputs(prev => ({ ...prev, [key]: opt ? opt.value === 'true' : false }))}
          />
        </div>
      );
    }

    if (key === 'customerSegment') {
      return (
        <div key={key}>
          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{label}</label>
          <PlexusSelect
            compact
            options={[
              { value: 'RETAIL', label: 'RETAIL' },
              { value: 'PREMIUM', label: 'PREMIUM' },
              { value: 'CORPORATE', label: 'CORPORATE' },
              { value: 'VIP', label: 'VIP' }
            ]}
            value={{ value: String(value || 'RETAIL'), label: String(value || 'RETAIL') }}
            onChange={(opt) => setCalcInputs(prev => ({ ...prev, [key]: opt ? opt.value : 'RETAIL' }))}
          />
        </div>
      );
    }

    const inputType = dataType === 'DATE'
      ? 'date'
      : (dataType === 'DECIMAL' || dataType === 'INTEGER' || dataType === 'LONG')
        ? 'number'
        : 'text';

    return (
      <div key={key}>
        <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{label}</label>
        <input
          type={inputType}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-[11px] font-semibold bg-white focus:border-blue-500 transition h-[36px]"
          value={value}
          onChange={(e) => setCalcInputs(prev => ({ ...prev, [key]: e.target.value }))}
        />
      </div>
    );
  };

  useEffect(() => {
    fetchInitialData(signal);
    const successMessage = (location.state as { success?: string } | null)?.success;
    if (successMessage && consumedSuccessKeyRef.current !== location.key) {
      consumedSuccessKeyRef.current = location.key;
      setToast({ message: successMessage, type: 'success' });
    }
  }, [location, setToast, fetchInitialData, signal]);

  const handleStatusAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/products/${id}/${action}`);
      setToast({ message: `Product ${action}d successfully.`, type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} product.`, type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axios.delete(`/api/v1/products/${id}`);
      setToast({ message: 'Product deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    try {
      await axios.post(`/api/v1/products/${id}/deactivate`);
      setToast({ message: 'Product archived successfully. It is now terminal and removed from active circulation.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archival failed.', type: 'error' });
    }
  };

  const handleVersion = async (id: number) => {
    try {
      await axios.post(`/api/v1/products/${id}/create-new-version`, {});
      setToast({ message: 'New version created in DRAFT status. You can now edit this new version.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Versioning failed.', type: 'error' });
    }
  };

  const handleCalculatePrice = async (prod: Product) => {
    setCalculatedPrices(prev => ({
      ...prev,
      [prod.id]: {
        ...(prev[prod.id] || { price: 0 }),
        loading: true,
        error: undefined,
      }
    }));
    try {
      const attributesFromMetadata: Record<string, any> = {};
      calcMetadata.forEach(meta => {
        const parsed = parseInputValue(meta.dataType, calcInputs[meta.attributeKey]);
        if (parsed !== undefined && !Number.isNaN(parsed)) {
          attributesFromMetadata[meta.attributeKey] = parsed;
        }
      });

      if (!attributesFromMetadata.effectiveDate) {
        attributesFromMetadata.effectiveDate = new Date().toISOString().split('T')[0];
      }

      const request = {
        productId: prod.id,
        enrollmentDate: new Date().toISOString().split('T')[0],
        customAttributes: attributesFromMetadata
      };

      const result = await PricingService.calculateProductPrice(request);
      setCalculatedPrices(prev => ({
        ...prev,
        [prod.id]: {
          price: Number(result.finalChargeablePrice || 0),
          loading: false,
          breakdown: result.componentBreakdown || []
        }
      }));
    } catch (err: any) {
      setCalculatedPrices(prev => ({
        ...prev,
        [prod.id]: { price: 0, loading: false, error: 'Calc Failed' }
      }));
      setToast({ message: err.response?.data?.message || 'Price calculation failed.', type: 'error' });
    }
  };

  const renderBreakdown = (productId: number, onRefresh?: () => void) => {
    const simulation = calculatedPrices[productId];
    if (!simulation || simulation.loading || simulation.error) return null;

    const breakdown = simulation.breakdown || [];
    const charges = breakdown.filter(item => !item.valueType?.startsWith('DISCOUNT'));
    const discounts = breakdown.filter(item => item.valueType?.startsWith('DISCOUNT'));
    const totalCharges = charges.reduce((sum, item) => sum + Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0)), 0);
    const totalDiscounts = discounts.reduce((sum, item) => sum + Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0)), 0);

    if (breakdown.length === 0) {
      return (
        <div className="mt-4 text-[10px] font-bold text-gray-500 bg-white p-3 rounded-lg border border-gray-200">
          No component-level breakdown returned by pricing engine for this run.
        </div>
      );
    }

    return (
      <div className="mt-4 bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="px-4 py-2.5 border-b border-gray-100 bg-gray-50">
          <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700">Calculation Receipt</h5>
        </div>

        <div className="p-3 space-y-2">
          {charges.map((item, idx) => {
            const amount = Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0));
            return (
              <div key={`charge-${item.componentCode}-${idx}`} className="flex items-center justify-between p-2.5 rounded-lg border border-blue-100 bg-blue-50/30">
                <div className="min-w-0 pr-3">
                  <div className="text-[11px] font-bold text-gray-800 truncate">{item.componentCode}</div>
                  <div className="text-[9px] text-gray-500 uppercase tracking-wider">
                    {item.valueType}{item.matchedTierCode ? ` | Tier: ${item.matchedTierCode}` : ''}
                  </div>
                </div>
                <div className="text-xs font-black whitespace-nowrap text-blue-600">
                  {PricingService.formatCurrency(amount)}
                </div>
              </div>
            );
          })}

          {discounts.map((item, idx) => {
            const amount = Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0));
            return (
              <div key={`discount-${item.componentCode}-${idx}`} className="flex items-center justify-between p-2.5 rounded-lg border border-green-100 bg-green-50/30">
                <div className="min-w-0 pr-3">
                  <div className="text-[11px] font-bold text-gray-800 truncate">{item.componentCode}</div>
                  <div className="text-[9px] text-gray-500 uppercase tracking-wider">
                    {item.valueType}{item.matchedTierCode ? ` | Tier: ${item.matchedTierCode}` : ''}
                  </div>
                </div>
                <div className="text-xs font-black whitespace-nowrap text-green-600">
                  -{PricingService.formatCurrency(amount)}
                </div>
              </div>
            );
          })}

          {discounts.length === 0 && (
            <div className="text-[10px] text-gray-500 bg-gray-50 p-2.5 rounded-lg border border-gray-100">No discounts applied.</div>
          )}
        </div>

        <div className="px-4 py-3 border-t border-gray-100 bg-gray-50/70">
          <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-gray-500">
            <span>Total Charges</span>
            <span className="text-blue-600">{PricingService.formatCurrency(totalCharges)}</span>
          </div>
          <div className="mt-1 flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-gray-500">
            <span>Total Discounts</span>
            <span className="text-green-600">-{PricingService.formatCurrency(totalDiscounts)}</span>
          </div>
          <div className="mt-2 pt-2 border-t border-gray-200 flex items-center justify-between">
            <span className="text-[10px] font-black uppercase tracking-widest text-gray-700">Final Chargeable Price</span>
            <div className="flex items-center gap-2">
              <span className="text-sm font-black text-blue-600">{PricingService.formatCurrency(simulation.price)}</span>
              <button
                onClick={onRefresh}
                className="p-1 text-blue-500 hover:bg-blue-100 rounded transition"
                title="Refresh Calculation"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Package}
        title="Product Management"
        description="Configure bank offerings, feature sets, and pricing bindings."
        actions={
          <HasPermission action="POST" path="/api/v1/products">
            <button
              onClick={() => navigate('/products/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Product
            </button>
          </HasPermission>
        }
      />

      <AdminInfoBanner icon={Info} title="Fat DTO Architecture" tone="indigo">
        <span className="italic">This system uses a decoupled model. Products are aggregates that link to reusable <strong>Feature Components</strong> and <strong>Pricing Components</strong>. You can create the product, its features, and its pricing links in one atomic request below.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product management table" containerClassName="overflow-x-auto" className="min-w-[1200px]">
          <thead>
            <tr>
              <th>Product Details</th>
              <th>Product Type</th>
              <th>Category</th>
              <th className="text-center">Status</th>
              <th>Updated At</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {products.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={6}>No active products found. Get started by clicking "Create New Product".</AdminDataTableEmptyRow>
            ) : (
              products.map((prod) => (
                <React.Fragment key={prod.id}>
                  <AdminDataTableRow
                    onClick={() => toggleExpand(prod.id)}
                    interactive
                    className="group"
                  >
                    {/* Product Info */}
                    <td className="max-w-[300px]">
                      <div className="flex items-center space-x-3">
                        <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300 flex-shrink-0">
                          <Package className="w-4 h-4 text-blue-600 group-hover:text-white transition duration-300" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 min-w-0">
                            <h3 className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900 transition leading-tight flex-1" title={prod.name}>{prod.name}</h3>
                            <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                              v{prod.version ?? 1}
                            </span>
                            {expandedIds.has(prod.id) ? (
                              <ChevronUp className="h-4 w-4 text-gray-400 flex-shrink-0" />
                            ) : (
                              <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
                            )}
                          </div>
                          <div className="text-[9px] font-mono font-bold text-blue-600 uppercase tracking-tight truncate">{prod.code}</div>
                        </div>
                      </div>
                    </td>

                    {/* Classification */}
                    <td className="max-w-[200px]">
                      {prod.productType ? (
                        <div className="truncate">
                          <div className="text-[11px] font-bold text-gray-700 leading-tight truncate" title={prod.productType.name}>{prod.productType.name}</div>
                          <div className="text-[9px] font-mono text-gray-400 truncate" title={prod.productType.code}>{prod.productType.code}</div>
                        </div>
                      ) : (
                        <div className="text-[10px] text-gray-300 italic uppercase">None</div>
                      )}
                    </td>

                    {/* Category */}
                    <td>
                      <div className="text-[11px] font-bold text-gray-600 uppercase truncate">
                        {prod.category}
                      </div>
                    </td>

                    {/* Status */}
                    <td className="text-center">
                      <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                        {prod.status}
                      </span>
                    </td>

                    {/* Updated At */}
                    <AuditTimestampCell value={prod.updatedAt || prod.createdAt} />

                    {/* Actions */}
                    <AdminDataTableActionCell onClick={(e) => e.stopPropagation()}>
                      {prod.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/products/*/activate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleStatusAction(prod.id, 'activate'); }}
                            tone="success"
                            size="compact"
                            title="Activate Product"
                          >
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {prod.status === 'ACTIVE' && (
                        <HasPermission action="POST" path="/api/v1/products/*/create-new-version">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleVersion(prod.id); }}
                            tone="success"
                            size="compact"
                            title="Create Revision (v+1)"
                          >
                            <AdminDataTableActionContent action="version" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {(prod.status === 'DRAFT' || prod.status === 'ACTIVE') && (
                        <HasPermission action="PATCH" path="/api/v1/products/*">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); prod.status === 'DRAFT' && navigate(`/products/edit/${prod.id}`); }}
                            tone="primary"
                            size="compact"
                            disabled={prod.status !== 'DRAFT'}
                            title={prod.status === 'DRAFT' ? "Modify Product" : "Direct editing is not allowed for active products. Create a new version to make changes."}
                          >
                            <AdminDataTableActionContent action="edit" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      <HasPermission action="DELETE" path="/api/v1/products/*">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); prod.status === 'ACTIVE' ? setArchiveModal({ isOpen: true, productId: prod.id }) : setDeleteModal({ isOpen: true, productId: prod.id, productName: prod.name }); }}
                          tone="danger"
                          size="compact"
                          title={prod.status === 'ACTIVE' ? "Archive Product" : "Delete Product"}
                        >
                          <AdminDataTableActionContent action={prod.status === 'ACTIVE' ? 'archive' : 'delete'} />
                        </AdminDataTableActionButton>
                      </HasPermission>
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                  {expandedIds.has(prod.id) && (
                    <tr className="bg-white">
                      <td colSpan={6} className="p-0 border-b border-gray-100">
                        <div className="p-6 grid grid-cols-1 lg:grid-cols-2 gap-8">
                    <div>
                      <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-blue-50/50 p-1.5 rounded-lg w-fit">
                        <ShieldCheck className="w-3 h-3 mr-1.5 text-blue-500" /> Linked Feature components
                      </h4>
                      <div className="space-y-2">
                        {prod.features?.map((f, idx) => (
                          <div key={idx} className="flex justify-between items-center text-xs p-3 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-blue-100 hover:bg-white transition shadow-sm">
                            <div className="flex flex-col">
                              <span className="font-bold text-gray-700">{f.featureName || 'Unnamed Feature'}</span>
                              <span className="text-[9px] font-mono text-gray-400">{f.featureComponentCode}</span>
                            </div>
                            <span className="font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-lg text-[10px]">{f.featureValue}</span>
                          </div>
                        ))}
                        {(!prod.features || prod.features.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No feature components bound to this product.</p>}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-purple-50/50 p-1.5 rounded-lg w-fit">
                        <Tag className="w-3 h-3 mr-1.5 text-purple-500" /> Pricing rule bindings
                      </h4>
                      <div className="space-y-2">
                        {prod.pricing?.map((p, idx) => (
                          <div key={idx} className="flex justify-between items-center text-xs p-3 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-purple-100 hover:bg-white transition shadow-sm">
                            <div className="flex flex-col">
                              <span className="font-bold text-gray-700">{p.pricingComponentName || 'Unnamed Component'}</span>
                              <span className="text-[9px] font-mono text-gray-400">{p.pricingComponentCode}</span>
                            </div>
                            <span className={`font-bold px-2 py-0.5 rounded-lg text-[10px] ${p.useRulesEngine ? 'bg-amber-100 text-amber-700' : 'bg-purple-100 text-purple-700'}`}>
                              {p.useRulesEngine ? 'DYNAMIC RULES' : `${p.fixedValue} (${p.fixedValueType})`}
                            </span>
                          </div>
                        ))}
                        {(!prod.pricing || prod.pricing.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No pricing rules bound to this product.</p>}
                      </div>
                    </div>

                    {/* Inline Pricing Simulation Tool */}
                    <div className="lg:col-span-2 bg-blue-50/50 rounded-2xl p-4 border border-blue-100 mt-2">
                      <div
                        role="button"
                        tabIndex={0}
                        className="flex items-center justify-between cursor-pointer"
                        onClick={() => toggleSimulation(prod.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            toggleSimulation(prod.id);
                          }
                        }}
                      >
                        <div className="flex items-center space-x-3">
                          <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-200">
                            <Zap className="w-4 h-4 text-white" />
                          </div>
                          <div>
                            <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight">Real-Time Pricing Simulation</h4>
                            <p className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Test this product aggregate with custom parameters</p>
                          </div>
                        </div>
                        {simulationOpenIds.has(prod.id)
                          ? <ChevronUp className="w-5 h-5 text-gray-400" />
                          : <ChevronDown className="w-5 h-5 text-gray-400" />}
                      </div>

                      {simulationOpenIds.has(prod.id) && (
                        <div className="mt-4 pt-4 border-t border-blue-100 grid grid-cols-1 lg:grid-cols-2 gap-6">
                          <div className="bg-white rounded-xl border border-gray-200 p-4">
                            <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Calculation Inputs</h5>
                            <div className="space-y-3">
                              {calcMetadata.length > 0
                                ? calcMetadata.map(renderDynamicField)
                                : (
                                  <div className="text-[10px] font-bold text-amber-600 bg-amber-50 p-2 rounded-lg border border-amber-100">
                                    Pricing metadata not available. Ensure `pricing:metadata:read` is granted to load dynamic inputs.
                                  </div>
                                )}
                            </div>
                            <div className="pt-3 mt-3 border-t border-gray-100">
                              <button
                                onClick={() => handleCalculatePrice(prod)}
                                disabled={calculatedPrices[prod.id]?.loading}
                                className="w-full bg-blue-600 text-white rounded-lg h-[42px] px-8 font-black uppercase tracking-widest text-[10px] hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center space-x-2 disabled:opacity-50"
                              >
                                {calculatedPrices[prod.id]?.loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                                <span>Run Calculation</span>
                              </button>
                            </div>
                          </div>

                          <div>
                            {calculatedPrices[prod.id]?.loading && (
                              <div className="h-full min-h-[180px] bg-white rounded-xl border border-gray-200 flex items-center justify-center">
                                <div className="flex items-center gap-2 text-blue-600 text-sm font-bold">
                                  <Loader2 className="w-4 h-4 animate-spin" />
                                  Calculating...
                                </div>
                              </div>
                            )}
                            {calculatedPrices[prod.id]?.error && (
                              <div className="text-[10px] font-bold text-red-500 bg-red-50 p-3 rounded-lg border border-red-100">
                                ⚠️ Simulation Failed: {calculatedPrices[prod.id].error}
                              </div>
                            )}
                            {!calculatedPrices[prod.id] && (
                              <div className="h-full min-h-[180px] bg-white rounded-xl border border-gray-200 p-4 text-sm text-gray-500 flex items-center justify-center text-center">
                                Run calculation to see a receipt-style component breakdown.
                              </div>
                            )}
                            {calculatedPrices[prod.id] && !calculatedPrices[prod.id].loading && !calculatedPrices[prod.id].error && (
                              renderBreakdown(prod.id, () => handleCalculatePrice(prod))
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))
            )}
          </tbody>
        </AdminDataTable>
      )}

      <ConfirmationModal
        isOpen={deleteModal.isOpen}
        onClose={() => setDeleteModal({ isOpen: false })}
        onConfirm={() => deleteModal.productId && handleDelete(deleteModal.productId)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete "${deleteModal.productName || 'this product'}"? This action cannot be undone.`}
        confirmText="Confirm & Delete"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={archiveModal.isOpen}
        onClose={() => setArchiveModal({ isOpen: false })}
        onConfirm={() => archiveModal.productId && handleArchive(archiveModal.productId)}
        title="Confirm Product Archival"
        message="Are you sure you want to archive this product? This action is terminal: it will immediately remove the product from active circulation and it cannot be re-activated. Customers currently enrolled will be affected based on bank policy."
        confirmText="Archive Product"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductManagementPage;
