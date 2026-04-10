import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Loader2, Package, ShieldCheck, Tag, Info, ChevronDown, ChevronUp, Play, RefreshCw, Zap } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { AdminDataTableActionButton, AdminDataTableActionContent } from '../../components/AdminDataTable';
import ConfirmationModal from '../../components/ConfirmationModal';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { PricingService } from '../../services/PricingService';
import PlexusSelect from '../../components/PlexusSelect';
import { formatAuditTimestamp } from '../../utils/auditTimestamp';

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

const ProductManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [calculatedPrices, setCalculatedPrices] = useState<Record<number, { price: number; loading: boolean; error?: string }>>({});
  const [calcParams, setCalcParams] = useState({ amount: 1000, segment: 'RETAIL' });

  // Modal states
  const [archiveModal, setArchiveModal] = useState<{ isOpen: boolean; productId?: number }>({ isOpen: false });

  const signal = useAbortSignal();

  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedIds);
    if (newExpanded.has(id)) {
      newExpanded.delete(id);
    } else {
      newExpanded.add(id);
    }
    setExpandedIds(newExpanded);
  };

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const p = await axios.get('/api/v1/products', { signal: abortSignal });
      setProducts(p.data.content || []);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch products. Please check your role permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    fetchInitialData(signal);
    if (location.state?.success) {
      setToast({ message: location.state.success, type: 'success' });
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location, setToast, fetchInitialData, signal, navigate]);

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
    if (!window.confirm('Are you sure you want to permanently delete this draft product?')) return;
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
    setCalculatedPrices(prev => ({ ...prev, [prod.id]: { price: 0, loading: true } }));
    try {
      const request = {
        productId: prod.id,
        transactionAmount: calcParams.amount,
        customerSegment: calcParams.segment,
        effectiveDate: new Date().toISOString().split('T')[0],
        enrollmentDate: new Date().toISOString().split('T')[0],
      };

      const result = await PricingService.calculateProductPrice(request);
      setCalculatedPrices(prev => ({
        ...prev,
        [prod.id]: { price: result.finalChargeablePrice, loading: false }
      }));
    } catch (err: any) {
      setCalculatedPrices(prev => ({
        ...prev,
        [prod.id]: { price: 0, loading: false, error: 'Calc Failed' }
      }));
      setToast({ message: err.response?.data?.message || 'Price calculation failed.', type: 'error' });
    }
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
              <Plus className="h-4 w-4" /> Create New Product
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
        <div className="flex flex-col space-y-4">
          {products.length > 0 && (
            <div className="hidden lg:grid grid-cols-12 gap-4 px-6 py-3 bg-gray-100/50 rounded-t-xl border-x border-t border-gray-200 text-[10px] font-bold text-gray-400 uppercase tracking-widest">
              <div className="col-span-3">Product Details</div>
              <div className="col-span-2">Product Type</div>
              <div className="col-span-1">Category</div>
              <div className="col-span-1 text-center">Status</div>
              <div className="col-span-1">Created At</div>
              <div className="col-span-1">Updated At</div>
              <div className="col-span-1 text-center">Expand</div>
              <div className="col-span-2 text-right">Actions</div>
            </div>
          )}

          {products.length === 0 ? (
            <div className="py-12 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-xs">No active products found. Get started by clicking "Create New Product".</div>
          ) : (
            products.map((prod) => (
              <div key={prod.id} className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden hover:shadow-md transition duration-300 group">
                <div
                  className="p-4 grid grid-cols-1 lg:grid-cols-12 gap-4 items-center border-b border-gray-50 bg-gray-50/20 group-hover:bg-blue-50/5 transition duration-300 cursor-pointer"
                  onClick={() => toggleExpand(prod.id)}
                >
                  {/* Product Info */}
                  <div className="lg:col-span-3 flex items-center space-x-3">
                    <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300 flex-shrink-0">
                      <Package className="w-4 h-4 text-blue-600 group-hover:text-white transition duration-300" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center space-x-1.5">
                        <h3 className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900 transition">{prod.name}</h3>
                        {expandedIds.has(prod.id) ? (
                          <ChevronUp className="h-4 w-4 text-gray-400 flex-shrink-0" />
                        ) : (
                          <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
                        )}
                      </div>
                      <div className="text-[9px] font-mono font-bold text-blue-600 uppercase tracking-tight truncate">{prod.code}</div>
                    </div>
                  </div>

                  {/* Classification */}
                  <div className="lg:col-span-2">
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mb-1">Product Type</div>
                    {prod.productType ? (
                      <div>
                        <div className="text-[11px] font-bold text-gray-700">{prod.productType.name}</div>
                        <div className="text-[9px] font-mono text-gray-400">{prod.productType.code}</div>
                      </div>
                    ) : (
                      <div className="text-[10px] text-gray-300 italic">None</div>
                    )}
                  </div>

                  {/* Category */}
                  <div className="lg:col-span-1">
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mb-1">Category</div>
                    <div className="text-[11px] font-bold text-gray-600 uppercase">Category: {prod.category}</div>
                  </div>

                  {/* Status */}
                  <div className="lg:col-span-1 flex lg:justify-center">
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mr-2">Status</div>
                    <span className={`px-2 py-0.5 rounded-lg border text-[10px] font-bold uppercase tracking-tight ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>
                      {prod.status}
                    </span>
                  </div>

                  {/* Created At */}
                  <div className="lg:col-span-1">
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mb-1">Created At</div>
                    <div className="text-[11px] font-bold text-gray-600" title={prod.createdAt || '--'}>{formatAuditTimestamp(prod.createdAt)}</div>
                  </div>

                  {/* Updated At */}
                  <div className="lg:col-span-1">
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mb-1">Updated At</div>
                    <div className="text-[11px] font-bold text-gray-600" title={prod.updatedAt || '--'}>{formatAuditTimestamp(prod.updatedAt)}</div>
                  </div>

                  {/* Live Pricing (Placeholder for spacing) */}
                  <div className="lg:col-span-1 flex flex-col items-center justify-center border-l border-gray-100/50" onClick={(e) => e.stopPropagation()}>
                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest flex items-center">
                       <Zap className="w-3.5 h-3.5 mr-1 text-amber-500" />
                       Click to Expand
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="lg:col-span-2 flex justify-end space-x-1" onClick={(e) => e.stopPropagation()}>
                    {prod.status === 'DRAFT' && (
                      <HasPermission action="POST" path="/api/v1/products/*/activate">
                        <AdminDataTableActionButton
                          onClick={() => handleStatusAction(prod.id, 'activate')}
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
                          onClick={() => handleVersion(prod.id)}
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
                          onClick={() => prod.status === 'DRAFT' && navigate(`/products/edit/${prod.id}`)}
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
                        onClick={() => prod.status === 'ACTIVE' ? setArchiveModal({ isOpen: true, productId: prod.id }) : handleDelete(prod.id)}
                        tone="danger"
                        size="compact"
                        title={prod.status === 'ACTIVE' ? "Archive Product" : "Delete Product"}
                      >
                        <AdminDataTableActionContent action={prod.status === 'ACTIVE' ? 'archive' : 'delete'} />
                      </AdminDataTableActionButton>
                    </HasPermission>
                  </div>
                </div>
                {expandedIds.has(prod.id) && (
                  <div className="p-6 grid grid-cols-1 lg:grid-cols-2 gap-8 bg-white">
                    {/* Inline Pricing Simulation Tool */}
                    <div className="lg:col-span-2 bg-blue-50/50 rounded-2xl p-6 border border-blue-100 mb-2">
                       <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center space-x-3">
                             <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-200">
                                <Zap className="w-4 h-4 text-white" />
                             </div>
                             <div>
                                <h4 className="text-sm font-black text-gray-900 uppercase tracking-tight">Real-Time Pricing Simulation</h4>
                                <p className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Test this product aggregate with custom parameters</p>
                             </div>
                          </div>
                          {calculatedPrices[prod.id] && !calculatedPrices[prod.id].loading && (
                             <div className="flex items-center space-x-3 bg-white px-4 py-2 rounded-xl border border-blue-200 shadow-sm animate-in fade-in slide-in-from-right-4">
                                <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Estimated Price:</span>
                                <span className="text-lg font-black text-blue-600 leading-none">{PricingService.formatCurrency(calculatedPrices[prod.id].price)}</span>
                                <button
                                   onClick={() => handleCalculatePrice(prod)}
                                   className="p-1.5 text-blue-500 hover:bg-blue-50 rounded-lg transition"
                                   title="Refresh Simulation"
                                >
                                   <RefreshCw className="w-3.5 h-3.5" />
                                </button>
                             </div>
                          )}
                       </div>

                       <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
                          <div>
                             <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">Transaction Amount ($)</label>
                             <input
                                type="number"
                                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm font-bold bg-white focus:border-blue-500 transition h-[42px]"
                                value={calcParams.amount}
                                onChange={(e) => setCalcParams({...calcParams, amount: parseFloat(e.target.value) || 0})}
                             />
                          </div>
                          <div>
                             <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1.5">Customer Segment</label>
                             <PlexusSelect
                                options={[
                                   { value: 'RETAIL', label: 'RETAIL' },
                                   { value: 'PREMIUM', label: 'PREMIUM' },
                                   { value: 'CORPORATE', label: 'CORPORATE' },
                                   { value: 'VIP', label: 'VIP' }
                                ]}
                                value={{ value: calcParams.segment, label: calcParams.segment }}
                                onChange={(opt) => setCalcParams({...calcParams, segment: opt ? opt.value : 'RETAIL'})}
                             />
                          </div>
                          <button
                             onClick={() => handleCalculatePrice(prod)}
                             disabled={calculatedPrices[prod.id]?.loading}
                             className="bg-blue-600 text-white rounded-lg h-[42px] font-black uppercase tracking-widest text-[10px] hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center space-x-2 disabled:opacity-50"
                          >
                             {calculatedPrices[prod.id]?.loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                             <span>Run Calculation</span>
                          </button>
                       </div>
                       {calculatedPrices[prod.id]?.error && (
                          <div className="mt-3 text-[10px] font-bold text-red-500 bg-red-50 p-2 rounded-lg border border-red-100 animate-in shake-1">
                             ⚠️ Simulation Failed: {calculatedPrices[prod.id].error}
                          </div>
                       )}
                    </div>

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
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

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
