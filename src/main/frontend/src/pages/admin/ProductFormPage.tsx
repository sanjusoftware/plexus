import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Trash2, Loader2, Save, X, ShieldCheck, Tag, AlertCircle } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';

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

const ProductFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const isEditing = !!id;

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [featureComponents, setFeatureComponents] = useState<FeatureComponent[]>([]);
  const [pricingComponents, setPricingComponents] = useState<any[]>([]);
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [isCodeEdited, setIsCodeEdited] = useState(false);
  const [error, setError] = useState('');

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

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [fc, pc, pt, p] = await Promise.all([
          axios.get('/api/v1/features'),
          axios.get('/api/v1/pricing-components'),
          axios.get('/api/v1/product-types'),
          isEditing ? axios.get(`/api/v1/products/${id}`) : Promise.resolve({ data: null })
        ]);

        setFeatureComponents(fc.data || []);
        setPricingComponents(pc.data || []);
        setProductTypes(pt.data || []);

        if (isEditing && p.data) {
          const prod = p.data;
          setEntityName(prod.name);
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
          setIsCodeEdited(true);
        }
      } catch (err: any) {
        setError('Failed to fetch required data.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id, isEditing, setEntityName]);

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
    setSubmitting(true);
    try {
      if (isEditing) {
        await axios.put(`/api/v1/products/${id}`, formData);
      } else {
        await axios.post('/api/v1/products', formData);
      }
      navigate('/products', { state: { success: isEditing ? 'Product successfully synced.' : 'Product created successfully.' } });
    } catch (err: any) {
      setError(err.response?.data?.message || 'Operation failed.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center p-32">
        <Loader2 className="w-16 h-16 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-20">
      <div className="bg-white rounded-[2.5rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="absolute top-0 right-0 w-80 h-full bg-blue-50 -skew-x-12 translate-x-40 opacity-30"></div>
        <div className="relative">
          <h1 className="text-3xl font-black text-gray-900 tracking-tight uppercase">
            {isEditing ? 'Update Product' : 'New Product'}
          </h1>
          <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">
            Unified configuration of basic data, reusable features, and pricing bindings.
          </p>
        </div>
        <button
          onClick={() => navigate('/products')}
          className="bg-gray-50 text-gray-400 p-3 rounded-2xl hover:bg-gray-100 transition relative border border-gray-100 shadow-sm"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="bg-white rounded-[2.5rem] shadow-sm overflow-hidden border border-gray-100">
        <form onSubmit={handleSubmit} className="p-12 space-y-12">
          {error && (
            <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
              <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
              <p className="text-sm font-bold">{error}</p>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-10">
            <div className="lg:col-span-1">
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Product Title</label>
              <input
                type="text"
                required
                className="w-full border-2 border-gray-100 rounded-2xl p-4 font-black text-gray-900 transition focus:border-blue-500 shadow-sm"
                value={formData.name}
                onChange={(e) => {
                  const name = e.target.value;
                  let code = formData.code;
                  if (!isEditing && !isCodeEdited) {
                    code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                  }
                  setFormData({ ...formData, name, code });
                }}
                placeholder="e.g. Ultra Savings"
              />
            </div>
            <div className="lg:col-span-1">
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Business Code (ID)</label>
              <input
                type="text"
                required
                className="w-full border-2 border-gray-100 rounded-2xl p-4 font-mono font-black text-blue-700 transition focus:border-blue-500 shadow-sm"
                value={formData.code}
                onChange={(e) => {
                  setIsCodeEdited(true);
                  setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                }}
                placeholder="e.g. SAV-PREM"
              />
            </div>
            <div className="lg:col-span-1">
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Product Classification</label>
              <select required className="w-full border-2 border-gray-100 rounded-2xl p-4 bg-gray-50 font-black text-xs uppercase tracking-widest transition focus:border-blue-500 shadow-sm" value={formData.productTypeCode} onChange={(e) => setFormData({...formData, productTypeCode: e.target.value})}>
                <option value="">Select Category...</option>
                {productTypes.map(t => <option key={t.id} value={t.code}>{t.name}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Target Market Segment</label>
              <select required className="w-full border-2 border-gray-100 rounded-2xl p-4 bg-gray-50 font-black text-xs uppercase tracking-widest transition focus:border-blue-500 shadow-sm" value={formData.category} onChange={(e) => setFormData({...formData, category: e.target.value})}>
                <option value="RETAIL">RETAIL CONSUMER</option>
                <option value="WEALTH">WEALTH MANAGEMENT</option>
                <option value="CORPORATE">CORPORATE BANKING</option>
                <option value="INVESTMENT">INVESTMENT BANKING</option>
              </select>
            </div>
            <div className="md:col-span-2">
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Short Marketing Tagline</label>
              <input type="text" className="w-full border-2 border-gray-100 rounded-2xl p-4 font-bold text-gray-700 transition focus:border-blue-500 shadow-sm" value={formData.tagline} onChange={(e) => setFormData({...formData, tagline: e.target.value})} placeholder="e.g. Grow your wealth with zero maintenance fees." />
            </div>
          </div>

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
                      <select className="w-full border-2 border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-blue-500 transition" value={link.featureComponentCode} onChange={(e) => {
                        const newF = [...formData.features];
                        newF[idx].featureComponentCode = e.target.value;
                        setFormData({...formData, features: newF});
                      }}>
                        <option value="">Select Global Component...</option>
                        {featureComponents.map(fc => <option key={fc.id} value={fc.code}>{fc.name} ({fc.dataType})</option>)}
                      </select>
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
                      <select className="w-full border-2 border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-purple-500 transition" value={link.pricingComponentCode} onChange={(e) => {
                         const newP = [...formData.pricing];
                         newP[idx].pricingComponentCode = e.target.value;
                         setFormData({...formData, pricing: newP});
                      }}>
                        <option value="">Select Global Pricing...</option>
                        {pricingComponents.map(pc => <option key={pc.id} value={pc.code}>{pc.name} ({pc.type})</option>)}
                      </select>
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
                        <select className="w-full border-2 border-gray-50 rounded-xl p-3.5 font-black text-[10px] uppercase tracking-widest bg-gray-50/30 transition focus:border-purple-500" value={link.fixedValueType} onChange={(e) => {
                           const newP = [...formData.pricing];
                           newP[idx].fixedValueType = e.target.value;
                           setFormData({...formData, pricing: newP});
                        }}>
                          <option value="FEE_ABSOLUTE">FEE (ABSOLUTE)</option>
                          <option value="FEE_PERCENTAGE">FEE (PERCENTAGE)</option>
                          <option value="RATE_ABSOLUTE">RATE (ABSOLUTE)</option>
                        </select>
                      </div>
                    </div>
                  )}
                </div>
              ))}
               {formData.pricing.length === 0 && <div className="py-12 text-center text-gray-400 bg-white border-4 border-dashed rounded-[2.5rem] font-black uppercase tracking-widest text-[10px]">No Pricing Components Bound</div>}
            </div>
          </div>

          <div className="pt-12 border-t border-gray-100 flex space-x-6">
            <button type="button" onClick={() => navigate('/products')} className="flex-1 px-8 py-5 border-2 border-gray-100 rounded-3xl font-black text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-xs">Discard Changes</button>
            <button type="submit" disabled={submitting} className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-3xl font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs disabled:opacity-50">
              {submitting ? <Loader2 className="w-6 h-6 animate-spin mr-3" /> : <Save className="w-6 h-6 mr-3" />}
              Commit Product Aggregate
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProductFormPage;
