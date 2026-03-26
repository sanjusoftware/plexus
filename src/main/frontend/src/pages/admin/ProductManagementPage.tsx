import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Save, X, Package, ShieldCheck, Tag, Layers, ChevronRight, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import StyledSelect from '../../components/StyledSelect';

interface FeatureComponent {
  id: number;
  code: string;
  name: string;
  dataType: string;
}

interface ProductType {
  id: number;
  code: string;
  name: string;
}

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
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const p = await axios.get('/api/v1/products');
      setProducts(p.data.content || []);
    } catch (err: any) {
      setError('Failed to fetch products. Please check your role permissions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
    if (location.state?.success) {
      setSuccess(location.state.success);
      window.history.replaceState({}, document.title);
    }
  }, [location]);

  const handleStatusAction = async (id: number, action: string) => {
    try {
      await axios.post(`/api/v1/products/${id}/${action}`);
      setSuccess(`Product ${action}d successfully.`);
      fetchInitialData();
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to ${action} product.`);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Permanently delete this product?')) return;
    try {
      await axios.delete(`/api/v1/products/${id}`);
      setSuccess('Product deleted.');
      fetchInitialData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Deletion failed.');
    }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-20">
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-6">
          <div className="p-4 bg-blue-50 rounded-2xl shadow-inner shadow-blue-100"><Package className="w-10 h-10 text-blue-600" /></div>
          <div>
            <h1 className="text-3xl font-black text-gray-900 tracking-tight">Product Management</h1>
            <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">Configure Bank Offerings, Feature Sets & Pricing Bindings</p>
          </div>
        </div>
        <button
          onClick={() => navigate('/products/create')}
          className="bg-blue-600 text-white px-8 py-3.5 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-xs"
        >
          <Plus className="w-5 h-5 mr-3" /> Create New Product
        </button>
      </div>

      <div className="bg-indigo-50 border-l-4 border-indigo-400 p-8 rounded-r-3xl shadow-sm flex items-start space-x-6">
        <div className="p-3 bg-indigo-100 rounded-xl"><Info className="w-6 h-6 text-indigo-700" /></div>
        <div>
           <p className="text-sm text-indigo-900 font-black leading-relaxed uppercase tracking-wide mb-2">FAT DTO Architecture</p>
           <p className="text-sm text-indigo-800 font-medium leading-relaxed italic max-w-4xl">
              This system uses a decoupled model. Products are aggregates that link to reusable <strong>Feature Components</strong> and <strong>Pricing Components</strong>.
              You can create the product, its features, and its pricing links in one atomic request below.
           </p>
        </div>
      </div>

      {error && <div className="bg-red-50 border-l-4 border-red-500 p-6 rounded-r-3xl text-red-700 text-sm font-bold flex items-center shadow-md animate-in slide-in-from-top-2"><AlertCircle className="w-5 h-5 mr-4" />{error}</div>}
      {success && <div className="bg-green-50 border-l-4 border-green-500 p-6 rounded-r-3xl text-green-700 text-sm font-bold flex items-center shadow-md animate-in slide-in-from-top-2"><CheckCircle2 className="w-5 h-5 mr-4" />{success}</div>}

      {loading ? (
        <div className="flex justify-center p-32 bg-white rounded-[3rem] border border-gray-100 shadow-sm"><Loader2 className="w-16 h-16 animate-spin text-blue-600" /></div>
      ) : (
        <div className="grid grid-cols-1 gap-10">
          {products.length === 0 ? (
            <div className="py-24 text-center text-gray-400 bg-white border-4 border-dashed rounded-[3rem] font-black uppercase tracking-widest text-xs">No active products found. Get started by clicking "Create New Product".</div>
          ) : (
            products.map((prod) => (
              <div key={prod.id} className="bg-white rounded-[2.5rem] shadow-sm border border-gray-100 overflow-hidden hover:shadow-2xl transition duration-500 group">
                <div className="p-8 flex flex-col md:flex-row justify-between items-start md:items-center border-b border-gray-50 bg-gray-50/20 group-hover:bg-blue-50/10 transition duration-500">
                  <div className="flex items-center space-x-6">
                    <div className="p-4 bg-white rounded-2xl shadow-sm border border-gray-100 group-hover:bg-blue-600 group-hover:border-blue-500 transition duration-500">
                      <Package className="w-8 h-8 text-blue-600 group-hover:text-white transition duration-500" />
                    </div>
                    <div>
                      <h3 className="text-2xl font-black text-gray-900 leading-tight group-hover:text-blue-900 transition">{prod.name}</h3>
                      <div className="flex flex-wrap gap-3 text-[10px] font-black uppercase tracking-widest text-gray-400 mt-2">
                        <span className="bg-white px-2 py-1 rounded-lg border shadow-sm font-mono text-blue-600">{prod.code}</span>
                        <span className="bg-white px-2 py-1 rounded-lg border shadow-sm">{prod.productTypeCode}</span>
                        <span className="bg-white px-2 py-1 rounded-lg border shadow-sm">{prod.category}</span>
                        <span className={`px-2 py-1 rounded-lg border shadow-sm ${prod.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-amber-50 text-amber-700 border-amber-100'}`}>{prod.status}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex space-x-3 mt-6 md:mt-0">
                    {prod.status === 'DRAFT' && (
                      <button onClick={() => handleStatusAction(prod.id, 'activate')} className="px-5 py-2.5 bg-green-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-green-700 transition shadow-lg shadow-green-100">Activate</button>
                    )}
                    {prod.status === 'ACTIVE' && (
                      <button onClick={() => handleStatusAction(prod.id, 'archive')} className="px-5 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-gray-200 transition">Archive</button>
                    )}
                    <button onClick={() => navigate(`/products/edit/${prod.id}`)} className="p-3 text-blue-600 hover:bg-blue-50 rounded-xl transition shadow-sm border border-blue-50" title="Modify Product"><Edit2 className="w-5 h-5" /></button>
                    <button onClick={() => handleDelete(prod.id)} className="p-3 text-red-600 hover:bg-red-50 rounded-xl transition shadow-sm border border-red-50" title="Delete Product"><Trash2 className="w-5 h-5" /></button>
                  </div>
                </div>
                <div className="p-10 grid grid-cols-1 lg:grid-cols-2 gap-12 bg-white">
                  <div>
                    <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-6 flex items-center bg-blue-50/50 p-2 rounded-lg w-fit">
                      <ShieldCheck className="w-3.5 h-3.5 mr-2 text-blue-500" /> Linked Feature components
                    </h4>
                    <div className="space-y-3">
                      {prod.features?.map((f, idx) => (
                        <div key={idx} className="flex justify-between items-center text-sm p-4 bg-gray-50/50 rounded-2xl border border-gray-100 hover:border-blue-100 hover:bg-white transition shadow-sm">
                          <span className="font-bold text-gray-700">{f.featureName || f.featureComponentCode}</span>
                          <span className="font-black text-blue-600 bg-blue-50 px-3 py-1 rounded-xl text-xs">{f.featureValue}</span>
                        </div>
                      ))}
                      {(!prod.features || prod.features.length === 0) && <p className="text-xs text-gray-400 italic bg-gray-50 p-6 rounded-2xl border border-dashed text-center">No feature components bound to this product.</p>}
                    </div>
                  </div>
                  <div>
                    <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-6 flex items-center bg-purple-50/50 p-2 rounded-lg w-fit">
                      <Tag className="w-3.5 h-3.5 mr-2 text-purple-500" /> Pricing rule bindings
                    </h4>
                    <div className="space-y-3">
                      {prod.pricing?.map((p, idx) => (
                        <div key={idx} className="flex justify-between items-center text-sm p-4 bg-gray-50/50 rounded-2xl border border-gray-100 hover:border-purple-100 hover:bg-white transition shadow-sm">
                          <span className="font-bold text-gray-700">{p.pricingComponentName || p.pricingComponentCode}</span>
                          <span className={`font-black px-3 py-1 rounded-xl text-xs ${p.useRulesEngine ? 'bg-amber-100 text-amber-700' : 'bg-purple-100 text-purple-700'}`}>
                            {p.useRulesEngine ? 'DYNAMIC RULES' : `${p.fixedValue} (${p.fixedValueType})`}
                          </span>
                        </div>
                      ))}
                      {(!prod.pricing || prod.pricing.length === 0) && <p className="text-xs text-gray-400 italic bg-gray-50 p-6 rounded-2xl border border-dashed text-center">No pricing rules bound to this product.</p>}
                    </div>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

    </div>
  );
};

export default ProductManagementPage;
