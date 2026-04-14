import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Library, Tag, ChevronDown, ChevronUp, Play, RefreshCw, Zap, Package } from 'lucide-react';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
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
import { PricingMetadata, PricingService, ProductPricingCalculationResult } from '../../services/PricingService';

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
  productsBreakdown?: ProductPricingCalculationResult[];
}

const toTitleFromCode = (value: string) =>
  (value || '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

const ProductBundlesPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const {
    keys,
    isHiddenSystemKey,
    isSystemCustomerSegmentKey,
  } = useSystemPricingKeys();

  const [bundles, setBundles] = useState<ProductBundle[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [simulationOpenIds, setSimulationOpenIds] = useState<Set<number>>(new Set());
  const [calculatedPrices, setCalculatedPrices] = useState<Record<number, BundleSimulationResult>>({});
  const [calcMetadata, setCalcMetadata] = useState<PricingMetadata[]>([]);
  const [calcInputs, setCalcInputs] = useState<Record<string, any>>({
    [keys.CUSTOMER_SEGMENT]: 'RETAIL',
    [keys.EFFECTIVE_DATE]: new Date().toISOString().split('T')[0],
  });
  const [productAmounts, setProductAmounts] = useState<Record<string, number>>({});

  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; bundleId?: number }>({ isOpen: false });
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean; bundleId?: number; bundleName?: string }>({ isOpen: false });
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
      const [b, metadata] = await Promise.all([
        axios.get('/api/v1/bundles', { signal: abortSignal }).catch(err => {
          // Handle 405 or other errors gracefully - bundles endpoint may not be available
          if (err.response?.status === 405 || err.response?.status === 404) {
            return { data: { content: [] } };
          }
          throw err;
        }),
        PricingService.getPricingMetadata(abortSignal).catch(() => []),
      ]);
      setBundles(b.data.content || []);

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
              next[m.attributeKey] = 0;
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
      setToast({ message: 'Failed to fetch bundles. Please check your role permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast, isHiddenSystemKey, isSystemCustomerSegmentKey]);

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
      await axios.post(`/api/v1/bundles/${id}/${action}`);
      setToast({ message: `Bundle ${action}d successfully.`, type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || `Failed to ${action} bundle.`, type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  const handleArchive = async (id: number) => {
    try {
      // Assuming bundle delete acts as archive for ACTIVE bundles in backend as per ProductManagementPage pattern
      await axios.delete(`/api/v1/bundles/${id}`);
      setToast({ message: 'Bundle archived successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Archival failed.', type: 'error' });
    }
  };

  const handleVersion = async (id: number) => {
    try {
      await axios.post(`/api/v1/bundles/${id}/create-new-version`, {});
      setToast({ message: 'New version created in DRAFT status.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Versioning failed.', type: 'error' });
    }
  };

  const handleCalculatePrice = async (bundle: ProductBundle) => {
    setCalculatedPrices(prev => ({
      ...prev,
      [bundle.id]: {
        loading: true,
        totalPrice: 0,
        error: undefined,
      }
    }));

    try {
      const attributesFromMetadata: Record<string, any> = {};
      calcMetadata.forEach(meta => {
        const value = calcInputs[meta.attributeKey];
        if (value !== undefined && value !== '') {
          attributesFromMetadata[meta.attributeKey] = value;
        }
      });

      const request = {
        productBundleId: bundle.id,
        enrollmentDate: new Date().toISOString().split('T')[0],
        products: bundle.products.map(p => ({
          productId: p.productId,
          transactionAmount: productAmounts[`${bundle.id}-${p.productId}`] || 1000
        })),
        customAttributes: attributesFromMetadata
      };

      const result = await PricingService.calculateBundlePrice(request);
      setCalculatedPrices(prev => ({
        ...prev,
        [bundle.id]: {
          totalPrice: result.totalPrice,
          loading: false,
          productsBreakdown: result.productsBreakdown
        }
      }));
    } catch (err: any) {
      setCalculatedPrices(prev => ({
        ...prev,
        [bundle.id]: { totalPrice: 0, loading: false, error: 'Calculation Failed' }
      }));
      setToast({ message: err.response?.data?.message || 'Price calculation failed.', type: 'error' });
    }
  };

  const renderBreakdown = (bundle: ProductBundle) => {
    const simulation = calculatedPrices[bundle.id];
    if (!simulation || simulation.loading || simulation.error) return null;

    return (
      <div className="space-y-6">
        {simulation.productsBreakdown?.map((pb, idx) => {
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
                      <span className="font-bold">{toTitleFromCode(item.componentCode)}</span>
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
            <span className="text-sm font-black text-blue-600">{PricingService.formatCurrency(simulation.totalPrice)}</span>
            <button
              onClick={() => handleCalculatePrice(bundle)}
              className="p-1 text-blue-500 hover:bg-blue-100 rounded transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Library}
        title="Product Bundles"
        description="Manage product aggregates, mandatory inclusions, and bundle-level pricing."
        actions={
          <HasPermission permission="catalog:bundle:create">
            <button
              onClick={() => navigate('/bundles/create')}
              className="admin-primary-btn"
            >
              <Plus className="h-4 w-4" /> New Bundle
            </button>
          </HasPermission>
        }
      />

      {loading ? (
        <div className="admin-card flex justify-center p-10"><Loader2 className="h-8 w-8 animate-spin text-blue-600" /></div>
      ) : (
        <AdminDataTable containerClassName="overflow-x-auto" className="min-w-[1200px]">
          <thead>
            <tr>
              <th>Bundle Details</th>
              <th>Target Segments</th>
              <th className="text-center">Status</th>
              <th>Updated At</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {bundles.length === 0 ? (
              <AdminDataTableEmptyRow colSpan={5}>No product bundles found. Click "New Bundle" to create one.</AdminDataTableEmptyRow>
            ) : (
              bundles.map((bundle) => (
                <React.Fragment key={bundle.id}>
                  <AdminDataTableRow
                    onClick={() => toggleExpand(bundle.id)}
                    interactive
                    className="group"
                  >
                    <td className="max-w-[300px]">
                      <div className="flex items-center space-x-3">
                        <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300 flex-shrink-0">
                          <Library className="w-4 h-4 text-blue-600 group-hover:text-white transition duration-300" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 min-w-0">
                            <h3 className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900 transition leading-tight flex-1" title={bundle.name}>{bundle.name}</h3>
                            <span className="text-[9px] font-black bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded uppercase tracking-tighter whitespace-nowrap">
                              v{bundle.version ?? 1}
                            </span>
                            {expandedIds.has(bundle.id) ? (
                              <ChevronUp className="h-4 w-4 text-gray-400 flex-shrink-0" />
                            ) : (
                              <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
                            )}
                          </div>
                          <div className="text-[9px] font-mono font-bold text-blue-600 uppercase tracking-tight truncate">{bundle.code}</div>
                        </div>
                      </div>
                    </td>

                    <td>
                      <div className="flex flex-wrap gap-1">
                        {bundle.targetCustomerSegments.split(',').map((seg, idx) => (
                          <span key={idx} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-[9px] font-bold rounded uppercase tracking-tighter">
                            {seg.trim()}
                          </span>
                        ))}
                      </div>
                    </td>

                    <td className="text-center">
                      <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${bundle.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                        {bundle.status}
                      </span>
                    </td>

                    <AuditTimestampCell value={bundle.updatedAt || bundle.createdAt} />

                    <AdminDataTableActionCell onClick={(e) => e.stopPropagation()}>
                      {bundle.status === 'DRAFT' && (
                        <HasPermission permission="catalog:bundle:activate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleStatusAction(bundle.id, 'activate'); }}
                            tone="success"
                            size="compact"
                          >
                            <AdminDataTableActionContent action="activate" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {bundle.status === 'ACTIVE' && (
                        <HasPermission permission="catalog:bundle:create">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleVersion(bundle.id); }}
                            tone="success"
                            size="compact"
                          >
                            <AdminDataTableActionContent action="version" />
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      <HasPermission permission="catalog:bundle:update">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); bundle.status === 'DRAFT' && navigate(`/bundles/edit/${bundle.id}`); }}
                          tone="primary"
                          size="compact"
                          disabled={bundle.status !== 'DRAFT'}
                        >
                          <AdminDataTableActionContent action="edit" />
                        </AdminDataTableActionButton>
                      </HasPermission>
                      <HasPermission permission="catalog:bundle:delete">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); bundle.status === 'ACTIVE' ? setArchiveModal({ isOpen: true, bundleId: bundle.id }) : setDeleteModal({ isOpen: true, bundleId: bundle.id, bundleName: bundle.name }); }}
                          tone="danger"
                          size="compact"
                        >
                          <AdminDataTableActionContent action={bundle.status === 'ACTIVE' ? 'archive' : 'delete'} />
                        </AdminDataTableActionButton>
                      </HasPermission>
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>

                  {expandedIds.has(bundle.id) && (
                    <tr className="bg-white">
                      <td colSpan={5} className="p-0 border-b border-gray-100">
                        <div className="p-6 grid grid-cols-1 lg:grid-cols-2 gap-8">
                          <div>
                            <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-blue-50/50 p-1.5 rounded-lg w-fit">
                              <Package className="w-3 h-3 mr-1.5 text-blue-500" /> Constituent Products
                            </h4>
                            <div className="space-y-2">
                              {bundle.products?.map((p, idx) => (
                                <div key={idx} className="flex justify-between items-center text-xs p-3 bg-gray-50/50 rounded-xl border border-gray-100 shadow-sm">
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
                            </div>
                          </div>

                          <div>
                            <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-purple-50/50 p-1.5 rounded-lg w-fit">
                              <Tag className="w-3 h-3 mr-1.5 text-purple-500" /> Bundle Pricing rules
                            </h4>
                            <div className="space-y-2">
                              {bundle.pricing?.map((p, idx) => (
                                <div key={idx} className="flex justify-between items-center text-xs p-3 bg-gray-50/50 rounded-xl border border-gray-100 shadow-sm">
                                  <div className="flex flex-col">
                                    <span className="font-bold text-gray-700">{p.pricingComponentName}</span>
                                    <span className="text-[9px] font-mono text-gray-400">{p.targetComponentCode || 'Global Adjustment'}</span>
                                  </div>
                                  <span className={`font-bold px-2 py-0.5 rounded-lg text-[10px] ${p.useRulesEngine ? 'bg-amber-100 text-amber-700' : 'bg-purple-100 text-purple-700'}`}>
                                    {p.useRulesEngine ? 'DYNAMIC RULES' : `${p.fixedValue} (${p.fixedValueType})`}
                                  </span>
                                </div>
                              ))}
                              {(!bundle.pricing || bundle.pricing.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No bundle-level pricing adjustments.</p>}
                            </div>
                          </div>

                          {/* Bundle Pricing Simulation */}
                          <div className="lg:col-span-2 bg-blue-50/50 rounded-2xl p-4 border border-blue-100 mt-2">
                            <div className="flex items-center justify-between cursor-pointer" onClick={() => toggleSimulation(bundle.id)}>
                              <div className="flex items-center space-x-3">
                                <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-200">
                                  <Zap className="w-4 h-4 text-white" />
                                </div>
                                <div>
                                  <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight">Bundle Price Simulation</h4>
                                  <p className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Test full bundle aggregate with cross-product rules</p>
                                </div>
                              </div>
                              {simulationOpenIds.has(bundle.id) ? <ChevronUp className="w-5 h-5 text-gray-400" /> : <ChevronDown className="w-5 h-5 text-gray-400" />}
                            </div>

                            {simulationOpenIds.has(bundle.id) && (
                              <div className="mt-4 pt-4 border-t border-blue-100 grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
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
                                              className="w-24 border border-gray-200 rounded-lg px-2 py-1 text-[11px] font-semibold h-[32px]"
                                              value={productAmounts[`${bundle.id}-${p.productId}`] || 1000}
                                              onChange={(e) => setProductAmounts(prev => ({ ...prev, [`${bundle.id}-${p.productId}`]: Number(e.target.value) }))}
                                            />
                                          </div>
                                        ))}
                                      </div>
                                    </div>

                                    <div className="grid grid-cols-2 gap-3">
                                      {calcMetadata.map(meta => (
                                        <div key={meta.attributeKey}>
                                          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">{meta.displayName || meta.attributeKey}</label>
                                          <input
                                            type={meta.dataType === 'DATE' ? 'date' : 'text'}
                                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-[11px] font-semibold h-[36px]"
                                            value={calcInputs[meta.attributeKey] || ''}
                                            onChange={(e) => setCalcInputs(prev => ({ ...prev, [meta.attributeKey]: e.target.value }))}
                                          />
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                  <button
                                    onClick={() => handleCalculatePrice(bundle)}
                                    disabled={calculatedPrices[bundle.id]?.loading}
                                    className="w-full mt-4 bg-blue-600 text-white rounded-lg h-[42px] font-black uppercase tracking-widest text-[10px] flex items-center justify-center space-x-2 shadow-lg shadow-blue-100"
                                  >
                                    {calculatedPrices[bundle.id]?.loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                                    <span>Calculate Bundle Total</span>
                                  </button>
                                </div>

                                <div className="bg-white rounded-xl border border-gray-200 p-4 min-h-full">
                                  <h5 className="text-[10px] font-black uppercase tracking-widest text-gray-700 mb-3">Bundle Breakdown</h5>
                                  {calculatedPrices[bundle.id]?.loading && (
                                    <div className="flex items-center justify-center py-10"><Loader2 className="w-6 h-6 animate-spin text-blue-600" /></div>
                                  )}
                                  {calculatedPrices[bundle.id]?.error && (
                                    <div className="text-[10px] font-bold text-red-500 bg-red-50 p-3 rounded-lg border border-red-100">{calculatedPrices[bundle.id]?.error}</div>
                                  )}
                                  {!calculatedPrices[bundle.id] && (
                                    <div className="text-center text-sm text-gray-400 py-10">Run calculation to see bundle details.</div>
                                  )}
                                  {calculatedPrices[bundle.id] && !calculatedPrices[bundle.id].loading && !calculatedPrices[bundle.id].error && (
                                    renderBreakdown(bundle)
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
        onConfirm={() => deleteModal.bundleId && handleDelete(deleteModal.bundleId)}
        title="Confirm Deletion"
        message={`Are you sure you want to permanently delete bundle "${deleteModal.bundleName}"?`}
        confirmText="Delete Bundle"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={archiveModal.isOpen}
        onClose={() => setArchiveModal({ isOpen: false })}
        onConfirm={() => archiveModal.bundleId && handleArchive(archiveModal.bundleId)}
        title="Confirm Archival"
        message="Are you sure you want to archive this bundle? It will be removed from circulation."
        confirmText="Archive Bundle"
        variant="danger"
      />
    </AdminPage>
  );
};

export default ProductBundlesPage;
