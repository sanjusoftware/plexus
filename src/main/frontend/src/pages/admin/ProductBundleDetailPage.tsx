import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Library,
  Loader2,
  Package,
  Tag,
  Zap,
  Play,
  RefreshCw,
  Edit,
  Trash2,
  CheckCircle2,
  History,
  Archive,
  ChevronUp,
  ChevronDown
} from 'lucide-react';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { useSystemPricingKeys } from '../../hooks/useSystemPricingKeys';
import { PricingMetadata, PricingService, ProductPricingCalculationResult, PriceComponentDetail } from '../../services/PricingService';
import ConfirmationModal from '../../components/ConfirmationModal';
import PlexusSelect from '../../components/PlexusSelect';
import {
  formatComponentLabelWithProRata,
  getSimulationFieldHelperText
} from './ProductManagementPage.utils';

interface BundleProduct {
  productId: number;
  productName: string;
  productCode: string;
  mainAccount: boolean;
  mandatory: boolean;
}

interface BundlePricing {
  pricingComponentId: number;
  pricingComponentName: string;
  targetComponentCode?: string;
  fixedValue?: number;
  fixedValueType?: string;
  useRulesEngine: boolean;
  effectiveDate?: string;
  expiryDate?: string | null;
}

interface ProductBundle {
  id: number;
  code: string;
  name: string;
  version?: number;
  status: string;
  targetCustomerSegments: string;
  activationDate: string;
  description: string;
  products: BundleProduct[];
  pricing: BundlePricing[];
  createdAt?: string;
  updatedAt?: string;
}

interface BundleSimulationResult {
  totalPrice: number;
  loading: boolean;
  error?: string;
  detailedError?: string;
  productsBreakdown?: ProductPricingCalculationResult[];
}

const toTitleFromCode = (value: string) =>
  (value || '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

const ProductBundleDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const {
    keys,
    hasSystemPricingKey,
    isHiddenSystemKey,
    isSystemAmountKey,
    isSystemDateKey,
    isSystemCustomerSegmentKey,
  } = useSystemPricingKeys();

  const [bundle, setBundle] = useState<ProductBundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [simulationOpen, setSimulationOpen] = useState(false);
  const [calculatedPrice, setCalculatedPrice] = useState<BundleSimulationResult | null>(null);
  const [calcMetadata, setCalcMetadata] = useState<PricingMetadata[]>([]);
  const [calcInputs, setCalcInputs] = useState<Record<string, any>>({
    [keys.CUSTOMER_SEGMENT]: 'RETAIL',
    [keys.EFFECTIVE_DATE]: new Date().toISOString().split('T')[0],
  });
  const [productAmounts, setProductAmounts] = useState<Record<string, number>>({});

  // Modal states
  const [archiveModalOpen, setArchiveModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const signal = useAbortSignal();

  const fetchBundleData = useCallback(async () => {
    setLoading(true);
    try {
      const [bRes, metadata] = await Promise.all([
        axios.get(`/api/v1/bundles/${id}`, { signal }),
        PricingService.getPricingMetadata(signal).catch(() => []),
      ]);
      const data = bRes.data;
      setBundle(data);
      setEntityName(data.name);

      const dynamicFields = (metadata || [])
        .filter((m: PricingMetadata) => (m.sourceType || 'CUSTOM_ATTRIBUTE') === 'CUSTOM_ATTRIBUTE')
        .filter((m: PricingMetadata) => !isHiddenSystemKey(m.attributeKey || ''));
      setCalcMetadata(dynamicFields);

      setCalcInputs(prev => {
        const next = { ...prev };
        dynamicFields.forEach((m: PricingMetadata) => {
          if (next[m.attributeKey] !== undefined) return;
          switch ((m.dataType || '').toUpperCase()) {
            case 'DECIMAL':
            case 'INTEGER':
            case 'LONG':
              next[m.attributeKey] = isSystemAmountKey(m.attributeKey || '') ? 1000 : 0;
              break;
            case 'BOOLEAN':
              next[m.attributeKey] = false;
              break;
            case 'DATE':
              next[m.attributeKey] = new Date().toISOString().split('T')[0];
              break;
            default:
              next[m.attributeKey] = isSystemCustomerSegmentKey(m.attributeKey || '') ? 'RETAIL' : '';
          }
        });
        return next;
      });
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch bundle details.', type: 'error' });
      navigate('/bundles');
    } finally {
      if (!signal.aborted) {
        setLoading(false);
      }
    }
  }, [id, signal, setEntityName, isHiddenSystemKey, isSystemAmountKey, isSystemCustomerSegmentKey, setToast, navigate]);

  useEffect(() => {
    fetchBundleData();
  }, [fetchBundleData]);

  const handleStatusAction = async (action: string) => {
    try {
      await axios.post(`/api/v1/bundles/${id}/${action}`);
      setToast({ message: `Bundle ${action}d successfully.`, type: 'success' });
      fetchBundleData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} bundle.`, type: 'error' });
    }
  };

  const handleDelete = async () => {
    try {
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle deleted successfully.', type: 'success' });
      navigate('/bundles');
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  const handleArchive = async () => {
    try {
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle archived successfully.', type: 'success' });
      navigate('/bundles');
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archival failed.', type: 'error' });
    }
  };

  const handleVersion = async () => {
    try {
      const res = await axios.post(`/api/v1/bundles/${id}/create-new-version`, {});
      setToast({ message: 'New version created in DRAFT status.', type: 'success' });
      navigate(`/bundles/edit/${res.data.id || id}`);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Versioning failed.', type: 'error' });
    }
  };

  const getFieldGroups = (metadata: PricingMetadata[]) => {
    const groups: (PricingMetadata[])[] = [];
    const processed = new Set<string>();
    const pairings: Record<string, string> = {
      EFFECTIVE_DATE: 'ENROLLMENT_DATE',
      ENROLLMENT_DATE: 'EFFECTIVE_DATE',
      TRANSACTION_AMOUNT: 'LOYALTY_SCORE',
      LOYALTY_SCORE: 'TRANSACTION_AMOUNT',
      IS_SALARY_ACCOUNT: 'LOYALTY_SCORE',
    };
    const excludedFields = ['GROSS_TOTAL_AMOUNT'];

    metadata.forEach((meta) => {
      if (processed.has(meta.attributeKey) || excludedFields.includes((meta.attributeKey || '').toUpperCase())) {
        return;
      }
      const pairedFieldKey = pairings[(meta.attributeKey || '').toUpperCase()];
      if (pairedFieldKey) {
        const pairedField = metadata.find((m) => (m.attributeKey || '').toUpperCase() === pairedFieldKey);
        if (pairedField && !processed.has(pairedField.attributeKey)) {
          groups.push([meta, pairedField]);
          processed.add(meta.attributeKey);
          processed.add(pairedField.attributeKey);
          return;
        }
      }
      groups.push([meta]);
      processed.add(meta.attributeKey);
    });
    return groups;
  };

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
    const helperText = getSimulationFieldHelperText(key);

    if (dataType === 'BOOLEAN') {
      return (
        <div key={key}>
          <label className="flex items-center space-x-3 cursor-pointer h-full">
            <div className="relative inline-flex h-6 w-11 items-center rounded-full bg-gray-200 transition-colors focus-within:outline-none focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2"
                 style={{ backgroundColor: (value === true || value === 'true') ? '#3b82f6' : '#d1d5db' }}>
              <span className="inline-block h-4 w-4 transform rounded-full bg-white shadow-lg transition-transform"
                style={{ transform: (value === true || value === 'true') ? 'translateX(20px)' : 'translateX(2px)' }}
              />
              <input type="checkbox" checked={value === true || value === 'true'} onChange={(e) => setCalcInputs(prev => ({ ...prev, [key]: e.target.checked }))} className="absolute inset-0 h-full w-full cursor-pointer opacity-0" />
            </div>
            <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{label}</span>
          </label>
          {helperText && <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">{helperText}</p>}
        </div>
      );
    }

    if (isSystemCustomerSegmentKey(key)) {
      return (
        <div key={key}>
          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{label}</label>
          <PlexusSelect options={[{ value: 'RETAIL', label: 'RETAIL' }, { value: 'PREMIUM', label: 'PREMIUM' }, { value: 'CORPORATE', label: 'CORPORATE' }, { value: 'VIP', label: 'VIP' }]} value={{ value: String(value || 'RETAIL'), label: String(value || 'RETAIL') }} onChange={(opt) => setCalcInputs(prev => ({ ...prev, [key]: opt ? opt.value : 'RETAIL' }))} />
        </div>
      );
    }

    const inputType = dataType === 'DATE' ? 'date' : (dataType === 'DECIMAL' || dataType === 'INTEGER' || dataType === 'LONG') ? 'number' : 'text';
    return (
      <div key={key}>
        <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{label}</label>
        <input type={inputType} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-[11px] font-semibold bg-white focus:border-blue-500 transition h-[42px]" value={value} onChange={(e) => setCalcInputs(prev => ({ ...prev, [key]: e.target.value }))} />
        {helperText && <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">{helperText}</p>}
      </div>
    );
  };

  const handleCalculatePrice = async () => {
    if (!bundle) return;
    setCalculatedPrice(prev => ({ ...(prev || { totalPrice: 0, loading: true }), loading: true, error: undefined, detailedError: undefined }));

    try {
      const attributesFromMetadata: Record<string, any> = {};
      calcMetadata.forEach(meta => {
        const parsed = parseInputValue(meta.dataType, calcInputs[meta.attributeKey]);
        if (parsed !== undefined && !Number.isNaN(parsed)) {
          attributesFromMetadata[meta.attributeKey] = parsed;
        }
      });

      const metadataEffectiveDateKey = calcMetadata.find((meta) => isSystemDateKey(meta.attributeKey || ''))?.attributeKey;
      const effectiveDateKey = metadataEffectiveDateKey || keys.EFFECTIVE_DATE;
      const enrollmentDateKey = calcMetadata.find((meta) => (meta.attributeKey || '').toUpperCase() === 'ENROLLMENT_DATE')?.attributeKey || Object.keys(attributesFromMetadata).find((key) => key.toUpperCase() === 'ENROLLMENT_DATE');
      const enrollmentDateValue = enrollmentDateKey ? attributesFromMetadata[enrollmentDateKey] : undefined;

      if (!attributesFromMetadata[effectiveDateKey]) {
        attributesFromMetadata[effectiveDateKey] = new Date().toISOString().split('T')[0];
      }
      if (hasSystemPricingKey(keys.TRANSACTION_AMOUNT) && attributesFromMetadata[keys.TRANSACTION_AMOUNT] === undefined) {
        attributesFromMetadata[keys.TRANSACTION_AMOUNT] = 0;
      }

      const request = {
        productBundleId: bundle.id,
        enrollmentDate: typeof enrollmentDateValue === 'string' && enrollmentDateValue.trim() ? enrollmentDateValue : new Date().toISOString().split('T')[0],
        products: bundle.products.map(p => ({
          productId: p.productId,
          transactionAmount: productAmounts[`${p.productId}`] || 1000
        })),
        customAttributes: attributesFromMetadata
      };

      const result = await PricingService.calculateBundlePrice(request);
      setCalculatedPrice({
        totalPrice: result.totalPrice,
        loading: false,
        productsBreakdown: result.productsBreakdown
      });
    } catch (err: any) {
      const apiError = err.response?.data;
      const isBusinessRuleViolation = apiError?.code === 'BUSINESS_RULE_VIOLATION';
      setCalculatedPrice({
        totalPrice: 0,
        loading: false,
        error: 'Calc Failed',
        detailedError: isBusinessRuleViolation ? apiError.message : undefined
      });
      setToast({ message: err.response?.data?.message || 'Price calculation failed.', type: 'error' });
    }
  };

  const renderBreakdown = () => {
    if (!calculatedPrice || !bundle || calculatedPrice.loading || calculatedPrice.error) return null;

    return (
      <div className="space-y-6">
        {calculatedPrice.productsBreakdown?.map((pb, idx) => {
          const product = bundle.products[idx];
          return (
            <div key={idx} className="bg-gray-50/50 rounded-xl border border-gray-100 p-4">
              <div className="flex items-center justify-between mb-3 border-b border-gray-100 pb-2">
                <div className="flex items-center gap-2">
                  <Package className="w-3.5 h-3.5 text-blue-500" />
                  <span className="text-[11px] font-black uppercase text-gray-700">{product.productName}</span>
                </div>
                <span className="text-xs font-black text-blue-600">{PricingService.formatCurrency(pb.finalChargeablePrice)}</span>
              </div>
              <div className="space-y-2">
                {pb.componentBreakdown.map((item, cIdx) => {
                  const isDiscount = item.valueType?.startsWith('DISCOUNT');
                  return (
                    <div key={cIdx} className={`flex items-center justify-between p-2 rounded-lg text-[10px] ${isDiscount ? 'bg-green-50/50 text-green-700' : 'bg-blue-50/50 text-blue-700'}`}>
                      <span className="font-bold">{formatComponentLabelWithProRata(toTitleFromCode(item.componentCode), item)}</span>
                      <span className="font-black">{isDiscount ? '-' : ''}{PricingService.formatCurrency(Math.abs(item.calculatedAmount))}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
        <div className="pt-4 border-t border-gray-200 flex items-center justify-between">
          <span className="text-[10px] font-black uppercase tracking-widest text-gray-700">Bundle Total Price</span>
          <div className="flex items-center gap-2">
            <span className="text-sm font-black text-blue-600">{PricingService.formatCurrency(calculatedPrice.totalPrice)}</span>
            <button
              onClick={handleCalculatePrice}
              className="p-1 text-blue-500 hover:bg-blue-100 rounded transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    );
  };

  if (loading) return <div className="admin-page flex justify-center items-center h-64"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>;
  if (!bundle) return null;

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Library}
        title={bundle.name}
        description={bundle.description || 'Manage product aggregate configuration and pricing simulation.'}
        actions={
          <div className="flex items-center gap-2">
            {bundle.status === 'DRAFT' && (
              <HasPermission permission="catalog:bundle:activate">
                <button onClick={() => handleStatusAction('activate')} className="admin-success-btn">
                  <CheckCircle2 className="h-4 w-4" /> Activate
                </button>
              </HasPermission>
            )}
            {bundle.status === 'ACTIVE' && (
              <HasPermission permission="catalog:bundle:create">
                <button onClick={handleVersion} className="admin-success-btn">
                  <History className="h-4 w-4" /> New Version
                </button>
              </HasPermission>
            )}
            {bundle.status === 'DRAFT' && (
              <HasPermission permission="catalog:bundle:update">
                <button onClick={() => navigate(`/bundles/edit/${id}`)} className="admin-primary-btn">
                  <Edit className="h-4 w-4" /> Edit Bundle
                </button>
              </HasPermission>
            )}
            <HasPermission permission="catalog:bundle:delete">
              <button
                onClick={() => bundle.status === 'ACTIVE' ? setArchiveModalOpen(true) : setDeleteModalOpen(true)}
                className="admin-danger-btn"
              >
                {bundle.status === 'ACTIVE' ? <Archive className="h-4 w-4" /> : <Trash2 className="h-4 w-4" />}
                {bundle.status === 'ACTIVE' ? 'Archive' : 'Delete'}
              </button>
            </HasPermission>
          </div>
        }
      />

      <div className="grid grid-cols-1 gap-6">
        {/* Header Info */}
        <div className="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm flex flex-wrap items-center gap-8">
            <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Bundle Code</label>
                <div className="text-sm font-mono font-bold text-blue-600">{bundle.code}</div>
            </div>
            <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Target Segments</label>
                <div className="flex flex-wrap gap-1">
                    {bundle.targetCustomerSegments.split(',').map((seg, idx) => (
                      <span key={idx} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-[9px] font-bold rounded uppercase tracking-tighter">
                        {seg.trim()}
                      </span>
                    ))}
                </div>
            </div>
            <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Status</label>
                <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${bundle.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                    {bundle.status} v{bundle.version || 1}
                </span>
            </div>
            {bundle.status === 'ACTIVE' && bundle.activationDate && (
                <div>
                    <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Activated On</label>
                    <div className="text-sm font-bold text-gray-800">{new Date(bundle.activationDate).toLocaleDateString()}</div>
                </div>
            )}
        </div>

        {/* Description */}
        <div className="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm border-l-4 border-l-blue-500">
            <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight mb-2">Description</h4>
            <p className="text-xs text-gray-500 italic leading-relaxed">{bundle.description || 'No description available for this bundle.'}</p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* Member Products */}
            <div>
                <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-blue-50/50 p-1.5 rounded-lg w-fit">
                    <Package className="w-3 h-3 mr-1.5 text-blue-500" /> Constituent Products
                </h4>
                <div className="space-y-3">
                    {bundle.products?.map((p, idx) => (
                        <div key={idx} className="flex justify-between items-center text-xs p-3 bg-white rounded-xl border border-gray-100 shadow-sm hover:border-blue-100 transition">
                          <div className="flex flex-col">
                            <span className="font-bold text-gray-700">{p.productName}</span>
                            <span className="text-[9px] font-mono text-gray-400">{p.productCode}</span>
                          </div>
                          <div className="flex gap-2">
                            {p.mainAccount && <span className="bg-blue-600 text-white px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-tighter">Main Account</span>}
                            {p.mandatory && <span className="bg-amber-100 text-amber-700 px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-tighter">Mandatory</span>}
                          </div>
                        </div>
                    ))}
                    {(!bundle.products || bundle.products.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No products linked to this bundle.</p>}
                </div>
            </div>

            {/* Pricing Rules */}
            <div>
                <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-purple-50/50 p-1.5 rounded-lg w-fit">
                    <Tag className="w-3 h-3 mr-1.5 text-purple-500" /> Bundle Pricing adjustments
                </h4>
                <div className="space-y-4">
                    {bundle.pricing?.map((p, idx) => (
                        <div key={idx} className="flex justify-between items-center text-xs p-3 bg-white rounded-xl border border-gray-100 shadow-sm hover:border-purple-100 transition">
                          <div className="flex flex-col">
                            <span className="font-bold text-gray-700">{p.pricingComponentName}</span>
                            <span className="text-[9px] font-mono text-gray-400">{p.targetComponentCode || 'Global Adjustment'}</span>
                          </div>
                          <span className={`font-bold px-2 py-0.5 rounded-lg text-[10px] border ${p.useRulesEngine ? 'bg-amber-100 text-amber-700 border-amber-200' : 'bg-purple-100 text-purple-700 border-purple-200'}`}>
                            {p.useRulesEngine ? 'DYNAMIC RULES' : `${PricingService.formatCurrency(p.fixedValue || 0)} (${p.fixedValueType?.replace(/_/g, ' ')})`}
                          </span>
                        </div>
                    ))}
                    {(!bundle.pricing || bundle.pricing.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No bundle-level pricing adjustments defined.</p>}
                </div>
            </div>
        </div>

        {/* Simulation Tool */}
        <div className="bg-blue-50/50 rounded-2xl p-6 border border-blue-100">
            <div role="button" tabIndex={0} className="flex items-center justify-between cursor-pointer" onClick={() => setSimulationOpen(!simulationOpen)}>
                <div className="flex items-center space-x-3">
                    <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-200"><Zap className="w-4 h-4 text-white" /></div>
                    <div>
                        <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight">Bundle Price Simulation</h4>
                        <p className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Test full bundle aggregate with cross-product rules</p>
                    </div>
                </div>
                {simulationOpen ? <ChevronUp className="w-5 h-5 text-gray-400" /> : <ChevronDown className="w-5 h-5 text-gray-400" />}
            </div>

            {simulationOpen && (
                <div className="mt-6 pt-6 border-t border-blue-100 grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
                    <div className="bg-white rounded-xl border border-gray-200 p-4">
                        <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Calculation Parameters</h5>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Product Transaction Amounts</label>
                                <div className="space-y-2">
                                    {bundle.products.map(p => (
                                        <div key={p.productId} className="flex items-center gap-3">
                                            <span className="text-[10px] font-bold text-gray-600 flex-1 truncate">{p.productName}</span>
                                            <input
                                                type="number"
                                                className="w-24 border border-gray-200 rounded-lg px-3 py-2 text-[11px] font-semibold h-[42px] bg-white focus:border-blue-500 transition"
                                                value={productAmounts[`${p.productId}`] || 1000}
                                                onChange={(e) => setProductAmounts(prev => ({ ...prev, [`${p.productId}`]: Number(e.target.value) }))}
                                            />
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="space-y-3">
                                {calcMetadata.length > 0 ? getFieldGroups(calcMetadata).map((group, groupIdx) => (
                                    <div key={groupIdx} className={`grid gap-3 ${group.length === 2 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                                        {group.map((meta) => renderDynamicField(meta))}
                                    </div>
                                )) : <div className="text-[10px] font-bold text-amber-600 bg-amber-50 p-2 rounded-lg border border-amber-100">Pricing metadata not available.</div>}
                            </div>
                        </div>
                        <button
                            onClick={handleCalculatePrice}
                            disabled={calculatedPrice?.loading}
                            className="w-full mt-4 bg-blue-600 text-white rounded-lg h-[42px] font-black uppercase tracking-widest text-[10px] flex items-center justify-center space-x-2 shadow-lg shadow-blue-100"
                        >
                            {calculatedPrice?.loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                            <span>Calculate Bundle Total</span>
                        </button>
                    </div>

                    <div className="bg-white rounded-xl border border-gray-200 p-4 min-h-full flex flex-col">
                        <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Bundle Breakdown</h5>
                        {calculatedPrice?.loading && (
                            <div className="flex-1 flex items-center justify-center"><div className="flex items-center gap-2 text-blue-600 text-sm font-bold"><Loader2 className="w-4 h-4 animate-spin" /> Calculating...</div></div>
                        )}
                        {calculatedPrice?.error && (
                            <div className="flex-1 flex items-center justify-center p-6 text-center">
                              <div>
                                <div className="text-[11px] font-black text-red-600 uppercase tracking-tight flex items-center justify-center gap-1.5">
                                  <span>⚠️ Simulation Failed: {calculatedPrice.error}</span>
                                </div>
                                {calculatedPrice.detailedError && (
                                  <div className="mt-2 text-[10px] font-bold text-gray-500 max-w-[300px] mx-auto leading-relaxed">
                                    {calculatedPrice.detailedError}
                                  </div>
                                )}
                              </div>
                            </div>
                        )}
                        {!calculatedPrice && (
                            <div className="flex-1 flex items-center justify-center text-center text-sm text-gray-500 py-10">Run calculation to see bundle details.</div>
                        )}
                        {calculatedPrice && !calculatedPrice.loading && !calculatedPrice.error && (
                            renderBreakdown()
                        )}
                    </div>
                </div>
            )}
        </div>
      </div>

      <ConfirmationModal
        isOpen={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        onConfirm={handleDelete}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete bundle "${bundle.name}"?`}
        confirmText="Delete Bundle"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={archiveModalOpen}
        onClose={() => setArchiveModalOpen(false)}
        onConfirm={handleArchive}
        title="Confirm Archival"
        message="Are you sure you want to archive this bundle? It will be removed from circulation."
        confirmText="Archive Bundle"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductBundleDetailPage;
