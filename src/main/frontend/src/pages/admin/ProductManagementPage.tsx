import React, { useState, useEffect } from 'react';
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
  const [products, setProducts] = useState<Product[]>([]);
  const [featureComponents, setFeatureComponents] = useState<FeatureComponent[]>([]);
  const [pricingComponents, setPricingComponents] = useState<any[]>([]);
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);

  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);

  const [formData, setFormData] = useState<any>({
    code: '',
    name: '',
    productTypeCode: '',
    category: 'RETAIL',
    tagline: '',
    fullDescription: '',
    features: [],
    pricing: []
  });

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const [p, fc, pc, pt] = await Promise.all([
        axios.get('/api/v1/products'),
        axios.get('/api/v1/features'),
        axios.get('/api/v1/pricing-components'),
        axios.get('/api/v1/product-types')
      ]);
      setProducts(p.data.content || []);
      setFeatureComponents(fc.data || []);
      setPricingComponents(pc.data || []);
      setProductTypes(pt.data || []);
    } catch (err: any) {
      setError('Failed to fetch initial data. Please check your role permissions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  const openModal = (prod?: Product) => {
    if (prod) {
      setEditingProduct(prod);
      setFormData({
        code: prod.code,
        name: prod.name,
        productTypeCode: prod.productTypeCode,
        category: prod.category,
        tagline: prod.tagline || '',
        fullDescription: prod.fullDescription || '',
        features: prod.features || [],
        pricing: prod.pricing || []
      });
    } else {
      setEditingProduct(null);
      setFormData({
        code: '',
        name: '',
        productTypeCode: '',
        category: 'RETAIL',
        tagline: '',
        fullDescription: '',
        features: [],
        pricing: []
      });
    }
    setIsModalOpen(true);
  };

  const addFeatureLink = () => {
    setFormData({
      ...formData,
      features: [...formData.features, { featureComponentCode: '', featureValue: '' }]
    });
  };

  const addPricingLink = () => {
    setFormData({
      ...formData,
      pricing: [...formData.pricing, { pricingComponentCode: '', useRulesEngine: false, fixedValueType: 'FEE_ABSOLUTE' }]
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      if (editingProduct) {
        await axios.put(`/api/v1/products/${editingProduct.id}`, formData);
        setSuccess('Product successfully synced.');
      } else {
        await axios.post('/api/v1/products', formData);
        setSuccess('Product created with features and pricing.');
      }
      setIsModalOpen(false);
      fetchInitialData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Operation failed.');
    }
  };

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
          onClick={() => openModal()}
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
                    <button onClick={() => openModal(prod)} className="p-3 text-blue-600 hover:bg-blue-50 rounded-xl transition shadow-sm border border-blue-50" title="Modify Product"><Edit2 className="w-5 h-5" /></button>
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

      {isModalOpen && (
        <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-xl flex items-center justify-center z-50 overflow-y-auto p-4 md:p-12">
          <div className="bg-white rounded-[3.5rem] max-w-6xl w-full shadow-2xl overflow-hidden my-auto border border-white/20 animate-in zoom-in-95 duration-200">
            <div className="px-12 py-10 bg-blue-900 text-white flex justify-between items-center relative overflow-hidden">
               <div className="absolute top-0 right-0 w-80 h-full bg-blue-800 -skew-x-12 translate-x-32 opacity-30"></div>
              <div className="relative">
                <h2 className="text-4xl font-black tracking-tighter uppercase">{editingProduct ? 'Update Product Aggregate' : 'New Product Definition'}</h2>
                <p className="text-blue-200 font-bold mt-1 text-sm">Unified configuration of basic data, reusable features, and pricing bindings.</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="hover:bg-blue-800 p-3 rounded-full transition relative border border-white/10 shadow-lg"><X className="w-8 h-8" /></button>
            </div>
            <form onSubmit={handleSubmit} className="p-12 space-y-12 max-h-[75vh] overflow-y-auto custom-scrollbar">
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-10">
                <div className="lg:col-span-1">
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Product Title</label>
                  <input type="text" required className="w-full border-2 border-gray-100 rounded-2xl p-4 font-black text-gray-900 transition focus:border-blue-500 shadow-sm" value={formData.name} onChange={(e) => setFormData({...formData, name: e.target.value})} placeholder="e.g. Ultra Savings" />
                </div>
                <div className="lg:col-span-1">
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Business Code (ID)</label>
                  <input type="text" required className="w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-black text-blue-700 transition focus:border-blue-500 shadow-sm" value={formData.code} onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})} placeholder="e.g. SAV-PREM" />
                </div>
                <div className="lg:col-span-1">
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Product Classification</label>
                  <StyledSelect required className="bg-gray-50 font-black text-xs uppercase tracking-widest shadow-sm" value={formData.productTypeCode} onChange={(e) => setFormData({...formData, productTypeCode: e.target.value})}>
                    <option value="">Select Category...</option>
                    {productTypes.map(t => <option key={t.id} value={t.code}>{t.name}</option>)}
                  </StyledSelect>
                </div>
                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Target Market Segment</label>
                  <StyledSelect required className="bg-gray-50 font-black text-xs uppercase tracking-widest shadow-sm" value={formData.category} onChange={(e) => setFormData({...formData, category: e.target.value})}>
                    <option value="RETAIL">RETAIL CONSUMER</option>
                    <option value="WEALTH">WEALTH MANAGEMENT</option>
                    <option value="CORPORATE">CORPORATE BANKING</option>
                    <option value="INVESTMENT">INVESTMENT BANKING</option>
                  </StyledSelect>
                </div>
                <div className="md:col-span-2">
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Short Marketing Tagline</label>
                  <input type="text" className="w-full border-2 border-gray-100 rounded-2xl p-4 font-bold text-gray-700 transition focus:border-blue-500 shadow-sm" value={formData.tagline} onChange={(e) => setFormData({...formData, tagline: e.target.value})} placeholder="e.g. Grow your wealth with zero maintenance fees." />
                </div>
              </div>

              {/* Feature Selection */}
              <div className="border-t border-gray-100 pt-12">
                <div className="flex justify-between items-center mb-8">
                  <div className="flex items-center space-x-4">
                    <div className="p-3 bg-blue-50 rounded-2xl"><ShieldCheck className="w-6 h-6 text-blue-600" /></div>
                    <h3 className="text-2xl font-black text-gray-900 tracking-tight">Feature Component Links</h3>
                  </div>
                  <button type="button" onClick={addFeatureLink} className="bg-blue-600 text-white px-5 py-2.5 rounded-xl font-black text-[10px] uppercase tracking-widest hover:bg-blue-700 transition shadow-lg shadow-blue-50">+ Add Component Link</button>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {formData.features.map((link: any, idx: number) => (
                    <div key={idx} className="bg-gray-50 p-8 rounded-3xl border-2 border-gray-100 relative group transition hover:border-blue-200">
                      <button type="button" onClick={() => {
                        const newF = [...formData.features];
                        newF.splice(idx, 1);
                        setFormData({...formData, features: newF});
                      }} className="absolute top-6 right-6 p-2 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-5 h-5" /></button>

                      <div className="space-y-6">
                        <div>
                          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Feature Definition</label>
                          <StyledSelect className="border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-blue-500" value={link.featureComponentCode} onChange={(e) => {
                            const newF = [...formData.features];
                            newF[idx].featureComponentCode = e.target.value;
                            setFormData({...formData, features: newF});
                          }}>
                            <option value="">Select Global Component...</option>
                            {featureComponents.map(fc => <option key={fc.id} value={fc.code}>{fc.name} ({fc.dataType})</option>)}
                          </StyledSelect>
                        </div>
                        <div>
                          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Concrete Value</label>
                          <input type="text" className="w-full border-2 border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-blue-500 transition" placeholder="e.g. 5.5, YES, 12 months" value={link.featureValue} onChange={(e) => {
                            const newF = [...formData.features];
                            newF[idx].featureValue = e.target.value;
                            setFormData({...formData, features: newF});
                          }} />
                        </div>
                      </div>
                    </div>
                  ))}
                  {formData.features.length === 0 && <div className="col-span-2 py-12 text-center text-gray-400 bg-white border-4 border-dashed rounded-[2.5rem] font-black uppercase tracking-widest text-[10px]">No Feature Components Selected</div>}
                </div>
              </div>

              {/* Pricing Selection */}
              <div className="border-t border-gray-100 pt-12">
                <div className="flex justify-between items-center mb-8">
                  <div className="flex items-center space-x-4">
                     <div className="p-3 bg-purple-50 rounded-2xl"><Tag className="w-6 h-6 text-purple-600" /></div>
                     <h3 className="text-2xl font-black text-gray-900 tracking-tight">Pricing Rule Bindings</h3>
                  </div>
                  <button type="button" onClick={addPricingLink} className="bg-purple-600 text-white px-5 py-2.5 rounded-xl font-black text-[10px] uppercase tracking-widest hover:bg-purple-700 transition shadow-lg shadow-purple-50">+ Add Pricing Link</button>
                </div>
                <div className="space-y-8">
                  {formData.pricing.map((link: any, idx: number) => (
                    <div key={idx} className="bg-gray-50/50 p-8 rounded-3xl border-2 border-gray-100 relative hover:border-purple-200 transition">
                      <button type="button" onClick={() => {
                        const newP = [...formData.pricing];
                        newP.splice(idx, 1);
                        setFormData({...formData, pricing: newP});
                      }} className="absolute top-6 right-6 p-2 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-5 h-5" /></button>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-8">
                        <div>
                          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Pricing Aggregate Component</label>
                          <StyledSelect className="border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-purple-500" value={link.pricingComponentCode} onChange={(e) => {
                             const newP = [...formData.pricing];
                             newP[idx].pricingComponentCode = e.target.value;
                             setFormData({...formData, pricing: newP});
                          }}>
                            <option value="">Select Global Pricing...</option>
                            {pricingComponents.map(pc => <option key={pc.id} value={pc.code}>{pc.name} ({pc.type})</option>)}
                          </StyledSelect>
                        </div>
                        <div className="flex items-end">
                          <label className="flex items-center cursor-pointer p-4 bg-white rounded-xl border-2 border-gray-100 hover:border-blue-200 transition w-full shadow-sm">
                            <input type="checkbox" className="w-6 h-6 rounded-lg text-blue-600 border-gray-300 focus:ring-blue-500" checked={link.useRulesEngine} onChange={(e) => {
                              const newP = [...formData.pricing];
                              newP[idx].useRulesEngine = e.target.checked;
                              setFormData({...formData, pricing: newP});
                            }} />
                            <div className="ml-4">
                              <span className="text-xs font-black text-gray-900 uppercase tracking-widest block">Activate Rules Engine</span>
                              <span className="text-[10px] text-gray-400 font-bold uppercase italic tracking-tighter">Use dynamic tiered segments</span>
                            </div>
                          </label>
                        </div>
                      </div>

                      {!link.useRulesEngine && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 p-6 bg-white rounded-2xl border-2 border-purple-50 shadow-sm animate-in fade-in duration-300">
                          <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Static Override Amount</label>
                            <input type="number" step="0.01" className="w-full border-2 border-gray-50 rounded-xl p-3.5 font-black text-gray-900 transition focus:border-purple-500 bg-gray-50/30" value={link.fixedValue} onChange={(e) => {
                              const newP = [...formData.pricing];
                              newP[idx].fixedValue = parseFloat(e.target.value);
                              setFormData({...formData, pricing: newP});
                            }} />
                          </div>
                          <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Amount Type</label>
                            <StyledSelect className="border-gray-50 rounded-xl p-3.5 font-black text-[10px] uppercase tracking-widest bg-gray-50/30 focus:border-purple-500" value={link.fixedValueType} onChange={(e) => {
                               const newP = [...formData.pricing];
                               newP[idx].fixedValueType = e.target.value;
                               setFormData({...formData, pricing: newP});
                            }}>
                              <option value="FEE_ABSOLUTE">FEE (ABSOLUTE)</option>
                              <option value="FEE_PERCENTAGE">FEE (PERCENTAGE)</option>
                              <option value="RATE_ABSOLUTE">RATE (ABSOLUTE)</option>
                            </StyledSelect>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                   {formData.pricing.length === 0 && <div className="py-12 text-center text-gray-400 bg-white border-4 border-dashed rounded-[2.5rem] font-black uppercase tracking-widest text-[10px]">No Pricing Components Bound</div>}
                </div>
              </div>

              <div className="pt-12 border-t border-gray-100 flex space-x-6">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-3xl font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard Changes</button>
                <button type="submit" className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-3xl font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs">
                  <Save className="w-6 h-6 mr-3" /> Commit Product Aggregate
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductManagementPage;
