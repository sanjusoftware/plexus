import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { Plus, Edit2, Trash2, Loader2, Tag, ChevronDown, ChevronRight, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import { HasPermission } from '../../components/HasPermission';

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
  const location = useLocation();
  const [components, setComponents] = useState<PricingComponent[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchComponents = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-components');
      setComponents(response.data);
    } catch (err: any) {
      setError('Failed to fetch pricing components.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchComponents();
    if (location.state?.success) {
      setSuccess(location.state.success);
      window.history.replaceState({}, document.title);
    }
  }, [location]);

  const toggleExpand = (id: number) => {
    const newExpanded = new Set(expandedRows);
    if (newExpanded.has(id)) newExpanded.delete(id);
    else newExpanded.add(id);
    setExpandedRows(newExpanded);
  };

  const handleActivate = async (id: number) => {
    try {
      await axios.post(`/api/v1/pricing-components/${id}/activate`);
      setSuccess('Component activated and is now ready for production use.');
      fetchComponents();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Activation failed.');
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure? Components cannot be deleted if they are linked to active products.')) return;
    try {
      await axios.delete(`/api/v1/pricing-components/${id}`);
      setSuccess('Component deleted.');
      fetchComponents();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Deletion failed.');
    }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6 pb-20">
      <div className="bg-white rounded-[2rem] p-8 shadow-sm border border-gray-100 flex justify-between items-center">
        <div className="flex items-center space-x-6">
          <div className="p-4 bg-purple-50 rounded-2xl"><Tag className="w-8 h-8 text-purple-600" /></div>
          <div>
            <h1 className="text-3xl font-black text-gray-900 tracking-tight">Pricing Components</h1>
            <p className="text-gray-500 font-medium mt-1">Manage reusable pricing structures with multi-tier logic.</p>
          </div>
        </div>
        <HasPermission action="POST" path="/api/v1/pricing-components">
          <button
            onClick={() => navigate('/pricing-components/create')}
            className="bg-blue-600 text-white px-7 py-3 rounded-2xl flex items-center hover:bg-blue-700 transition font-black shadow-xl shadow-blue-100 uppercase tracking-widest text-xs"
          >
            <Plus className="w-5 h-5 mr-2" /> New Component
          </button>
        </HasPermission>
      </div>

      <div className="bg-blue-50/50 border-l-4 border-blue-400 p-6 rounded-r-2xl shadow-sm flex items-start space-x-4">
        <div className="p-2 bg-blue-100 rounded-lg"><Info className="w-5 h-5 text-blue-700" /></div>
        <p className="text-sm text-blue-900 font-medium leading-relaxed italic">
          <strong>Expert Guidance:</strong> Components define the "what" (e.g., Monthly Fee), while Tiers define the "when" and "how much" (e.g., $10 for RETAIL segment).
          Linking a component to a product enables these rules to execute during bundle pricing.
        </p>
      </div>

      {error && <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-r-xl text-red-700 text-sm font-bold flex items-center shadow-sm"><AlertCircle className="w-4 h-4 mr-3" />{error}</div>}
      {success && <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded-r-xl text-green-700 text-sm font-bold flex items-center shadow-sm"><CheckCircle2 className="w-4 h-4 mr-3" />{success}</div>}

      {loading ? (
        <div className="flex justify-center p-24 bg-white rounded-3xl border border-gray-100"><Loader2 className="w-12 h-12 animate-spin text-blue-600" /></div>
      ) : (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50/50">
              <tr>
                <th className="w-12 px-6 py-5"></th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Aggregate Component</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Type</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-6 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Segments</th>
                <th className="px-6 py-5 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest">Actions</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-50">
              {components.map((comp) => (
                <React.Fragment key={comp.id}>
                  <tr className="hover:bg-gray-50 transition cursor-pointer group" onClick={() => toggleExpand(comp.id)}>
                    <td className="px-6 py-6 text-center">
                      {expandedRows.has(comp.id) ? <ChevronDown className="w-5 h-5 text-blue-600 font-black" /> : <ChevronRight className="w-5 h-5 text-gray-300 group-hover:text-blue-400" />}
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <div className="text-sm font-black text-gray-900 leading-tight">{comp.name}</div>
                      <div className="text-[10px] text-gray-400 font-mono mt-1 tracking-widest">{comp.code}</div>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${comp.type === 'FEE' ? 'bg-purple-50 text-purple-700 border border-purple-100' : 'bg-amber-50 text-amber-700 border border-amber-100'}`}>
                        {comp.type}
                      </span>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap">
                      <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${comp.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-yellow-50 text-yellow-700 border border-yellow-100'}`}>
                        {comp.status}
                      </span>
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap text-xs font-bold text-gray-500">
                      {comp.pricingTiers?.length || 0} Tiered Segments
                    </td>
                    <td className="px-6 py-6 whitespace-nowrap text-right text-sm font-medium space-x-1">
                      {comp.status === 'DRAFT' && (
                        <HasPermission action="POST" path="/api/v1/pricing-components/*/activate">
                          <button onClick={(e) => { e.stopPropagation(); handleActivate(comp.id); }} className="text-green-600 hover:bg-green-50 p-2.5 rounded-xl transition border border-transparent hover:border-green-100" title="Activate Production Mode"><CheckCircle2 className="w-4 h-4" /></button>
                        </HasPermission>
                      )}
                      <HasPermission action="PUT" path="/api/v1/pricing-components/*">
                        <button onClick={(e) => { e.stopPropagation(); navigate(`/pricing-components/edit/${comp.id}`); }} className="text-blue-600 hover:bg-blue-50 p-2.5 rounded-xl transition border border-transparent hover:border-blue-100" title="Edit Structure"><Edit2 className="w-4 h-4" /></button>
                      </HasPermission>
                      <HasPermission action="DELETE" path="/api/v1/pricing-components/*">
                        <button onClick={(e) => { e.stopPropagation(); handleDelete(comp.id); }} className="text-red-600 hover:bg-red-50 p-2.5 rounded-xl transition border border-transparent hover:border-red-100" title="Permanently Delete"><Trash2 className="w-4 h-4" /></button>
                      </HasPermission>
                    </td>
                  </tr>
                  {expandedRows.has(comp.id) && (
                    <tr className="bg-gray-50/30">
                      <td colSpan={6} className="px-16 py-8 border-b border-gray-100">
                        <div className="text-sm font-medium text-gray-600 mb-8 italic leading-relaxed border-l-2 border-gray-200 pl-4">{comp.description}</div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                          {comp.pricingTiers?.map((tier, idx) => (
                            <div key={idx} className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm hover:shadow-md transition">
                              <div className="flex justify-between items-start mb-6">
                                <div>
                                  <div className="text-xs font-black text-gray-400 uppercase tracking-widest mb-1">Tier Code: {tier.code}</div>
                                  <h4 className="font-black text-gray-900 text-lg leading-tight">{tier.name}</h4>
                                </div>
                                <div className="text-right">
                                  <div className="text-2xl font-black text-blue-600 tracking-tight">{tier.priceValue.priceAmount}</div>
                                  <div className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{tier.priceValue.valueType}</div>
                                </div>
                              </div>
                              <div className="space-y-2">
                                <div className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2 border-b border-gray-50 pb-1">Calculation Conditions</div>
                                <div className="flex flex-wrap gap-2">
                                  {tier.conditions?.map((c, cidx) => (
                                    <div key={cidx} className="bg-blue-50/50 text-blue-800 px-3 py-1.5 rounded-xl text-[11px] font-bold border border-blue-100/50 flex items-center">
                                      <span className="text-blue-400 mr-2">{c.attributeName}</span>
                                      <span className="bg-white px-1.5 py-0.5 rounded text-blue-600 font-black mx-1">{c.operator}</span>
                                      <span className="text-blue-900">{c.attributeValue}</span>
                                      {cidx < tier.conditions.length - 1 && <span className="ml-2 font-black text-blue-300 uppercase tracking-tighter text-[9px]">{c.connector}</span>}
                                    </div>
                                  ))}
                                  {(!tier.conditions || tier.conditions.length === 0) && <div className="text-xs text-gray-400 italic">No segment conditions (Catch-all Tier)</div>}
                                </div>
                              </div>
                            </div>
                          ))}
                          {(!comp.pricingTiers || comp.pricingTiers.length === 0) && (
                            <div className="col-span-2 py-12 text-center text-gray-400 font-medium italic border-2 border-dashed rounded-3xl">This component has no logic defined. Click Edit to add tiers.</div>
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
