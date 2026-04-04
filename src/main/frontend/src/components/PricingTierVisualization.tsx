import React, { useState } from 'react';
import { ChevronDown, ChevronUp, Layers, AlertCircle, Info } from 'lucide-react';

interface PricingTier {
  id: number;
  code: string;
  name: string;
  description?: string;
  minValue?: number;
  maxValue?: number;
  conditions?: string;
  valueType?: string;
  rawValue?: number;
}

interface PricingTierVisualizationProps {
  tiers: PricingTier[];
  componentCode?: string;
  componentName?: string;
  isRulesEngine?: boolean;
  onTierSelect?: (tier: PricingTier) => void;
}

const PricingTierVisualization: React.FC<PricingTierVisualizationProps> = ({
  tiers,
  componentCode = 'PRICING_COMPONENT',
  componentName = 'Pricing Component',
  isRulesEngine = false,
  onTierSelect,
}) => {
  const [expandedTier, setExpandedTier] = useState<number | null>(null);
  const [hoveredTier, setHoveredTier] = useState<number | null>(null);

  if (!tiers || tiers.length === 0) {
    return (
      <div className="bg-amber-50 border-2 border-amber-200 rounded-2xl p-8 flex items-center space-x-4">
        <AlertCircle className="w-6 h-6 text-amber-600 flex-shrink-0" />
        <div>
          <p className="font-black text-amber-900">No Pricing Tiers Defined</p>
          <p className="text-sm text-amber-700 mt-1">Create tiers to enable dynamic pricing rules</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center space-x-3">
        <div className="p-3 bg-indigo-50 rounded-xl">
          <Layers className="w-5 h-5 text-indigo-600" />
        </div>
        <div>
          <h3 className="text-lg font-black text-gray-900 uppercase">Pricing Tier Structure</h3>
          <p className="text-xs text-gray-500 font-bold uppercase mt-1">
            {componentCode} • {componentName}
            {isRulesEngine && <span className="ml-2 text-amber-600 font-black">[Rules Engine]</span>}
          </p>
        </div>
      </div>

      {/* Tier Info Box */}
      <div className="bg-blue-50 border-l-4 border-blue-400 rounded-r-lg p-4 flex items-start space-x-3">
        <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
        <div>
          <p className="text-sm font-bold text-blue-900 uppercase tracking-wide">
            {isRulesEngine
              ? 'Dynamic Tiers: Matched based on rules engine conditions'
              : 'Static Configuration: Single tier applies to all scenarios'}
          </p>
          <p className="text-xs text-blue-700 mt-1">
            {isRulesEngine
              ? 'Drools rules evaluate customer segment, transaction amount, and custom attributes to select the applicable tier'
              : 'This is a fixed pricing tier with no conditional logic'}
          </p>
        </div>
      </div>

      {/* Tiers */}
      <div className="space-y-3">
        {tiers.map((tier, idx) => (
          <div key={tier.id || idx} className="overflow-hidden">
            {/* Tier Header */}
            <button
              onClick={() => setExpandedTier(expandedTier === idx ? null : idx)}
              onMouseEnter={() => setHoveredTier(idx)}
              onMouseLeave={() => setHoveredTier(null)}
              className={`w-full px-6 py-4 flex justify-between items-center transition rounded-xl border-2 ${
                expandedTier === idx
                  ? 'bg-indigo-50 border-indigo-300'
                  : hoveredTier === idx
                  ? 'bg-gray-50 border-gray-200'
                  : 'bg-white border-gray-100'
              }`}
            >
              <div className="flex items-center space-x-4 flex-1">
                {/* Tier Number */}
                <div className="w-10 h-10 rounded-full bg-indigo-600 text-white flex items-center justify-center font-black text-sm flex-shrink-0">
                  T{idx + 1}
                </div>

                {/* Tier Info */}
                <div className="text-left flex-1">
                  <p className="font-black text-gray-900">{tier.name || tier.code}</p>
                  <p className="text-xs text-gray-500 font-bold mt-1">
                    {tier.minValue !== undefined && tier.maxValue !== undefined
                      ? `${tier.minValue} - ${tier.maxValue}`
                      : tier.minValue !== undefined
                      ? `≥ ${tier.minValue}`
                      : tier.maxValue !== undefined
                      ? `≤ ${tier.maxValue}`
                      : 'Default Tier'}
                  </p>
                </div>

                {/* Value Badge */}
                {tier.rawValue !== undefined && (
                  <div className="text-right">
                    <p className="font-black text-lg text-indigo-600">{tier.rawValue}</p>
                    <p className="text-xs text-gray-500 font-bold">
                      {tier.valueType || 'FEE_ABSOLUTE'}
                    </p>
                  </div>
                )}
              </div>

              {/* Expand Icon */}
              <div className="ml-4">
                {expandedTier === idx ? (
                  <ChevronUp className="w-5 h-5 text-indigo-600" />
                ) : (
                  <ChevronDown className="w-5 h-5 text-gray-400" />
                )}
              </div>
            </button>

            {/* Tier Details */}
            {expandedTier === idx && (
              <div className="bg-indigo-50/50 border-l-2 border-r-2 border-b-2 border-indigo-200 p-6 space-y-4">
                {/* Description */}
                {tier.description && (
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Description
                    </p>
                    <p className="text-sm text-gray-700 bg-white p-3 rounded-lg border border-indigo-100">
                      {tier.description}
                    </p>
                  </div>
                )}

                {/* Range Info */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Minimum Value
                    </p>
                    <p className="text-sm font-bold text-gray-900 bg-white p-3 rounded-lg border border-indigo-100">
                      {tier.minValue !== undefined ? tier.minValue : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Maximum Value
                    </p>
                    <p className="text-sm font-bold text-gray-900 bg-white p-3 rounded-lg border border-indigo-100">
                      {tier.maxValue !== undefined ? tier.maxValue : '—'}
                    </p>
                  </div>
                </div>

                {/* Conditions */}
                {tier.conditions && (
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Matching Conditions
                    </p>
                    <div className="bg-white p-4 rounded-lg border border-indigo-100 font-mono text-xs text-gray-700 overflow-x-auto">
                      <p className="whitespace-pre-wrap break-words">{tier.conditions}</p>
                    </div>
                    <p className="text-xs text-gray-500 font-bold mt-2 italic">
                      This tier is applied when these conditions are matched by the rules engine
                    </p>
                  </div>
                )}

                {/* Pricing Details */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Value
                    </p>
                    <p className="text-lg font-black text-indigo-600 bg-white p-3 rounded-lg border border-indigo-100">
                      {tier.rawValue !== undefined ? tier.rawValue : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Type
                    </p>
                    <p className="text-sm font-bold text-gray-900 bg-white p-3 rounded-lg border border-indigo-100">
                      {tier.valueType || 'FEE_ABSOLUTE'}
                    </p>
                  </div>
                </div>

                {/* Action Button */}
                {onTierSelect && (
                  <button
                    onClick={() => onTierSelect(tier)}
                    className="w-full mt-4 bg-indigo-600 text-white py-2 rounded-lg font-bold uppercase text-xs hover:bg-indigo-700 transition"
                  >
                    Select This Tier
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Tier Matching Flow Diagram */}
      {isRulesEngine && (
        <div className="bg-gradient-to-r from-indigo-50 to-purple-50 rounded-2xl p-6 border-2 border-indigo-200">
          <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-4">
            Tier Matching Logic Flow
          </p>
          <div className="space-y-2 font-mono text-xs text-gray-700">
            <div className="flex items-center space-x-3">
              <div className="bg-indigo-600 text-white px-2 py-1 rounded font-bold">1</div>
              <p>Customer initiates transaction</p>
            </div>
            <div className="flex items-center space-x-3 ml-6">
              <div className="w-0.5 h-4 bg-gray-400"></div>
            </div>
            <div className="flex items-center space-x-3">
              <div className="bg-indigo-600 text-white px-2 py-1 rounded font-bold">2</div>
              <p>Rules engine evaluates all tier conditions</p>
            </div>
            <div className="flex items-center space-x-3 ml-6">
              <div className="w-0.5 h-4 bg-gray-400"></div>
            </div>
            <div className="flex items-center space-x-3">
              <div className="bg-indigo-600 text-white px-2 py-1 rounded font-bold">3</div>
              <p>First matching tier is selected</p>
            </div>
            <div className="flex items-center space-x-3 ml-6">
              <div className="w-0.5 h-4 bg-gray-400"></div>
            </div>
            <div className="flex items-center space-x-3">
              <div className="bg-green-600 text-white px-2 py-1 rounded font-bold">✓</div>
              <p>Price calculated using tier's value and type</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PricingTierVisualization;

