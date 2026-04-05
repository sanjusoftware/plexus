import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Tag, ChevronDown, ChevronRight, CheckCircle2, Info } from 'lucide-react';
import { HasPermission } from '../../components/HasPermission';
import { useAuth } from '../../context/AuthContext';

interface TierCondition {
  attributeName: string;
  operator: string;
  attributeValue: string;
  connector: string;
}

interface PriceValue {
  priceAmount: number;
  valueType: string;
  currency?: string;
  rawValue?: number;
}

interface PricingTier {
  id?: number;
  code: string;
  name: string;
  minThreshold?: number;
  maxThreshold?: number;
  applyChargeOnFullBreach?: boolean;
  conditions: TierCondition[];
  priceValue: PriceValue;
  priceValues?: PriceValue[];
}

interface PricingComponent {
  id: number;
  code: string;
  name: string;
  type: string;
  description: string;
  status: string;
  pricingTiers: PricingTier[];
}

const PricingComponentsPage = () => {
  const navigate = useNavigate();
  const { setToast } = useAuth();
  const [components, setComponents] = useState<PricingComponent[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  const normalizeTier = (tier: any): PricingTier => {
    const firstValue = tier?.priceValue || (Array.isArray(tier?.priceValues) ? tier.priceValues[0] : null);
    return {
      ...tier,
      conditions: tier.conditions || [],
      priceValues: tier.priceValues || (firstValue ? [firstValue] : []),
      priceValue: {
        priceAmount: firstValue?.priceAmount ?? firstValue?.rawValue ?? 0,
        valueType: firstValue?.valueType || '—',
        currency: firstValue?.currency,
        rawValue: firstValue?.rawValue
      }
    };
  };

  const normalizeComponent = (component: any): PricingComponent => ({
    ...component,
    pricingTiers: (component.pricingTiers || []).map(normalizeTier)
  });

  const fetchComponents = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-components');
      setComponents((response.data || []).map(normalizeComponent));
    } catch (err: any) {
      setToast({ message: 'Failed to fetch pricing components.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchComponents();
  }, []);

  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedRows);
    if (newExpanded.has(id)) newExpanded.delete(id);
    else newExpanded.add(id);
    setExpandedRows(newExpanded);
  };

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/pricing-components/${id}/activate`);
      setToast({ message: 'Component activated and is now ready for production use.', type: 'success' });
      fetchComponents();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Activation failed.', type: 'error' });
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Components cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/pricing-components/${id}`);
      setToast({ message: 'Component deleted successfully.', type: 'success' });
      fetchComponents();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Deletion failed.', type: 'error' });
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-4 pb-10">
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-4">
          <div className="p-3 bg-purple-50 rounded-xl"><Tag className="w-6 h-6 text-purple-600" /></div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 tracking-tight">Pricing Components</h1>
            <p className="text-gray-500 font-medium mt-0.5 text-xs">Manage reusable pricing structures with multi-tier logic.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/pricing-components">
          <button
            onClick={() => navigate('/pricing-components/create')}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg flex items-center hover:bg-blue-700 transition font-bold shadow-md shadow-blue-100 text-sm"
          >
            <Plus className="w-4 h-4 mr-1.5" /> New Component
          </button>
        </HasPermission>
      </div>

      <div className="bg-blue-50/50 border-l-4 border-blue-400 p-4 rounded-r-xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-blue-100 rounded-lg"><Info className="w-5 h-5 text-blue-700" /></div>
        <p className="text-xs text-blue-900 font-medium leading-relaxed italic">
          <strong>Expert Guidance:</strong> Components define the "what" (e.g., Monthly Fee), while Tiers define the "when" and "how much" (e.g., $10 for RETAIL segment).
          Linking a component to a product enables these rules to execute during bundle pricing.
        </p>
      </div>

      {loading ? (
        <div className="flex justify-center p-16 bg-white rounded-xl border border-gray-100"><Loader2 className="w-10 h-10 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="w-10 px-4 py-3"></th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Aggregate Component</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Type</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-4 py-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest">Segments</th>
                <th className="px-4 py-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {components.map((comp) => (
                <React.Fragment key={comp.id}>
                  <tr className="hover:bg-gray-50 transition cursor-pointer group" onClick={() => toggleExpand(comp.id)}>
                    <td className="px-4 py-3 text-center">
                      {expandedRows.has(comp.id) ? <ChevronDown className="w-4 h-4 text-blue-600 font-bold" /> : <ChevronRight className="w-4 h-4 text-gray-300 group-hover:text-blue-400" />}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="text-sm font-bold text-gray-900 leading-tight">{comp.name}</div>
                      <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest">{comp.code}</div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${comp.type === 'FEE' ? 'bg-purple-50 text-purple-700 border border-purple-100' : 'bg-amber-50 text-amber-700 border border-amber-100'}`}>
                        {comp.type}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${comp.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                        {comp.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-xs font-bold text-gray-500">
                      {comp.pricingTiers?.length || 0} Tiers
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-right text-xs font-medium space-x-1">
                      {comp.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/pricing-components/*/activate">
                          <button onClick={(e) => { e.stopPropagation(); handleActivate(comp.id); }} className="text-green-600 hover:bg-green-50 p-1.5 rounded-lg transition border border-transparent hover:border-green-100" title="Activate Production Mode"><CheckCircle2 className="w-4 h-4" /></button>
                        </HasPermission>
                      )}
                      <HasPermission action="PATCH" path="/api/v1/pricing-components/*">
                        <button onClick={(e) => { e.stopPropagation(); navigate(`/pricing-components/edit/${comp.id}`); }} className="text-blue-600 hover:bg-blue-50 p-1.5 rounded-lg transition border border-transparent hover:border-blue-100" title="Edit Structure"><Edit2 className="w-4 h-4" /></button>
                      </HasPermission>
                      <HasPermission action="DELETE" path="/api/v1/pricing-components/*">
                        <button onClick={(e) => { e.stopPropagation(); handleDelete(comp.id); }} className="text-red-600 hover:bg-red-50 p-1.5 rounded-lg transition border border-transparent hover:border-red-100" title="Permanently Delete"><Trash2 className="w-4 h-4" /></button>
                      </HasPermission>
                    </td>
                  </tr>
                  {expandedRows.has(comp.id) && (
                    <tr className="bg-gray-50/30">
                      <td colSpan={6} className="px-10 py-4 border-b border-gray-100">
                        <div className="text-xs font-medium text-gray-600 mb-4 italic leading-relaxed border-l-2 border-gray-200 pl-3">{comp.description}</div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {comp.pricingTiers?.map((tier, idx) => (
                            <div key={idx} className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition">
                              <div className="flex justify-between items-start mb-4">
                                <div>
                                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-0.5">Tier: {tier.code}</div>
                                  <h4 className="font-bold text-gray-900 text-sm leading-tight">{tier.name}</h4>
                                </div>
                                <div className="text-right">
                                  <div className="text-lg font-bold text-blue-600 tracking-tight">{tier.priceValue.priceAmount}</div>
                                  <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{tier.priceValue.valueType}</div>
                                </div>
                              </div>
                              <div className="space-y-1.5">
                                <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1 border-b border-gray-50 pb-0.5">Calculation Conditions</div>
                                <div className="flex flex-wrap gap-1.5">
                                  {tier.conditions?.map((c, cidx) => (
                                    <div key={cidx} className="bg-blue-50/50 text-blue-800 px-2 py-1 rounded-lg text-[10px] font-bold border border-blue-100/50 flex items-center">
                                      <span className="text-blue-400 mr-1.5">{c.attributeName}</span>
                                      <span className="bg-white px-1 py-0.5 rounded text-blue-600 font-bold mx-1">{c.operator}</span>
                                      <span className="text-blue-900">{c.attributeValue}</span>
                                      {cidx < tier.conditions.length - 1 && <span className="ml-1.5 font-bold text-blue-300 uppercase tracking-tighter text-[9px]">{c.connector}</span>}
                                    </div>
                                  ))}
                                  {(!tier.conditions || tier.conditions.length === 0) && <div className="text-[10px] text-gray-400 italic">No segment conditions (Catch-all Tier)</div>}
                                </div>
                              </div>
                            </div>
                          ))}
                          {(!comp.pricingTiers || comp.pricingTiers.length === 0) && (
                            <div className="col-span-2 py-8 text-center text-xs text-gray-400 font-medium italic border border-dashed rounded-xl">This component has no logic defined. Click Edit to add tiers.</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

    </div>
  );
};

export default PricingComponentsPage;
