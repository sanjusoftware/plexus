import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Package, ShieldCheck, Tag, Info } from 'lucide-react';
import { AdminInfoBanner, AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';

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
  productTypeCode: string;
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

  const signal = useAbortSignal();

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
        <div className="grid grid-cols-1 gap-6">
          {products.length === 0 ? (
            <div className="py-12 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-xs">No active products found. Get started by clicking "Create New Product".</div>
          ) : (
            products.map((prod) => (
              <div key={prod.id} className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md transition duration-300 group">
                <div className="p-4 flex flex-col md:flex-row justify-between items-start md:items-center border-b border-gray-50 bg-gray-50/20 group-hover:bg-blue-50/10 transition duration-300">
                  <div className="flex items-center space-x-4">
                    <div className="p-2.5 bg-white rounded-xl shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-300">
                      <Package className="w-5 h-5 text-blue-600 group-hover:text-white transition duration-300" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-gray-900 leading-tight group-hover:text-blue-900 transition">{prod.name}</h3>
                      <div className="flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-widest text-gray-400 mt-1">
                        <span className="bg-white px-1.5 py-0.5 rounded-lg border shadow-sm font-mono text-blue-600">{prod.code}</span>
                        <span className="bg-white px-1.5 py-0.5 rounded-lg border shadow-sm">{prod.productTypeCode}</span>
                        <span className="bg-white px-1.5 py-0.5 rounded-lg border shadow-sm">{prod.category}</span>
                        <span className={`px-1.5 py-0.5 rounded-lg border shadow-sm ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>{prod.status}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex space-x-2 mt-4 md:mt-0">
                    {prod.status === 'DRAFT' && (
                      <HasPermission action="POST" path="/api/v1/products/*/activate">
                        <button onClick={() => handleStatusAction(prod.id, 'activate')} className="px-3 py-1.5 bg-green-600 text-white rounded-lg text-[10px] font-bold uppercase tracking-widest hover:bg-green-700 transition shadow-md shadow-green-100">Activate</button>
                      </HasPermission>
                    )}
                    <HasPermission action="PATCH" path="/api/v1/products/*">
                      <button onClick={() => navigate(`/products/edit/${prod.id}`)} className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition shadow-sm border border-blue-50" title="Modify Product"><Edit2 className="w-4 h-4" /></button>
                    </HasPermission>
                    <HasPermission action="DELETE" path="/api/v1/products/*">
                      <button
                        onClick={() => prod.status === 'ACTIVE' ? handleStatusAction(prod.id, 'archive') : handleDelete(prod.id)}
                        className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition shadow-sm border border-red-50"
                        title={prod.status === 'ACTIVE' ? "Archive Product" : "Delete Product"}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </HasPermission>
                  </div>
                </div>
                <div className="p-6 grid grid-cols-1 lg:grid-cols-2 gap-8 bg-white">
                  <div>
                    <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center bg-blue-50/50 p-1.5 rounded-lg w-fit">
                      <ShieldCheck className="w-3 h-3 mr-1.5 text-blue-500" /> Linked Feature components
                    </h4>
                    <div className="space-y-2">
                      {prod.features?.map((f, idx) => (
                        <div key={idx} className="flex justify-between items-center text-xs p-3 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-blue-100 hover:bg-white transition shadow-sm">
                          <span className="font-bold text-gray-700">{f.featureName || f.featureComponentCode}</span>
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
                          <span className="font-bold text-gray-700">{p.pricingComponentName || p.pricingComponentCode}</span>
                          <span className={`font-bold px-2 py-0.5 rounded-lg text-[10px] ${p.useRulesEngine ? 'bg-amber-100 text-amber-700' : 'bg-purple-100 text-purple-700'}`}>
                            {p.useRulesEngine ? 'DYNAMIC RULES' : `${p.fixedValue} (${p.fixedValueType})`}
                          </span>
                        </div>
                      ))}
                      {(!prod.pricing || prod.pricing.length === 0) && <p className="text-[10px] text-gray-400 italic bg-gray-50 p-4 rounded-xl border border-dashed text-center">No pricing rules bound to this product.</p>}
                    </div>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

    </AdminPage>
  );
};

export default ProductManagementPage;
