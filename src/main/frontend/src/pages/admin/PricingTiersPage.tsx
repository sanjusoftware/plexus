import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Layers, Loader2, Tag, ChevronRight, CheckCircle2, AlertCircle } from 'lucide-react';
import { Link } from 'react-router-dom';

interface PricingTier {
  id: number;
  code: string;
  name: string;
  minThreshold?: number;
  maxThreshold?: number;
  applyChargeOnFullBreach?: boolean;
  conditions: any[];
  priceValue: any;
  componentId?: number;
  componentName?: string;
  componentCode?: string;
}

const PricingTiersPage = () => {
  const [tiers, setTiers] = useState<PricingTier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchTiers = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/pricing-components');
      const allTiers: PricingTier[] = [];
      response.data.forEach((comp: any) => {
        if (comp.pricingTiers) {
          comp.pricingTiers.forEach((tier: any) => {
            allTiers.push({
              ...tier,
              componentId: comp.id,
              componentName: comp.name,
              componentCode: comp.code
            });
          });
        }
      });
      setTiers(allTiers);
    } catch (err: any) {
      setError('Failed to fetch pricing tiers from the components registry.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTiers();
  }, []);

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-20">
      <div className="bg-white rounded-[2rem] p-10 shadow-sm border border-gray-100 flex justify-between items-center relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-full bg-blue-50 -skew-x-12 translate-x-32 opacity-30"></div>
        <div className="flex items-center space-x-6 relative">
          <div className="p-4 bg-blue-100 rounded-2xl shadow-inner shadow-blue-200"><Layers className="w-10 h-10 text-blue-700" /></div>
          <div>
            <h1 className="text-4xl font-black text-gray-900 tracking-tight">Pricing Tiers Registry</h1>
            <p className="text-gray-500 font-bold mt-1 uppercase tracking-widest text-[10px]">Unified view of all segment logic across the bank</p>
          </div>
        </div>
      </div>

      <div className="bg-amber-50/50 border-l-4 border-amber-400 p-8 rounded-r-3xl shadow-sm flex items-start space-x-6">
        <div className="p-3 bg-amber-100 rounded-xl"><AlertCircle className="w-6 h-6 text-amber-700" /></div>
        <div>
           <p className="text-sm text-amber-900 font-black leading-relaxed uppercase tracking-wide mb-2">Read-Only View Registry</p>
           <p className="text-sm text-amber-800 font-medium leading-relaxed italic max-w-2xl">
              Pricing Tiers are scoped to their parent <strong>Pricing Component</strong>. To modify a tier's amount or rules, please navigate to the
              <Link to="/pricing-components" className="ml-1 text-amber-900 font-black underline decoration-amber-300 hover:decoration-amber-500 transition">Pricing Components</Link> page.
           </p>
        </div>
      </div>

      {error && <div className="bg-red-50 border-l-4 border-red-500 p-6 rounded-r-3xl text-red-700 text-sm font-bold flex items-center shadow-md"><AlertCircle className="w-5 h-5 mr-4" />{error}</div>}

      {loading ? (
        <div className="flex justify-center p-32 bg-white rounded-[3rem] border border-gray-100 shadow-sm"><Loader2 className="w-16 h-16 animate-spin text-blue-600" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-8">
          {tiers.length === 0 ? (
            <div className="col-span-full py-24 text-center text-gray-400 bg-white border-4 border-dashed rounded-[3rem] font-black uppercase tracking-widest text-xs">No segment tiers defined yet.</div>
          ) : (
            tiers.map((tier) => (
              <div key={tier.id || `${tier.componentId}-${tier.code}`} className="bg-white rounded-[2rem] shadow-sm border border-gray-100 p-8 flex flex-col hover:shadow-2xl hover:border-blue-100 transition duration-300 group">
                <div className="flex justify-between items-start mb-8">
                  <div className="bg-blue-50 p-4 rounded-2xl group-hover:bg-blue-600 transition duration-300"><Layers className="w-7 h-7 text-blue-600 group-hover:text-white" /></div>
                  <div className="text-right">
                    <p className="text-4xl font-black text-blue-900 group-hover:text-blue-600 transition duration-300 tracking-tighter">{tier.priceValue.priceAmount}</p>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mt-1">{tier.priceValue.valueType}</p>
                  </div>
                </div>

                <h3 className="text-xl font-black text-gray-900 mb-2 leading-none group-hover:text-blue-900 transition">{tier.name}</h3>
                <p className="text-xs text-gray-400 font-mono mb-8 uppercase tracking-widest">ID: {tier.code}</p>

                <div className="bg-gray-50 rounded-2xl p-4 mb-8 border border-gray-100">
                  <div className="flex items-center space-x-3 mb-1">
                    <Tag className="w-3.5 h-3.5 text-blue-500" />
                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Parent Component</span>
                  </div>
                  <span className="text-sm font-black text-gray-700 block truncate">{tier.componentName}</span>
                </div>

                <div className="flex-1 space-y-3">
                  <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest border-b border-gray-100 pb-2 mb-4">Rule Logic</p>
                  {tier.conditions?.map((c, idx) => (
                    <div key={idx} className="flex items-start text-[11px] text-gray-600 bg-white px-3 py-2 rounded-xl border border-gray-100 shadow-sm">
                      <CheckCircle2 className="w-3.5 h-3.5 mr-3 text-blue-400 flex-shrink-0 mt-0.5" />
                      <div className="flex-1">
                        <span className="font-bold text-gray-400 mr-2 uppercase tracking-tighter">{c.attributeName}</span>
                        <span className="font-black text-blue-600 mx-1">{c.operator}</span>
                        <span className="font-black text-gray-900">{c.attributeValue}</span>
                      </div>
                    </div>
                  ))}
                  {(!tier.conditions || tier.conditions.length === 0) && (
                    <div className="flex items-center text-[10px] text-amber-600 bg-amber-50 px-4 py-3 rounded-2xl border border-amber-100 italic font-bold">
                       <AlertCircle className="w-3 h-3 mr-2" /> Default segment (no rules)
                    </div>
                  )}
                </div>

                <div className="mt-10 pt-6 border-t border-gray-50 flex justify-end">
                   <Link to={`/pricing-components`} className="bg-gray-900 text-white px-5 py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest flex items-center hover:bg-blue-600 transition-all duration-300 group-hover:scale-105">
                      Configure Component <ChevronRight className="w-3.5 h-3.5 ml-2" />
                   </Link>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default PricingTiersPage;
