import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Trash2, Loader2, Save, Package, ShieldCheck, Tag, HelpCircle, Zap } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import PlexusSelect from '../../components/PlexusSelect';
import { useAbortSignal } from '../../hooks/useAbortSignal';
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

  const signal = useAbortSignal();

  const getDefaultValueForType = (type: string) => {
    switch (type) {
      case 'BOOLEAN': return 'false';
      case 'INTEGER':
      case 'DECIMAL': return '0';
      case 'DATE': return '';
      default: return '';
    }
  };

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [fc, pc, pt, p] = await Promise.all([
          axios.get('/api/v1/features', { signal }),
          axios.get('/api/v1/pricing-components', { signal }),
          axios.get('/api/v1/product-types', { signal }),
          isEditing ? axios.get(`/api/v1/products/${id}`, { signal }) : Promise.resolve({ data: null })
        ]);

        setFeatureComponents(fc.data || []);
        setPricingComponents(pc.data || []);
        setProductTypes(pt.data || []);

        if (isEditing && p.data) {
          const prod = p.data;
          if (prod.status !== 'DRAFT') {
            setToast({ message: 'Only products in DRAFT status can be edited.', type: 'error' });
            navigate('/products');
            return;
          }
          setEntityName(prod.name);
          setFormData({
            code: prod.code,
            name: prod.name,
            productTypeCode: prod.productType?.code || '',
            category: prod.category,
            tagline: prod.tagline || '',
            fullDescription: prod.fullDescription || '',
            features: prod.features || [],
            pricing: prod.pricing || []
          });
          setIsCodeEdited(true);
        }
      } catch (err: any) {
        if (axios.isCancel(err)) return;
        setToast({ message: 'Failed to fetch required data.', type: 'error' });
      } finally {
        if (!signal.aborted) {
          setLoading(false);
        }
      }
    };

    fetchData();
  }, [id, isEditing, setEntityName, setToast, signal]);

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
      <div className="flex justify-center p-20">
        <Loader2 className="h-12 w-12 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <AdminPage>
      <AdminFormHeader
        icon={Package}
        title={isEditing ? 'Update Product' : 'New Product'}
        description="Unified configuration of basic data, reusable features, and pricing bindings."
        onClose={() => navigate('/products')}
      />

      <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100">
        <form onSubmit={handleSubmit} className="space-y-6 p-5">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div className="lg:col-span-1">
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Product Title</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-900 text-sm transition focus:border-blue-500 shadow-sm"
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
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Business Code (ID)</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-mono font-bold text-blue-700 text-sm transition focus:border-blue-500 shadow-sm"
                value={formData.code}
                onChange={(e) => {
                  setIsCodeEdited(true);
                  setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                }}
                placeholder="e.g. SAV-PREM"
              />
            </div>
            <div className="lg:col-span-1">
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Product Classification</label>
              <PlexusSelect
                required
                placeholder="Select Category..."
                options={productTypes.map(t => ({ value: t.code, label: t.name }))}
                value={productTypes.find(t => t.code === formData.productTypeCode) ? { value: formData.productTypeCode, label: productTypes.find(t => t.code === formData.productTypeCode)!.name } : null}
                onChange={(opt) => setFormData({...formData, productTypeCode: opt ? opt.value : ''})}
              />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Target Market Segment</label>
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
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Short Marketing Tagline</label>
              <input type="text" className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-700 text-sm transition focus:border-blue-500 shadow-sm" value={formData.tagline} onChange={(e) => setFormData({...formData, tagline: e.target.value})} placeholder="e.g. Grow your wealth with zero maintenance fees." />
            </div>
          </div>

          <div className="border-t border-gray-100 pt-6">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-blue-50 rounded-xl"><ShieldCheck className="w-5 h-5 text-blue-600" /></div>
                <h3 className="text-lg font-bold text-gray-900 tracking-tight">Product Features</h3>
              </div>
              <button type="button" onClick={addFeatureLink} className="bg-blue-600 text-white px-3 py-1.5 rounded-lg font-bold text-[10px] uppercase tracking-widest hover:bg-blue-700 transition shadow-md shadow-blue-50">+ Link Feature</button>
            </div>
            <div className="space-y-4">
              {formData.features.map((link: any, idx: number) => (
                <div key={idx} className="bg-gray-50/50 p-6 rounded-xl border border-gray-100 relative group transition hover:border-blue-200">
                  <button type="button" onClick={() => {
                    const newF = [...formData.features];
                    newF.splice(idx, 1);
                    setFormData({...formData, features: newF});
                  }} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Feature Definition</label>
                      <PlexusSelect
                        placeholder="Select Global Component..."
                        options={[
                          { value: 'CREATE_NEW', label: '+ CREATE NEW FEATURE...' },
                          ...featureComponents.map(fc => ({ value: fc.code, label: `${fc.name} (${fc.dataType})` }))
                        ]}
                        value={(() => {
                          if (link.featureComponentCode === 'CREATE_NEW') {
                            return { value: 'CREATE_NEW', label: '+ CREATE NEW FEATURE...' };
                          }
                          const fc = featureComponents.find(f => f.code === link.featureComponentCode);
                          return fc ? { value: fc.code, label: `${fc.name} (${fc.dataType})` } : null;
                        })()}
                        onChange={(opt) => {
                          const newF = [...formData.features];
                          const val = opt ? opt.value : '';
                          newF[idx].featureComponentCode = val;
                          if (val === 'CREATE_NEW') {
                            newF[idx].featureName = '';
                            newF[idx].dataType = 'STRING';
                            newF[idx].featureValue = '';
                          } else if (val === '') {
                            newF[idx].featureName = '';
                            newF[idx].dataType = 'STRING';
                            newF[idx].featureValue = '';
                          } else {
                            const fc = featureComponents.find(f => f.code === val);
                            if (fc) {
                              newF[idx].featureName = fc.name;
                              newF[idx].dataType = fc.dataType;
                              newF[idx].featureValue = getDefaultValueForType(fc.dataType);
                            }
                          }
                          setFormData({...formData, features: newF});
                        }}
                      />
                    </div>

                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Concrete Value</label>
                      {(() => {
                        const dataType = link.dataType || 'STRING';
                        switch (dataType) {
                          case 'BOOLEAN':
                            return (
                              <div
                                className="h-[42px] flex items-center px-3 bg-white border border-white rounded-lg shadow-sm cursor-pointer"
                                onClick={() => {
                                  const newF = [...formData.features];
                                  newF[idx].featureValue = link.featureValue === 'true' ? 'false' : 'true';
                                  setFormData({...formData, features: newF});
                                }}
                              >
                                <button
                                  type="button"
                                  className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${link.featureValue === 'true' ? 'bg-blue-600' : 'bg-gray-200'}`}
                                >
                                  <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${link.featureValue === 'true' ? 'translate-x-5' : 'translate-x-1'}`} />
                                </button>
                                <span className="ml-2 text-[9px] font-bold uppercase tracking-widest text-gray-500 select-none">
                                  {link.featureValue === 'true' ? 'TRUE' : 'FALSE'}
                                </span>
                              </div>
                            );
                          case 'DATE':
                            return (
                              <input
                                type="date"
                                className="w-full border border-white rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                value={link.featureValue}
                                onChange={(e) => {
                                  const newF = [...formData.features];
                                  newF[idx].featureValue = e.target.value;
                                  setFormData({...formData, features: newF});
                                }}
                              />
                            );
                          case 'INTEGER':
                          case 'DECIMAL':
                            return (
                              <input
                                type="number"
                                step={dataType === 'INTEGER' ? '1' : 'any'}
                                className="w-full border border-white rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                placeholder="Value..."
                                value={link.featureValue}
                                onChange={(e) => {
                                  const newF = [...formData.features];
                                  newF[idx].featureValue = e.target.value;
                                  setFormData({...formData, features: newF});
                                }}
                              />
                            );
                          default:
                            return (
                              <input
                                type="text"
                                className="w-full border border-white rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition h-[42px]"
                                placeholder="e.g. 5.5, YES, 12 months"
                                value={link.featureValue}
                                onChange={(e) => {
                                  const newF = [...formData.features];
                                  newF[idx].featureValue = e.target.value;
                                  setFormData({...formData, features: newF});
                                }}
                              />
                            );
                        }
                      })()}
                    </div>

                    {link.featureComponentCode === 'CREATE_NEW' && (
                      <div className="md:col-span-2 p-4 bg-blue-50/50 rounded-xl border border-blue-100 space-y-4 animate-in fade-in slide-in-from-top-2">
                         <div>
                            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">New Feature Name</label>
                            <input type="text" className="w-full border border-white rounded-lg p-2.5 text-[11px] bg-white font-bold shadow-sm focus:border-blue-500 transition" placeholder="e.g. Max Overdraft" value={link.featureName || ''} onChange={(e) => {
                              const newF = [...formData.features];
                              newF[idx].featureName = e.target.value;
                              // Auto-gen code from name if it's new
                              newF[idx].tempCode = e.target.value.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                              setFormData({...formData, features: newF});
                            }} />
                         </div>
                         <div className="grid grid-cols-2 gap-3">
                            <div>
                              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Internal Code</label>
                              <input type="text" className="w-full border border-white rounded-lg p-2.5 text-[9px] bg-white font-mono font-bold shadow-sm focus:border-blue-500 transition" placeholder="MAX_OVERDRAFT" value={link.tempCode || ''} onChange={(e) => {
                                const newF = [...formData.features];
                                newF[idx].tempCode = e.target.value.toUpperCase().replace(/\s/g, '_');
                                setFormData({...formData, features: newF});
                              }} />
                            </div>
                            <div>
                              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Data Type</label>
                              <PlexusSelect
                                options={[
                                  { value: 'STRING', label: 'STRING' },
                                  { value: 'INTEGER', label: 'INTEGER' },
                                  { value: 'BOOLEAN', label: 'BOOLEAN' },
                                  { value: 'DECIMAL', label: 'DECIMAL' },
                                  { value: 'DATE', label: 'DATE' }
                                ]}
                                value={link.dataType ? { value: link.dataType, label: link.dataType } : null}
                                onChange={(opt) => {
                                  const newF = [...formData.features];
                                  const newType = opt ? opt.value : 'STRING';
                                  newF[idx].dataType = newType;
                                  newF[idx].featureValue = getDefaultValueForType(newType);
                                  setFormData({...formData, features: newF});
                                }}
                              />
                            </div>
                         </div>
                      </div>
                    )}
                  </div>
                </div>
              ))}
              {formData.features.length === 0 && <div className="py-8 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-[10px]">No Feature Components Selected</div>}
            </div>
          </div>

           <div className="border-t border-gray-100 pt-6">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                 <div className="p-2 bg-purple-50 rounded-xl"><Tag className="w-5 h-5 text-purple-600" /></div>
                 <h3 className="text-lg font-bold text-gray-900 tracking-tight">Pricing Rule Bindings</h3>
              </div>
              <div className="flex space-x-2">
                <button
                  type="button"
                  onClick={() => setShowPricingHelp(!showPricingHelp)}
                  className="p-1.5 bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100 transition border border-blue-200"
                  title="Pricing Help"
                >
                  <HelpCircle className="w-4 h-4" />
                </button>
                <button type="button" onClick={addPricingLink} className="bg-purple-600 text-white px-3 py-1.5 rounded-lg font-bold text-[10px] uppercase tracking-widest hover:bg-purple-700 transition shadow-md shadow-purple-50">+ Add Pricing Link</button>
              </div>
            </div>
            <div className="space-y-4">
              {/* Pricing Help Panel */}
              {showPricingHelp && (
                <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 space-y-3 animate-in fade-in slide-in-from-top-2">
                  <div className="flex items-start space-x-3">
                    <Zap className="w-4 h-4 text-blue-600 flex-shrink-0 mt-0.5" />
                    <div className="flex-1">
                      <h4 className="font-bold text-blue-900 text-xs mb-2">Pricing Configuration Guide</h4>
                      <div className="space-y-2 text-[11px] text-blue-800">
                        <div>
                          <p className="font-bold mb-0.5">📌 Static Fixed Values</p>
                          <p>Use this for simple pricing: Set a fixed amount (absolute) or percentage and apply it directly.</p>
                        </div>
                        <div>
                          <p className="font-bold mb-0.5">⚡ Dynamic Rules Engine</p>
                          <p>Use this for complex pricing: Enable Drools rules that match customer segments and transaction amounts to select the right tier.</p>
                        </div>
                        <div>
                          <p className="font-bold mb-0.5">🎯 Fee Types</p>
                          <p>FEE_ABSOLUTE (fixed amount), FEE_PERCENTAGE (% of transaction), RATE_ABSOLUTE (interest rate)</p>
                        </div>
                        <div>
                          <p className="font-bold mb-0.5">💰 Discount Targeting</p>
                          <p>Leave target component blank for global discounts, or specify a component code to discount specific fees.</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {formData.pricing.map((link: any, idx: number) => (
                <div key={idx} className="bg-gray-50/50 p-6 rounded-xl border border-gray-100 relative hover:border-purple-200 transition">
                  <button type="button" onClick={() => {
                    const newP = [...formData.pricing];
                    newP.splice(idx, 1);
                    setFormData({...formData, pricing: newP});
                  }} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-4">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Pricing Aggregate Component</label>
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
                      <div className="flex items-center justify-between px-4 bg-white rounded-lg border border-gray-200 transition w-full shadow-sm h-[42px]">
                        <div className="flex flex-col justify-center">
                          <span className="text-[11px] font-bold text-gray-900 uppercase tracking-widest leading-tight">Activate Rules Engine</span>
                          <span className="text-[9px] text-gray-400 font-bold uppercase italic tracking-tighter leading-tight">Use dynamic tiered segments</span>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            const newP = [...formData.pricing];
                            newP[idx].useRulesEngine = !link.useRulesEngine;
                            setFormData({...formData, pricing: newP});
                          }}
                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${link.useRulesEngine ? 'bg-blue-600' : 'bg-gray-200'}`}
                        >
                          <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${link.useRulesEngine ? 'translate-x-5' : 'translate-x-1'}`} />
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Effective Date</label>
                      <input
                        type="date"
                        className="w-full border border-gray-200 rounded-lg px-3 font-bold text-gray-900 text-[11px] transition focus:border-blue-500 bg-white h-[42px] shadow-sm"
                        value={link.effectiveDate || ''}
                        onChange={(e) => {
                          const newP = [...formData.pricing];
                          newP[idx].effectiveDate = e.target.value;
                          setFormData({...formData, pricing: newP});
                        }}
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Expiry Date</label>
                      <input
                        type="date"
                        className="w-full border border-gray-200 rounded-lg px-3 font-bold text-gray-900 text-[11px] transition focus:border-blue-500 bg-white h-[42px] shadow-sm"
                        value={link.expiryDate || ''}
                        onChange={(e) => {
                          const newP = [...formData.pricing];
                          newP[idx].expiryDate = e.target.value;
                          setFormData({...formData, pricing: newP});
                        }}
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Target Component Code</label>
                      <PlexusSelect
                        placeholder="Select Target (Optional)..."
                        options={pricingComponents.filter(pc => pc.code !== link.pricingComponentCode).map(pc => ({ value: pc.code, label: pc.code }))}
                        value={link.targetComponentCode ? { value: link.targetComponentCode, label: link.targetComponentCode } : null}
                        onChange={(opt) => {
                          const newP = [...formData.pricing];
                          newP[idx].targetComponentCode = opt ? opt.value : '';
                          setFormData({...formData, pricing: newP});
                        }}
                      />
                    </div>
                  </div>

                  {!link.useRulesEngine && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 p-4 bg-white rounded-xl border border-purple-50 shadow-sm animate-in fade-in duration-300">
                      <div>
                        <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Static Override Amount</label>
                        <input
                          type="number"
                          step="0.01"
                          className="w-full border border-gray-200 rounded-lg px-3 font-bold text-gray-900 text-sm transition focus:border-blue-500 bg-white h-[42px] shadow-sm"
                          value={link.fixedValue ?? ''}
                          onChange={(e) => {
                            const newP = [...formData.pricing];
                            newP[idx].fixedValue = parseFloat(e.target.value);
                            setFormData({...formData, pricing: newP});
                          }}
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Amount Type</label>
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
               {formData.pricing.length === 0 && <div className="py-8 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-[10px]">No Pricing Components Bound</div>}
            </div>
          </div>

           <div className="flex space-x-4 border-t border-gray-100 pt-6">
             <button type="button" onClick={() => navigate('/products')} className="flex-1 px-4 py-3 border border-gray-100 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]">Discard Changes</button>
             {!isEditing && (
               <button
                 type="button"
                 onClick={() => setShowPriceSimulation(true)}
                 className="flex-1 px-4 py-3 border border-amber-200 text-amber-700 rounded-xl font-bold hover:bg-amber-50 transition uppercase tracking-widest text-[10px] flex items-center justify-center"
               >
                 <Zap className="w-4 h-4 mr-1.5" />
                 Test Pricing
               </button>
             )}
             <button type="submit" disabled={submitting} className="flex-1 px-4 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50">
               {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
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
    </AdminPage>
  );
};

export default ProductFormPage;
