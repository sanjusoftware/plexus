import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Package, ShieldCheck, Tag, Info, CheckCircle2, ChevronDown, ChevronUp, Play, RefreshCw, X } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { AdminDataTableActionButton } from '../../components/AdminDataTable';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { PricingService } from '../../services/PricingService';

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
}

const ProductManagementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { setToast } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [calculatedPrices, setCalculatedPrices] = useState<Record<number, { price: number; loading: boolean; error?: string }>>({});
  const [calculatingId, setCalculatingId] = useState<number | null>(null);
  const [calcParams, setCalcParams] = useState({ amount: 1000, segment: 'RETAIL' });

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
      window.history.replaceState({}, document.title);
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
    if (!window.confirm('Permanently delete this product?')) return;
    try {
      await axios.delete(`/api/v1/products/${id}`);
      setToast({ message: 'Product deleted successfully.', type: 'success' });
      await fetchInitialData(signal);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
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
              <div className="col-span-3">Product Name & Code</div>
              <div className="col-span-2">Classification</div>
              <div className="col-span-2">Category</div>
              <div className="col-span-1 text-center">Status</div>
              <div className="col-span-2 text-center">Live Pricing</div>
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
                    <div className="text-[10px] lg:hidden font-bold text-gray-400 uppercase tracking-widest mb-1">Classification</div>
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
                  <div className="lg:col-span-2">
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

                  {/* Live Pricing */}
                  <div className="lg:col-span-2 flex flex-col items-center justify-center border-l border-gray-100/50" onClick={(e) => e.stopPropagation()}>
                     {calculatingId === prod.id ? (
                        <div className="flex flex-col space-y-1.5 p-2 bg-blue-50 rounded-xl border border-blue-100 animate-in fade-in zoom-in-95 min-w-[140px]">
                           <div className="flex items-center justify-between">
                             <span className="text-[8px] font-black text-blue-400 uppercase tracking-tighter">Simulation</span>
                             <button onClick={() => setCalculatingId(null)} className="text-gray-400 hover:text-red-500 transition"><X className="w-3 h-3"/></button>
                           </div>
                           <div className="flex items-center space-x-1">
                              <div className="relative flex-1">
                                 <span className="absolute left-1.5 top-1/2 -translate-y-1/2 text-[9px] font-bold text-gray-400">$</span>
                                 <input
                                    type="number"
                                    className="w-full p-1 pl-3.5 text-[10px] font-bold border-0 rounded bg-white shadow-sm ring-1 ring-blue-100 focus:ring-blue-400 transition"
                                    value={calcParams.amount}
                                    onChange={(e) => setCalcParams({...calcParams, amount: parseFloat(e.target.value) || 0})}
                                    placeholder="Amount"
                                 />
                              </div>
                              <select
                                 className="text-[9px] font-black border-0 rounded p-1 bg-white shadow-sm ring-1 ring-blue-100 focus:ring-blue-400 transition appearance-none pr-3"
                                 value={calcParams.segment}
                                 onChange={(e) => setCalcParams({...calcParams, segment: e.target.value})}
                              >
                                 <option value="RETAIL">RETAIL</option>
                                 <option value="PREMIUM">PREM</option>
                                 <option value="CORPORATE">CORP</option>
                              </select>
                           </div>
                           <div className="flex items-center justify-between pt-1 border-t border-blue-100/50">
                              <button
                                 onClick={() => handleCalculatePrice(prod)}
                                 disabled={calculatedPrices[prod.id]?.loading}
                                 className="bg-blue-600 hover:bg-blue-700 text-white p-1 rounded transition shadow-md shadow-blue-200"
                                 title="Run Calculation"
                              >
                                 {calculatedPrices[prod.id]?.loading ? <Loader2 className="w-2.5 h-2.5 animate-spin" /> : <Play className="w-2.5 h-2.5" />}
                              </button>
                              <div className="flex-1 text-right pr-1">
                                {calculatedPrices[prod.id] && !calculatedPrices[prod.id].loading && (
                                   <div className="flex flex-col leading-none">
                                      <span className="text-[10px] font-black text-blue-700">{PricingService.formatCurrency(calculatedPrices[prod.id].price)}</span>
                                      {calculatedPrices[prod.id].error && <span className="text-[8px] font-bold text-red-500 uppercase">{calculatedPrices[prod.id].error}</span>}
                                   </div>
                                )}
                              </div>
                              {calculatedPrices[prod.id] && !calculatedPrices[prod.id].loading && (
                                <button
                                  onClick={() => handleCalculatePrice(prod)}
                                  className="p-1 text-blue-500 hover:bg-blue-100 rounded transition"
                                  title="Refresh"
                                >
                                  <RefreshCw className="w-2.5 h-2.5" />
                                </button>
                              )}
                           </div>
                        </div>
                     ) : (
                       <button
                        onClick={() => { setCalculatingId(prod.id); }}
                        className="flex items-center space-x-1.5 px-3 py-1.5 bg-gray-50 text-gray-500 hover:bg-blue-600 hover:text-white rounded-lg border border-gray-200 hover:border-blue-500 transition-all duration-300 text-[10px] font-bold uppercase tracking-widest shadow-sm"
                       >
                         <Play className="w-3 h-3" />
                         <span>Calculate Price</span>
                       </button>
                     )}
                  </div>

                  {/* Actions */}
                  <div className="lg:col-span-2 flex justify-end space-x-1" onClick={(e) => e.stopPropagation()}>
                    {prod.status === 'DRAFT' && (
                      <HasPermission action="POST" path="/api/v1/products/*/activate">
                        <AdminDataTableActionButton
                          onClick={() => handleStatusAction(prod.id, 'activate')}
                          tone="success"
                          size="compact"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          Activate
                        </AdminDataTableActionButton>
                      </HasPermission>
                    )}
                    <HasPermission action="PATCH" path="/api/v1/products/*">
                      <AdminDataTableActionButton
                        onClick={() => navigate(`/products/edit/${prod.id}`)}
                        tone="primary"
                        size="compact"
                        title="Modify Product"
                      >
                        <Edit2 className="h-3.5 w-3.5" />
                        Edit
                      </AdminDataTableActionButton>
                    </HasPermission>
                    <HasPermission action="DELETE" path="/api/v1/products/*">
                      <AdminDataTableActionButton
                        onClick={() => prod.status === 'ACTIVE' ? handleStatusAction(prod.id, 'archive') : handleDelete(prod.id)}
                        tone="danger"
                        size="compact"
                        title={prod.status === 'ACTIVE' ? "Archive Product" : "Delete Product"}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        {prod.status === 'ACTIVE' ? "Archive" : "Delete"}
                      </AdminDataTableActionButton>
                    </HasPermission>
                  </div>
                </div>
                {expandedIds.has(prod.id) && (
                  <div className="p-6 grid grid-cols-1 lg:grid-cols-2 gap-8 bg-white">
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

    </AdminPage>
  );
};

export default ProductManagementPage;
