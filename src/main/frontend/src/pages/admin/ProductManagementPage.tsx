import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Package, Tag, Info, ChevronDown, ChevronUp, Play, RefreshCw, Zap, Puzzle, Layers } from 'lucide-react';
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
import { useSystemPricingKeys } from '../../hooks/useSystemPricingKeys';
import { PriceComponentDetail, PricingMetadata, PricingService, PricingComponent, FeatureComponent, TierCondition } from '../../services/PricingService';
import PlexusSelect from '../../components/PlexusSelect';
import { formatComponentLabelWithProRata, formatPercentageBaseHint, getSimulationFieldHelperText } from './ProductManagementPage.utils';

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
  effectiveDate?: string;
  expiryDate?: string | null;
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
  detailedError?: string;
  breakdown?: PriceComponentDetail[];
}

const toTitleFromCode = (value: string) =>
  (value || '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

const ProductManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, setToast } = useAuth();
  const {
    keys,
    hasSystemPricingKey,
    isHiddenSystemKey,
    isSystemAmountKey,
    isSystemDateKey,
    isSystemCustomerSegmentKey,
  } = useSystemPricingKeys();
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [simulationOpenIds, setSimulationOpenIds] = useState<Set<number>>(new Set());
  const [calculatedPrices, setCalculatedPrices] = useState<Record<number, SimulationResult>>({});
  const [calcMetadata, setCalcMetadata] = useState<PricingMetadata[]>([]);
  const [calcInputs, setCalcInputs] = useState<Record<string, any>>({
    [keys.TRANSACTION_AMOUNT]: 1000,
    [keys.CUSTOMER_SEGMENT]: 'RETAIL',
    [keys.EFFECTIVE_DATE]: new Date().toISOString().split('T')[0],
  });

  const [componentDetails, setComponentDetails] = useState<Record<string, PricingComponent>>({});
  const [featureDetails, setFeatureDetails] = useState<Record<string, FeatureComponent>>({});
  const [detailsLoading, setDetailsLoading] = useState<Set<string>>(new Set());

  // Modal states
  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; productId?: number }>({ isOpen: false });
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean; productId?: number; productName?: string }>({ isOpen: false });
  const consumedSuccessKeyRef = useRef<string | null>(null);

  const signal = useAbortSignal();

  const fetchComponentDetails = useCallback(async (code: string) => {
    if (componentDetails[code] || detailsLoading.has(code)) return;
    setDetailsLoading(prev => new Set(prev).add(code));
    try {
      const details = await PricingService.getPricingComponentByCode(code);
      setComponentDetails(prev => ({ ...prev, [code]: details }));
    } catch (err) {
      console.error(`Failed to fetch details for component ${code}`, err);
    } finally {
      setDetailsLoading(prev => {
        const next = new Set(prev);
        next.delete(code);
        return next;
      });
    }
  }, [componentDetails, detailsLoading]);

  const fetchFeatureDetails = useCallback(async (code: string) => {
    if (featureDetails[code] || detailsLoading.has(code)) return;
    setDetailsLoading(prev => new Set(prev).add(code));
    try {
      const details = await PricingService.getFeatureComponentByCode(code);
      setFeatureDetails(prev => ({ ...prev, [code]: details }));
    } catch (err) {
      console.error(`Failed to fetch details for feature ${code}`, err);
    } finally {
      setDetailsLoading(prev => {
        const next = new Set(prev);
        next.delete(code);
        return next;
      });
    }
  }, [featureDetails, detailsLoading]);

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
      const product = products.find(p => p.id === id);
      if (product) {
        product.pricing?.forEach(p => fetchComponentDetails(p.pricingComponentCode));
        product.features?.forEach(f => fetchFeatureDetails(f.featureComponentCode));
      }
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
      setToast({ message: 'Failed to fetch products. Please check your role permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast, isHiddenSystemKey, isSystemAmountKey, isSystemCustomerSegmentKey]);

  // Define field groups and order for better UX
  const getFieldGroups = (metadata: PricingMetadata[]) => {
    const groups: (PricingMetadata[])[] = [];
    const processed = new Set<string>();

    // Known field pairing rules
    const pairings: Record<string, string> = {
      EFFECTIVE_DATE: 'ENROLLMENT_DATE',
      ENROLLMENT_DATE: 'EFFECTIVE_DATE',
      TRANSACTION_AMOUNT: 'LOYALTY_SCORE',
      LOYALTY_SCORE: 'TRANSACTION_AMOUNT',
      IS_SALARY_ACCOUNT: 'LOYALTY_SCORE',
    };

    // Fields to exclude (not needed for calculation)
    const excludedFields = ['GROSS_TOTAL_AMOUNT'];

    // Organize fields
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

      // Single field
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

  const renderDynamicField = (prod: Product, meta: PricingMetadata) => {
    const key = meta.attributeKey;
    const value = calcInputs[key] ?? '';
    const dataType = (meta.dataType || '').toUpperCase();
    const label = (meta.displayName || key).toUpperCase();
    const helperText = getSimulationFieldHelperText(key);

    if (dataType === 'BOOLEAN') {
      return (
        <div key={key}>
          <label className="flex items-center space-x-3 cursor-pointer h-full">
            {/* Toggle Switch */}
            <div className="relative inline-flex h-6 w-11 items-center rounded-full bg-gray-200 transition-colors focus-within:outline-none focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2"
                 style={{ backgroundColor: (value === true || value === 'true') ? '#3b82f6' : '#d1d5db' }}>
              <span
                className="inline-block h-4 w-4 transform rounded-full bg-white shadow-lg transition-transform"
                style={{ transform: (value === true || value === 'true') ? 'translateX(20px)' : 'translateX(2px)' }}
              />
              <input
                type="checkbox"
                checked={value === true || value === 'true'}
                onChange={(e) => setCalcInputs(prev => ({ ...prev, [key]: e.target.checked }))}
                className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
              />
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
        {helperText && (
          <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">{helperText}</p>
        )}
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
      PricingService.clearComponentCache(); // Clear cache when something changes
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

      const metadataEffectiveDateKey = calcMetadata.find((meta) => isSystemDateKey(meta.attributeKey || ''))?.attributeKey;
      const effectiveDateKey = metadataEffectiveDateKey || keys.EFFECTIVE_DATE;
      const enrollmentDateKey = calcMetadata.find((meta) => (meta.attributeKey || '').toUpperCase() === 'ENROLLMENT_DATE')?.attributeKey
        || Object.keys(attributesFromMetadata).find((key) => key.toUpperCase() === 'ENROLLMENT_DATE');
      const enrollmentDateValue = enrollmentDateKey
        ? attributesFromMetadata[enrollmentDateKey]
        : undefined;

      if (!attributesFromMetadata[effectiveDateKey]) {
        attributesFromMetadata[effectiveDateKey] = new Date().toISOString().split('T')[0];
      }


      if (hasSystemPricingKey(keys.TRANSACTION_AMOUNT) && attributesFromMetadata[keys.TRANSACTION_AMOUNT] === undefined) {
        attributesFromMetadata[keys.TRANSACTION_AMOUNT] = 0;
      }

      const request = {
        productId: prod.id,
        enrollmentDate: typeof enrollmentDateValue === 'string' && enrollmentDateValue.trim()
          ? enrollmentDateValue
          : new Date().toISOString().split('T')[0],
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
      const apiError = err.response?.data;
      const isBusinessRuleViolation = apiError?.code === 'BUSINESS_RULE_VIOLATION';

      setCalculatedPrices(prev => ({
        ...prev,
        [prod.id]: {
          price: 0,
          loading: false,
          error: 'Calc Failed',
          detailedError: isBusinessRuleViolation ? apiError.message : undefined
        }
      }));
      setToast({ message: err.response?.data?.message || 'Price calculation failed.', type: 'error' });
    }
  };

  const formatCondition = (condition: TierCondition, currency: string) => {
    const attributeLabel = toTitleFromCode(condition.attributeName);
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

    let formattedValue = value;
    const lowerAttr = condition.attributeName.toLowerCase();
    if (lowerAttr.includes('amount') || lowerAttr.includes('balance') || lowerAttr.includes('income')) {
        formattedValue = `${getCurrencySymbol(currency)}${formatNumber(value)}`;
    } else if (!isNaN(parseFloat(value))) {
        formattedValue = formatNumber(value);
    }

    return (
      <span>
        <span className="text-blue-500">{attributeLabel}</span> {friendlyOp} <span className="font-black text-blue-900">{formattedValue}</span>
      </span>
    );
  };

  const renderBreakdown = (prod: Product, onRefresh?: () => void) => {
    const simulation = calculatedPrices[prod.id];
    if (!simulation || simulation.loading || simulation.error) return null;

    const breakdown = simulation.breakdown || [];
    const charges = breakdown.filter(item => !item.valueType?.startsWith('DISCOUNT'));
    const discounts = breakdown.filter(item => item.valueType?.startsWith('DISCOUNT'));
    const totalCharges = charges.reduce((sum, item) => sum + Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0)), 0);
    const totalDiscounts = discounts.reduce((sum, item) => sum + Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0)), 0);

    const nameByCode: Record<string, string> = {};
    (prod.pricing || []).forEach((link) => {
      if (link.pricingComponentCode) {
        nameByCode[link.pricingComponentCode] = link.pricingComponentName || toTitleFromCode(link.pricingComponentCode);
      }
    });

    const getDisplayName = (componentCode: string) => {
      return nameByCode[componentCode] || toTitleFromCode(componentCode);
    };

    const getPercentageHint = (item: PriceComponentDetail, isDiscount: boolean) => {
      return formatPercentageBaseHint(item, {
        isDiscount,
        formatCurrency: (amount) => PricingService.formatCurrency(amount),
        targetComponentLabel: item.targetComponentCode ? getDisplayName(item.targetComponentCode) : null,
      });
    };

    const getReceiptMetaLine = (item: PriceComponentDetail, isDiscount: boolean) => {
      const segments = [
        item.valueType,
        item.matchedTierCode ? `Tier: ${item.matchedTierCode}` : null,
        getPercentageHint(item, isDiscount),
      ].filter(Boolean);

      return segments.join(' | ');
    };

    if (breakdown.length === 0) {
      return (
        <div className="mt-4 text-[10px] font-bold text-gray-500 bg-white p-3 rounded-lg border border-gray-200">
          No component-level breakdown returned by pricing engine for this run.
        </div>
      );
    }

    return (
      <div className="space-y-2">
        {charges.map((item, idx) => {
          const amount = Math.abs(Number(item.calculatedAmount ?? item.rawValue ?? 0));
          return (
            <div key={`charge-${item.componentCode}-${idx}`} className="flex items-center justify-between p-2.5 rounded-lg border border-blue-100 bg-blue-50/30">
              <div className="min-w-0 pr-3">
                <div className="text-[11px] font-bold text-gray-800 leading-tight break-words">
                  {formatComponentLabelWithProRata(getDisplayName(item.componentCode), item)} <span className="text-[9px] font-mono font-normal text-gray-500">({item.componentCode})</span>
                </div>
                <div className="text-[9px] text-gray-500 uppercase tracking-wider">
                  {getReceiptMetaLine(item, false)}
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
                <div className="text-[11px] font-bold text-gray-800 leading-tight break-words">
                  {formatComponentLabelWithProRata(getDisplayName(item.componentCode), item)} <span className="text-[9px] font-mono font-normal text-gray-500">({item.componentCode})</span>
                </div>
                <div className="text-[9px] text-gray-500 uppercase tracking-wider">
                  {getReceiptMetaLine(item, true)}
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

        <div className="pt-3 border-t border-gray-100 space-y-1">
          <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-gray-500">
            <span>Total Charges</span>
            <span className="text-blue-600">{PricingService.formatCurrency(totalCharges)}</span>
          </div>
          <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-gray-500">
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
        title="Product Catalog"
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
        <span className="italic">This system uses a decoupled model. Products are aggregates that link to reusable <strong>Product Features</strong> and <strong>Pricing Components</strong>. You can create the product, its features, and its pricing links in one atomic request below.</span>
      </AdminInfoBanner>

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable aria-label="Product catalog table" containerClassName="overflow-x-auto" className="min-w-[1200px]">
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
                      <div className="flex flex-col items-center">
                        <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                          {prod.status}
                        </span>
                        {prod.status === 'ACTIVE' && prod.activationDate && (
                            <span className="text-[9px] text-gray-400 font-bold mt-1">
                                {new Date(prod.activationDate).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: '2-digit' })}
                            </span>
                        )}
                      </div>
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
                      {(prod.status === 'DRAFT') && (
                        <HasPermission action="PATCH" path="/api/v1/products/*">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); navigate(`/products/edit/${prod.id}`); }}
                            tone="primary"
                            size="compact"
                            title="Modify Product"
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
                        <div className="p-6">
                            {/* Product Tagline & Description */}
                            <div className="mb-6 border-l-4 border-blue-500 pl-4">
                                <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight">{prod.tagline || 'No tagline defined'}</h4>
                                <p className="text-xs text-gray-500 mt-1 italic">{prod.fullDescription || 'No description available for this product.'}</p>
                            </div>

                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                    <div>
                      <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-blue-50/50 p-1.5 rounded-lg w-fit">
                          <Puzzle className="w-3 h-3 mr-1.5 text-blue-500" /> Product Features
                      </h4>
                      <div className="space-y-3">
                        {prod.features?.map((f, idx) => {
                          const details = featureDetails[f.featureComponentCode];
                          return (
                          <div key={idx} className="p-4 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-blue-100 hover:bg-white transition shadow-sm">
                            <div className="flex justify-between items-start mb-2">
                                <div className="flex flex-col">
                                    <div className="flex items-center gap-2">
                                        <span className="font-bold text-gray-700 text-sm">{f.featureName || details?.name || 'Unnamed Feature'}</span>
                                        {details && (
                                            <>
                                                <span className={`px-1.5 py-0.5 rounded text-[8px] font-black uppercase tracking-tighter ${details.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                                                    {details.status}
                                                </span>
                                                <span className="text-[8px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter">
                                                    v{details.version}
                                                </span>
                                            </>
                                        )}
                                    </div>
                                    <span className="text-[9px] font-mono text-gray-400">{f.featureComponentCode}</span>
                                </div>
                                <span className="font-bold text-blue-600 bg-blue-50 px-2.5 py-1 rounded-lg text-[11px] shadow-sm border border-blue-100">{f.featureValue}</span>
                            </div>
                            {details?.description && (
                                <p className="text-[10px] text-gray-500 italic mt-1 leading-relaxed">{details.description}</p>
                            )}
                          </div>
                        );})}
                        {(!prod.features || prod.features.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No product features bound to this product.</p>}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-purple-50/50 p-1.5 rounded-lg w-fit">
                        <Tag className="w-3 h-3 mr-1.5 text-purple-500" /> Pricing rules
                      </h4>
                      <div className="space-y-4">
                        {prod.pricing?.map((p, idx) => {
                          const details = componentDetails[p.pricingComponentCode];
                          return (
                          <div key={idx} className="p-4 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-purple-100 hover:bg-white transition shadow-sm">
                            <div className="flex justify-between items-start mb-3">
                                <div className="flex flex-col">
                                    <div className="flex items-center gap-2">
                                        <span className="font-bold text-gray-700 text-sm">{p.pricingComponentName || details?.name || 'Unnamed Component'}</span>
                                        {details && (
                                            <>
                                                <span className={`px-1.5 py-0.5 rounded text-[8px] font-black uppercase tracking-tighter ${details.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                                                    {details.status}
                                                </span>
                                                <span className="text-[8px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter">
                                                    v{details.version}
                                                </span>
                                            </>
                                        )}
                                    </div>
                                    <span className="text-[9px] font-mono text-gray-400">{p.pricingComponentCode}</span>
                                </div>
                                <span className={`font-bold px-2.5 py-1 rounded-lg text-[10px] shadow-sm border ${p.useRulesEngine ? 'bg-amber-100 text-amber-700 border-amber-200' : 'bg-purple-100 text-purple-700 border-purple-200'}`}>
                                  {p.useRulesEngine ? 'DYNAMIC RULES' : `${PricingService.formatCurrency(p.fixedValue || 0)} (${p.fixedValueType?.replace(/_/g, ' ')})`}
                                </span>
                            </div>

                            {details?.description && (
                                <p className="text-[10px] text-gray-500 italic mb-4 leading-relaxed border-l-2 border-gray-200 pl-2">{details.description}</p>
                            )}

                            {details?.pricingTiers && details.pricingTiers.length > 0 && (
                                <div className="space-y-2 mt-4">
                                    <div className="text-[9px] font-black text-gray-400 uppercase tracking-widest mb-2 flex items-center">
                                        <Layers className="w-3 h-3 mr-1" /> Tier Configuration
                                    </div>
                                    {[...details.pricingTiers].sort((a, b) => (b.priority || 0) - (a.priority || 0)).map((tier, tidx) => (
                                        <div key={tidx} className="bg-white p-3 rounded-lg border border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
                                            <div className="flex-1">
                                                <div className="flex items-center space-x-2 mb-1">
                                                    <span className="text-[8px] font-black bg-purple-50 text-purple-600 px-1.5 py-0.5 rounded uppercase tracking-tighter">Tier #{tidx + 1}</span>
                                                    <div className="text-[9px] font-bold text-gray-400 uppercase tracking-widest">{tier.code}</div>
                                                </div>
                                                <h5 className="font-bold text-gray-800 text-[11px]">{tier.name}</h5>

                                                <div className="flex flex-wrap items-center gap-1.5 mt-1.5">
                                                    {tier.conditions?.map((c, cidx) => (
                                                        <React.Fragment key={cidx}>
                                                            <div className="bg-blue-50/50 text-blue-700 px-2 py-0.5 rounded text-[9px] font-medium border border-blue-100">
                                                                {formatCondition(c, user?.currencyCode || 'USD')}
                                                            </div>
                                                            {cidx < tier.conditions.length - 1 && (
                                                                <span className="text-[8px] font-black text-gray-300 uppercase">{c.connector}</span>
                                                            )}
                                                        </React.Fragment>
                                                    ))}
                                                    {(!tier.conditions || tier.conditions.length === 0) && (
                                                        <div className="text-[9px] text-gray-400 italic bg-gray-50 px-2 py-0.5 rounded border border-gray-100">Catch-all Tier</div>
                                                    )}
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-4 pl-4 border-l border-gray-50">
                                                <div className="text-right">
                                                    <div className="text-[11px] font-black text-blue-600">
                                                        {tier.priceValues?.[0]?.valueType?.includes('PERCENTAGE') ? '' : (user?.currencyCode || 'USD')}
                                                        {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(tier.priceValues?.[0]?.rawValue || 0)}
                                                        {tier.priceValues?.[0]?.valueType?.includes('PERCENTAGE') ? '%' : ''}
                                                    </div>
                                                    <div className="text-[8px] font-black text-gray-400 uppercase tracking-tighter">{tier.priceValues?.[0]?.valueType?.replace(/_/g, ' ')}</div>
                                                </div>
                                                <div className="text-center bg-gray-50 px-2 py-1 rounded">
                                                    <div className="text-[8px] font-black text-gray-400 uppercase tracking-tighter">Prio</div>
                                                    <div className="text-[10px] font-bold text-gray-600">{tier.priority ?? '—'}</div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                          </div>
                        );})}
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
                         <div className="mt-4 pt-4 border-t border-blue-100 grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
                           <div className="bg-white rounded-xl border border-gray-200 p-4">
                              <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Calculation Inputs</h5>
                              <div className="space-y-3">
                                {calcMetadata.length > 0
                                  ? getFieldGroups(calcMetadata).map((group, groupIdx) => (
                                      <div key={groupIdx} className={`grid gap-3 ${group.length === 2 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                                        {group.map((meta) => renderDynamicField(prod, meta))}
                                      </div>
                                    ))
                                  : (
                                    <div className="col-span-2 text-[10px] font-bold text-amber-600 bg-amber-50 p-2 rounded-lg border border-amber-100">
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

                              <div className="bg-white rounded-xl border border-gray-200 p-4 min-h-full flex flex-col">
                                <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Calculation Receipt</h5>
                                {calculatedPrices[prod.id]?.loading && (
                                  <div className="flex-1 flex items-center justify-center">
                                   <div className="flex items-center gap-2 text-blue-600 text-sm font-bold">
                                     <Loader2 className="w-4 h-4 animate-spin" />
                                     Calculating...
                                   </div>
                                 </div>
                               )}
                               {calculatedPrices[prod.id]?.error && (
                                 <div className="flex-1 flex items-center justify-center p-6">
                                   <div className="text-center">
                                     <div className="text-[11px] font-black text-red-600 uppercase tracking-tight flex items-center justify-center gap-1.5">
                                       <span>⚠️ Simulation Failed: {calculatedPrices[prod.id].error}</span>
                                     </div>
                                     {calculatedPrices[prod.id]?.detailedError && (
                                       <div className="mt-2 text-[10px] font-bold text-gray-500 max-w-[300px] mx-auto leading-relaxed">
                                         {calculatedPrices[prod.id].detailedError}
                                       </div>
                                     )}
                                   </div>
                                 </div>
                               )}
                               {!calculatedPrices[prod.id] && (
                                 <div className="flex-1 flex items-center justify-center text-center">
                                   <div className="text-sm text-gray-500">
                                     Run calculation to see a receipt-style component breakdown.
                                   </div>
                                 </div>
                               )}
                               {calculatedPrices[prod.id] && !calculatedPrices[prod.id].loading && !calculatedPrices[prod.id].error && (
                                 renderBreakdown(prod, () => handleCalculatePrice(prod))
                              )}
                             </div>
                        </div>
                      )}
                    </div>
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
