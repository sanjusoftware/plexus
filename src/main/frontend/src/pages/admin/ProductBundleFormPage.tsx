import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Trash2, Loader2, Save, Library, Package, Tag } from 'lucide-react';
import { AdminFormHeader, AdminPage } from '../../components/AdminPageLayout';
import { useBreadcrumb } from '../../context/BreadcrumbContext';
import { useAuth } from '../../context/AuthContext';
import PlexusSelect from '../../components/PlexusSelect';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard';

const ProductBundleFormPage = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const { setEntityName } = useBreadcrumb();
  const { setToast } = useAuth();
  const isEditing = !!id;

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [violations, setViolations] = useState<any[]>([]);
  const [products, setProducts] = useState<any[]>([]);
  const [pricingComponents, setPricingComponents] = useState<any[]>([]);
  const [isCodeEdited, setIsCodeEdited] = useState(false);

  const [formData, setFormData] = useState<any>({
    code: '',
    name: '',
    description: '',
    targetCustomerSegments: 'RETAIL',
    activationDate: '',
    expiryDate: '',
    products: [],
    pricing: []
  });

  const signal = useAbortSignal();
  const { resetDirtyBaseline, confirmDiscardChanges } = useUnsavedChangesGuard(formData);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [pRes, pcRes, bRes] = await Promise.all([
          axios.get('/api/v1/products', { signal }),
          axios.get('/api/v1/pricing-components', { signal }),
          isEditing ? axios.get(`/api/v1/bundles/${id}`, { signal }) : Promise.resolve({ data: null })
        ]);

        setProducts(pRes.data.content || []);
        setPricingComponents(pcRes.data || []);

        if (isEditing && bRes.data) {
          const bundle = bRes.data;
          if (bundle.status !== 'DRAFT') {
            setToast({ message: 'Only bundles in DRAFT status can be edited.', type: 'error' });
            navigate('/bundles');
            return;
          }
          setEntityName(bundle.name);
          const loadedData = {
            code: bundle.code,
            name: bundle.name,
            description: bundle.description || '',
            targetCustomerSegments: bundle.targetCustomerSegments,
            activationDate: bundle.activationDate || '',
            expiryDate: bundle.expiryDate || '',
            products: (bundle.products || []).map((p: any) => ({
              productCode: p.productCode,
              mainAccount: p.mainAccount,
              mandatory: p.mandatory
            })),
            pricing: (bundle.pricing || []).map((p: any) => ({
              pricingComponentCode: p.pricingComponentCode,
              targetComponentCode: p.targetComponentCode,
              fixedValue: p.fixedValue,
              fixedValueType: p.fixedValueType,
              useRulesEngine: p.useRulesEngine,
              effectiveDate: p.effectiveDate,
              expiryDate: p.expiryDate
            }))
          };
          setFormData(loadedData);
          resetDirtyBaseline(loadedData);
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
  }, [id, isEditing, navigate, setEntityName, setToast, signal, resetDirtyBaseline]);

  const handleCancel = () => {
    if (!confirmDiscardChanges()) return;
    navigate('/bundles');
  };

  const addProductLink = () => {
    setFormData({
      ...formData,
      products: [...formData.products, { productCode: '', mainAccount: false, mandatory: true }]
    });
  };

  const addPricingLink = () => {
    setFormData({
      ...formData,
      pricing: [...formData.pricing, { pricingComponentCode: '', useRulesEngine: false, fixedValueType: 'FEE_ABSOLUTE' }]
    });
  };

  const clearViolation = (field: string) => {
    setViolations(prev => prev.filter(v => v.field !== field));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setViolations([]);

    try {
      if (isEditing) {
        await axios.patch(`/api/v1/bundles/${id}`, formData);
      } else {
        await axios.post('/api/v1/bundles', formData);
      }
      navigate('/bundles', { state: { success: isEditing ? 'Bundle updated successfully.' : 'Bundle created successfully.' } });
    } catch (err: any) {
      if (err.response?.status === 422 && err.response?.data?.errors) {
        setViolations(err.response.data.errors);
      }
      setToast({ message: err.response?.data?.message || 'Operation failed.', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const renderViolations = (field: string) => {
    return violations
      .filter(v => v.field === field)
      .map((v, i) => (
        <div key={i} className={v.severity === 'WARNING' ? 'admin-warning-text' : 'admin-error-text'}>
          {v.reason}
        </div>
      ));
  };

  if (loading) {
    return <div className="flex justify-center p-20"><Loader2 className="h-12 w-12 animate-spin text-blue-600" /></div>;
  }

  return (
    <AdminPage>
      <AdminFormHeader
        icon={Library}
        title={isEditing ? 'Update Bundle' : 'New Bundle'}
        description="Configure bundle metadata, member products, and shared pricing rules."
        onClose={handleCancel}
      />

      <div className="bg-white rounded-xl shadow-sm border border-gray-100">
        <form onSubmit={handleSubmit} className="space-y-6 p-5">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Bundle Title</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-900 text-sm focus:border-blue-500 transition"
                value={formData.name}
                onChange={(e) => {
                  const name = e.target.value;
                  let code = formData.code;
                  if (!isEditing && !isCodeEdited) {
                    code = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                  }
                  setFormData({ ...formData, name, code });
                  clearViolation('name');
                }}
              />
              {renderViolations('name')}
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Bundle Code</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-mono font-bold text-blue-700 text-sm focus:border-blue-500 transition"
                value={formData.code}
                onChange={(e) => {
                  setIsCodeEdited(true);
                  setFormData({ ...formData, code: e.target.value.toUpperCase().replace(/\s/g, '_') });
                  clearViolation('code');
                }}
              />
              {renderViolations('code')}
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Target Segments</label>
              <input
                type="text"
                required
                className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-900 text-sm focus:border-blue-500 transition"
                value={formData.targetCustomerSegments}
                onChange={(e) => {
                  setFormData({ ...formData, targetCustomerSegments: e.target.value });
                  clearViolation('targetCustomerSegments');
                }}
                placeholder="e.g. RETAIL, VIP"
              />
              {renderViolations('targetCustomerSegments')}
            </div>
            <div className="lg:col-span-3">
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Description</label>
              <textarea
                className="w-full border border-gray-200 rounded-xl p-3 font-bold text-gray-700 text-sm focus:border-blue-500 transition"
                rows={2}
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              />
            </div>
          </div>

          <div className="border-t border-gray-100 pt-6">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-blue-50 rounded-xl"><Package className="w-5 h-5 text-blue-600" /></div>
                <h3 className="text-lg font-bold text-gray-900 tracking-tight">Member Products</h3>
              </div>
              <button type="button" onClick={addProductLink} className="bg-blue-600 text-white px-3 py-1.5 rounded-lg font-bold text-[10px] uppercase tracking-widest hover:bg-blue-700 transition">+ Add Product</button>
            </div>
            <div className="space-y-4">
              {formData.products.map((p: any, idx: number) => (
                <div key={idx} className="bg-gray-50/50 p-6 rounded-xl border border-gray-100 relative group transition hover:border-blue-200">
                  <button type="button" onClick={() => {
                    const newP = [...formData.products];
                    newP.splice(idx, 1);
                    setFormData({...formData, products: newP});
                  }} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Select Product</label>
                      <PlexusSelect
                        options={products.map(pr => ({ value: pr.code, label: pr.name, code: pr.code }))}
                        value={products.find(pr => pr.code === p.productCode) ? { value: p.productCode, label: products.find(pr => pr.code === p.productCode).name } : null}
                        onChange={(opt) => {
                          const newP = [...formData.products];
                          newP[idx].productCode = opt ? opt.value : '';
                          setFormData({...formData, products: newP});
                        }}
                      />
                    </div>
                    <div className="flex items-end space-x-4">
                       <label className="flex items-center space-x-2 cursor-pointer mb-2">
                         <input type="checkbox" className="w-4 h-4 rounded border-gray-300 text-blue-600" checked={p.mainAccount} onChange={(e) => {
                           const newP = [...formData.products];
                           // Ensure only one main account
                           if (e.target.checked) {
                             newP.forEach(item => item.mainAccount = false);
                           }
                           newP[idx].mainAccount = e.target.checked;
                           setFormData({...formData, products: newP});
                         }} />
                         <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Main Account</span>
                       </label>
                       <label className="flex items-center space-x-2 cursor-pointer mb-2">
                         <input type="checkbox" className="w-4 h-4 rounded border-gray-300 text-blue-600" checked={p.mandatory} onChange={(e) => {
                           const newP = [...formData.products];
                           newP[idx].mandatory = e.target.checked;
                           setFormData({...formData, products: newP});
                         }} />
                         <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Mandatory</span>
                       </label>
                    </div>
                  </div>
                </div>
              ))}
              {formData.products.length === 0 && <div className="py-8 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-[10px]">No Products Linked</div>}
            </div>
          </div>

          <div className="border-t border-gray-100 pt-6">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-purple-50 rounded-xl"><Tag className="w-5 h-5 text-purple-600" /></div>
                <h3 className="text-lg font-bold text-gray-900 tracking-tight">Bundle Pricing Adjustments</h3>
              </div>
              <button type="button" onClick={addPricingLink} className="bg-purple-600 text-white px-3 py-1.5 rounded-lg font-bold text-[10px] uppercase tracking-widest hover:bg-purple-700 transition">+ Add Pricing Adjustment</button>
            </div>
            <div className="space-y-4">
              {formData.pricing.map((p: any, idx: number) => (
                <div key={idx} className="bg-gray-50/50 p-6 rounded-xl border border-gray-100 relative group transition hover:border-purple-200">
                   <button type="button" onClick={() => {
                    const newP = [...formData.pricing];
                    newP.splice(idx, 1);
                    setFormData({...formData, pricing: newP});
                  }} className="absolute top-4 right-4 p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-full transition"><Trash2 className="w-4 h-4" /></button>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-4">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Pricing Component</label>
                      <PlexusSelect
                        options={pricingComponents.map(pc => ({ value: pc.code, label: pc.name }))}
                        value={pricingComponents.find(pc => pc.code === p.pricingComponentCode) ? { value: p.pricingComponentCode, label: pricingComponents.find(pc => pc.code === p.pricingComponentCode).name } : null}
                        onChange={(opt) => {
                          const newP = [...formData.pricing];
                          newP[idx].pricingComponentCode = opt ? opt.value : '';
                          setFormData({...formData, pricing: newP});
                        }}
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Target (Leave blank for Global)</label>
                       <PlexusSelect
                        options={products.map(pr => ({ value: pr.code, label: pr.name }))}
                        value={products.find(pr => pr.code === p.targetComponentCode) ? { value: p.targetComponentCode, label: products.find(pr => pr.code === p.targetComponentCode).name } : null}
                        onChange={(opt) => {
                          const newP = [...formData.pricing];
                          newP[idx].targetComponentCode = opt ? opt.value : '';
                          setFormData({...formData, pricing: newP});
                        }}
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div>
                      <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Effective Date</label>
                      <input type="date" className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] font-bold bg-white h-[42px]" value={p.effectiveDate || ''} onChange={(e) => {
                        const newP = [...formData.pricing];
                        newP[idx].effectiveDate = e.target.value;
                        setFormData({...formData, pricing: newP});
                      }} />
                    </div>
                    {!p.useRulesEngine && (
                      <>
                        <div>
                          <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Override Amount</label>
                          <input type="number" step="0.01" className="w-full border border-gray-200 rounded-lg p-2.5 text-[11px] font-bold bg-white h-[42px]" value={p.fixedValue ?? ''} onChange={(e) => {
                            const newP = [...formData.pricing];
                            newP[idx].fixedValue = parseFloat(e.target.value);
                            setFormData({...formData, pricing: newP});
                          }} />
                        </div>
                        <div>
                          <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1.5">Amount Type</label>
                          <PlexusSelect
                            options={[
                              { value: 'FEE_ABSOLUTE', label: 'FEE (ABSOLUTE)' },
                              { value: 'FEE_PERCENTAGE', label: 'FEE (PERCENTAGE)' },
                              { value: 'RATE_ABSOLUTE', label: 'RATE (ABSOLUTE)' }
                            ]}
                            value={p.fixedValueType ? { value: p.fixedValueType, label: p.fixedValueType.replace('_', ' ') } : null}
                            onChange={(opt) => {
                              const newP = [...formData.pricing];
                              newP[idx].fixedValueType = opt ? opt.value : '';
                              setFormData({...formData, pricing: newP});
                            }}
                          />
                        </div>
                      </>
                    )}
                  </div>
                </div>
              ))}
               {formData.pricing.length === 0 && <div className="py-8 text-center text-gray-400 bg-white border-2 border-dashed rounded-xl font-bold uppercase tracking-widest text-[10px]">No Pricing Adjustments</div>}
            </div>
          </div>

          <div className="flex space-x-4 border-t border-gray-100 pt-6">
            <button type="button" onClick={handleCancel} className="flex-1 px-4 py-3 border border-gray-100 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]">Discard Changes</button>
            <button type="submit" disabled={submitting} className="flex-1 px-4 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50">
              {submitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <Save className="w-5 h-5 mr-2" />}
              Commit Product Bundle
            </button>
          </div>
        </form>
      </div>
    </AdminPage>
  );
};

export default ProductBundleFormPage;
