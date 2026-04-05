import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Trash2, Loader2, Save, X, ShieldCheck, Tag, HelpCircle, Zap } from 'lucide-react';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import PlexusSelect from '../../components/PlexusSelect';
import LivePricePreview from '../../components/LivePricePreview';
import PriceSimulationTool from '../../components/PriceSimulationTool';

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
  const { setToast } = useAuth();
  const isEditing = !!id;

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [featureComponents, setFeatureComponents] = useState<FeatureComponent[]>([]);
  const [pricingComponents, setPricingComponents] = useState<any[]>([]);
  const [productTypes, setProductTypes] = useState<ProductType[]>([]);
  const [isCodeEdited, setIsCodeEdited] = useState(false);
  const [showPriceSimulation, setShowPriceSimulation] = useState(false);
  const [showPricingHelp, setShowPricingHelp] = useState(false);

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
        setToast({ message: 'Failed to fetch required data.', type: 'error' });
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
    setSubmitting(true);

    // Prepare payload for Fat DTO
    const payload = {
      ...formData,
      features: formData.features.map((f: any) => {
        if (f.featureComponentCode === 'CREATE_NEW') {
          return {
            featureComponentCode: f.tempCode,
            featureName: f.featureName,
            dataType: f.dataType,
            featureValue: f.featureValue
          };
        }
        return {
          featureComponentCode: f.featureComponentCode,
          featureName: f.featureName,
          dataType: f.dataType,
          featureValue: f.featureValue
        };
      })
    };

    try {
      if (isEditing) {
        await axios.patch(`/api/v1/products/${id}`, payload);
      } else {
        await axios.post('/api/v1/products', payload);
      }
      navigate('/products', { state: { success: isEditing ? 'Product successfully synced.' : 'Product created successfully.' } });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Operation failed.', type: 'error' });
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
              <PlexusSelect
                required
                placeholder="Select Category..."
                options={productTypes.map(t => ({ value: t.code, label: t.name }))}
                value={productTypes.find(t => t.code === formData.productTypeCode) ? { value: formData.productTypeCode, label: productTypes.find(t => t.code === formData.productTypeCode)!.name } : null}
                onChange={(opt) => setFormData({...formData, productTypeCode: opt ? opt.value : ''})}
              />
            </div>
            <div>
              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">Target Market Segment</label>
              <PlexusSelect
                required
                options={[
                  { value: 'RETAIL', label: 'RETAIL CONSUMER' },
                  { value: 'WEALTH', label: 'WEALTH MANAGEMENT' },
                  { value: 'CORPORATE', label: 'CORPORATE BANKING' },
                  { value: 'INVESTMENT', label: 'INVESTMENT BANKING' }
                ]}
                value={{
                  RETAIL: 'RETAIL CONSUMER',
                  WEALTH: 'WEALTH MANAGEMENT',
                  CORPORATE: 'CORPORATE BANKING',
                  INVESTMENT: 'INVESTMENT BANKING'
                }[formData.category as string] ? {
                  value: formData.category,
                  label: ({ RETAIL: 'RETAIL CONSUMER', WEALTH: 'WEALTH MANAGEMENT', CORPORATE: 'CORPORATE BANKING', INVESTMENT: 'INVESTMENT BANKING' } as any)[formData.category]
                } : null}
                onChange={(opt) => setFormData({...formData, category: opt ? opt.value : ''})}
              />
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
                        const val = e.target.value;
                        newF[idx].featureComponentCode = val;
                        if (val === 'CREATE_NEW') {
                          newF[idx].featureName = '';
                          newF[idx].dataType = 'STRING';
                        } else {
                          const fc = featureComponents.find(f => f.code === val);
                          if (fc) {
                            newF[idx].featureName = fc.name;
                            newF[idx].dataType = fc.dataType;
                          }
                        }
                        setFormData({...formData, features: newF});
                      }}>
                        <option value="">Select Global Component...</option>
                        <option value="CREATE_NEW" className="text-blue-600 font-bold">+ CREATE NEW FEATURE...</option>
                        {featureComponents.map(fc => <option key={fc.id} value={fc.code}>{fc.name} ({fc.dataType})</option>)}
                      </select>
                    </div>

                    {link.featureComponentCode === 'CREATE_NEW' && (
                      <div className="p-6 bg-blue-50/50 rounded-2xl border-2 border-blue-100 space-y-6 animate-in fade-in slide-in-from-top-2">
                         <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">New Feature Name</label>
                            <input type="text" className="w-full border-2 border-white rounded-xl p-3.5 text-xs bg-white font-black shadow-sm focus:border-blue-500 transition" placeholder="e.g. Max Overdraft" value={link.featureName || ''} onChange={(e) => {
                              const newF = [...formData.features];
                              newF[idx].featureName = e.target.value;
                              // Auto-gen code from name if it's new
                              newF[idx].tempCode = e.target.value.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                              setFormData({...formData, features: newF});
                            }} />
                         </div>
                         <div className="grid grid-cols-2 gap-4">
                            <div>
                              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Internal Code</label>
                              <input type="text" className="w-full border-2 border-white rounded-xl p-3.5 text-[10px] bg-white font-mono font-black shadow-sm focus:border-blue-500 transition" placeholder="MAX_OVERDRAFT" value={link.tempCode || ''} onChange={(e) => {
                                const newF = [...formData.features];
                                newF[idx].tempCode = e.target.value.toUpperCase().replace(/\s/g, '_');
                                setFormData({...formData, features: newF});
                              }} />
                            </div>
                            <div>
                              <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">Data Type</label>
                              <select className="w-full border-2 border-white rounded-xl p-3.5 text-[10px] bg-white font-black shadow-sm focus:border-blue-500 transition" value={link.dataType} onChange={(e) => {
                                const newF = [...formData.features];
                                newF[idx].dataType = e.target.value;
                                setFormData({...formData, features: newF});
                              }}>
                                <option value="STRING">STRING</option>
                                <option value="INTEGER">INTEGER</option>
                                <option value="BOOLEAN">BOOLEAN</option>
                                <option value="DECIMAL">DECIMAL</option>
                                <option value="DATE">DATE</option>
                              </select>
                            </div>
                         </div>
                      </div>
                    )}

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
              <div className="flex space-x-3">
                <button
                  type="button"
                  onClick={() => setShowPricingHelp(!showPricingHelp)}
                  className="p-2.5 bg-blue-50 text-blue-600 rounded-xl hover:bg-blue-100 transition border border-blue-200"
                  title="Pricing Help"
                >
                  <HelpCircle className="w-5 h-5" />
                </button>
                <button type="button" onClick={addPricingLink} className="bg-purple-600 text-white px-5 py-2.5 rounded-xl font-black text-[10px] uppercase tracking-widest hover:bg-purple-700 transition shadow-lg shadow-purple-50">+ Add Pricing Link</button>
              </div>
            </div>
            <div className="space-y-8">
              {/* Pricing Help Panel */}
              {showPricingHelp && (
                <div className="bg-blue-50 border-2 border-blue-200 rounded-2xl p-6 space-y-4 animate-in fade-in slide-in-from-top-2">
                  <div className="flex items-start space-x-3">
                    <Zap className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <div className="flex-1">
                      <h4 className="font-black text-blue-900 text-sm mb-3">Pricing Configuration Guide</h4>
                      <div className="space-y-3 text-sm text-blue-800">
                        <div>
                          <p className="font-bold mb-1">📌 Static Fixed Values</p>
                          <p className="text-xs">Use this for simple pricing: Set a fixed amount (absolute) or percentage and apply it directly.</p>
                        </div>
                        <div>
                          <p className="font-bold mb-1">⚡ Dynamic Rules Engine</p>
                          <p className="text-xs">Use this for complex pricing: Enable Drools rules that match customer segments and transaction amounts to select the right tier.</p>
                        </div>
                        <div>
                          <p className="font-bold mb-1">🎯 Fee Types</p>
                          <p className="text-xs">FEE_ABSOLUTE (fixed amount), FEE_PERCENTAGE (% of transaction), RATE_ABSOLUTE (interest rate)</p>
                        </div>
                        <div>
                          <p className="font-bold mb-1">💰 Discount Targeting</p>
                          <p className="text-xs">Leave target component blank for global discounts, or specify a component code to discount specific fees.</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}
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
                      <PlexusSelect
                        placeholder="Select Global Pricing..."
                        options={pricingComponents.map(pc => ({ value: pc.code, label: `${pc.name} (${pc.type})` }))}
                        value={pricingComponents.find(pc => pc.code === link.pricingComponentCode) ? {
                          value: link.pricingComponentCode,
                          label: `${pricingComponents.find(pc => pc.code === link.pricingComponentCode)!.name} (${pricingComponents.find(pc => pc.code === link.pricingComponentCode)!.type})`
                        } : null}
                        onChange={(opt) => {
                          const newP = [...formData.pricing];
                          newP[idx].pricingComponentCode = opt ? opt.value : '';
                          setFormData({...formData, pricing: newP});
                        }}
                      />
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
                        <PlexusSelect
                          options={[
                            { value: 'FEE_ABSOLUTE', label: 'FEE (ABSOLUTE)' },
                            { value: 'FEE_PERCENTAGE', label: 'FEE (PERCENTAGE)' },
                            { value: 'RATE_ABSOLUTE', label: 'RATE (ABSOLUTE)' }
                          ]}
                          value={({
                            FEE_ABSOLUTE: 'FEE (ABSOLUTE)',
                            FEE_PERCENTAGE: 'FEE (PERCENTAGE)',
                            RATE_ABSOLUTE: 'RATE (ABSOLUTE)'
                          } as any)[link.fixedValueType] ? {
                            value: link.fixedValueType,
                            label: ({ FEE_ABSOLUTE: 'FEE (ABSOLUTE)', FEE_PERCENTAGE: 'FEE (PERCENTAGE)', RATE_ABSOLUTE: 'RATE (ABSOLUTE)' } as any)[link.fixedValueType]
                          } : null}
                          onChange={(opt) => {
                            const newP = [...formData.pricing];
                            newP[idx].fixedValueType = opt ? opt.value : '';
                            setFormData({...formData, pricing: newP});
                          }}
                        />
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
             {!isEditing && (
               <button
                 type="button"
                 onClick={() => setShowPriceSimulation(true)}
                 className="flex-1 px-8 py-5 border-2 border-amber-200 text-amber-700 rounded-3xl font-black hover:bg-amber-50 transition uppercase tracking-widest text-xs flex items-center justify-center"
               >
                 <Zap className="w-5 h-5 mr-2" />
                 Test Pricing
               </button>
             )}
             <button type="submit" disabled={submitting} className="flex-1 px-8 py-5 bg-blue-600 text-white rounded-3xl font-black hover:bg-blue-700 transition shadow-2xl shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-xs disabled:opacity-50">
               {submitting ? <Loader2 className="w-6 h-6 animate-spin mr-3" /> : <Save className="w-6 h-6 mr-3" />}
               Commit Product Aggregate
             </button>
           </div>
        </form>
      </div>

      {/* Live Price Preview - Only show after product activation with pricing */}
      {isEditing && formData.pricing && formData.pricing.length > 0 && (
        <LivePricePreview productId={id ? parseInt(id) : undefined} currentFormData={formData} />
      )}

      {/* Price Simulation Tool */}
      <PriceSimulationTool
        isOpen={showPriceSimulation}
        onClose={() => setShowPriceSimulation(false)}
        defaultProductId={id ? parseInt(id) : undefined}
      />
    </div>
  );
};

export default ProductFormPage;
