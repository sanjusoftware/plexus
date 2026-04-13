import React, { useState } from 'react';
import { X, Settings, Play, Download, Loader2, Info } from 'lucide-react';
import { PricingService, ProductPriceRequest, ProductPricingCalculationResult } from '../services/PricingService';
import PlexusSelect from './PlexusSelect';
import { formatComponentLabelWithProRata, getSimulationDateGuidance, getSimulationFieldHelperText } from '../pages/admin/ProductManagementPage.utils';

interface PriceSimulationToolProps {
  isOpen: boolean;
  onClose: () => void;
  defaultProductId?: number;
}

interface SimulationScenario {
  name: string;
  productId: number;
  transactionAmount: number;
  customerSegment: string;
  effectiveDate: string;
  enrollmentDate: string;
}

const PriceSimulationTool: React.FC<PriceSimulationToolProps> = ({ isOpen, onClose, defaultProductId }) => {
  const [scenarios, setScenarios] = useState<SimulationScenario[]>([
    {
      name: 'Scenario 1: Retail Customer',
      productId: defaultProductId || 0,
      transactionAmount: 1000,
      customerSegment: 'RETAIL',
      effectiveDate: new Date().toISOString().split('T')[0],
      enrollmentDate: new Date().toISOString().split('T')[0],
    },
  ]);

  const [results, setResults] = useState<Map<number, ProductPricingCalculationResult>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selectedScenario, setSelectedScenario] = useState(0);

  const addScenario = () => {
    setScenarios([
      ...scenarios,
      {
        name: `Scenario ${scenarios.length + 1}`,
        productId: defaultProductId || 0,
        transactionAmount: 1000,
        customerSegment: 'RETAIL',
        effectiveDate: new Date().toISOString().split('T')[0],
        enrollmentDate: new Date().toISOString().split('T')[0],
      },
    ]);
  };

  const removeScenario = (index: number) => {
    setScenarios(scenarios.filter((_, i) => i !== index));
  };

  const updateScenario = (index: number, updates: Partial<SimulationScenario>) => {
    const updated = [...scenarios];
    updated[index] = { ...updated[index], ...updates };
    setScenarios(updated);
  };

  const runSimulation = async () => {
    setLoading(true);
    setError('');
    const newResults = new Map<number, ProductPricingCalculationResult>();

    try {
      for (let i = 0; i < scenarios.length; i++) {
        const scenario = scenarios[i];
        if (scenario.productId === 0) {
          setError('Please select a product ID for all scenarios');
          break;
        }

        const request: ProductPriceRequest = {
          productId: scenario.productId,
          enrollmentDate: scenario.enrollmentDate,
          customAttributes: {
            TRANSACTION_AMOUNT: scenario.transactionAmount,
            CUSTOMER_SEGMENT: scenario.customerSegment,
            EFFECTIVE_DATE: scenario.effectiveDate,
            ENROLLMENT_DATE: scenario.enrollmentDate,
          }
        };

        const result = await PricingService.calculateProductPrice(request);
        newResults.set(i, result);
      }
      setResults(newResults);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Simulation failed');
    } finally {
      setLoading(false);
    }
  };

  const exportToJSON = () => {
    const exportData = {
      timestamp: new Date().toISOString(),
      scenarios,
      results: Array.from(results.entries()).map(([idx, result]) => ({
        scenarioIndex: idx,
        scenario: scenarios[idx],
        result,
      })),
    };
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `pricing-simulation-${new Date().getTime()}.json`;
    a.click();
  };

  if (!isOpen) return null;

  const currentResult = results.get(selectedScenario);
  const currentScenario = scenarios[selectedScenario];
  const simulationDateGuidance = getSimulationDateGuidance();

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-3xl shadow-2xl max-w-6xl w-full max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="bg-gradient-to-r from-purple-600 to-pink-600 text-white px-8 py-6 flex justify-between items-center">
          <div>
            <h2 className="text-2xl font-black uppercase tracking-tight">Price Simulation Tool</h2>
            <p className="text-purple-100 text-sm font-bold mt-1">Test pricing across multiple scenarios</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-white/20 rounded-lg transition"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Main Content */}
        <div className="overflow-auto flex-1 p-8">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Scenario List */}
            <div className="space-y-4">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-lg font-black text-gray-900 uppercase">Scenarios</h3>
                <button
                  onClick={addScenario}
                  className="bg-purple-600 text-white px-3 py-1.5 rounded-lg text-xs font-black hover:bg-purple-700 transition"
                >
                  + Add
                </button>
              </div>

              <div className="space-y-2">
                {scenarios.map((scenario, idx) => (
                  <div
                    key={idx}
                    onClick={() => setSelectedScenario(idx)}
                    className={`p-4 rounded-xl border-2 cursor-pointer transition ${
                      selectedScenario === idx
                        ? 'bg-purple-50 border-purple-300'
                        : 'bg-gray-50 border-gray-100 hover:border-purple-200'
                    }`}
                  >
                    <div className="flex justify-between items-start mb-2">
                      <p className="font-black text-sm text-gray-900">{scenario.name}</p>
                      {scenarios.length > 1 && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeScenario(idx);
                          }}
                          className="text-red-600 hover:text-red-700"
                        >
                          ×
                        </button>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 font-bold">
                      {scenario.customerSegment} • {PricingService.formatCurrency(scenario.transactionAmount)}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            {/* Scenario Editor */}
            <div className="space-y-6 lg:col-span-2">
              <div className="flex items-center space-x-3 mb-6">
                <div className="p-3 bg-purple-50 rounded-xl">
                  <Settings className="w-5 h-5 text-purple-600" />
                </div>
                <div>
                  <h3 className="text-lg font-black text-gray-900 uppercase">Configure Scenario</h3>
                  <p className="text-xs text-gray-500 font-bold uppercase mt-1">{currentScenario.name}</p>
                </div>
              </div>

              <div className="space-y-4">
                <div className="rounded-2xl border border-purple-200 bg-purple-50 px-4 py-3">
                  <div className="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-purple-700">
                    <Info className="h-3.5 w-3.5" />
                    <span>How dates work</span>
                  </div>
                  <div className="mt-2 space-y-1.5 text-[10px] leading-snug text-purple-900">
                    {simulationDateGuidance.map((entry) => (
                      <p key={entry.label}>
                        <span className="font-black uppercase tracking-wide">{entry.label}:</span> {entry.description}
                      </p>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Scenario Name
                  </label>
                  <input
                    type="text"
                    value={currentScenario.name}
                    onChange={(e) => updateScenario(selectedScenario, { name: e.target.value })}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Product ID
                  </label>
                  <input
                    type="number"
                    value={currentScenario.productId}
                    onChange={(e) => updateScenario(selectedScenario, { productId: parseInt(e.target.value) })}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500"
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Transaction Amount
                    </label>
                    <div className="relative">
                      <span className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 font-bold">$</span>
                      <input
                        type="number"
                        value={currentScenario.transactionAmount}
                        onChange={(e) =>
                          updateScenario(selectedScenario, { transactionAmount: parseFloat(e.target.value) || 0 })
                        }
                        className="w-full border-2 border-gray-100 rounded-xl p-3 pl-8 font-bold text-gray-900 transition focus:border-purple-500"
                        step="100"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                      Customer Segment
                    </label>
                    <PlexusSelect
                      options={[
                        { value: 'RETAIL', label: 'RETAIL' },
                        { value: 'PREMIUM', label: 'PREMIUM' },
                        { value: 'CORPORATE', label: 'CORPORATE' },
                        { value: 'VIP', label: 'VIP' }
                      ]}
                      value={{ value: currentScenario.customerSegment, label: currentScenario.customerSegment }}
                      onChange={(opt) => updateScenario(selectedScenario, { customerSegment: opt ? opt.value : 'RETAIL' })}
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Effective Date
                  </label>
                  <input
                    type="date"
                    value={currentScenario.effectiveDate}
                    onChange={(e) => updateScenario(selectedScenario, { effectiveDate: e.target.value })}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500"
                  />
                  <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">
                    {getSimulationFieldHelperText('EFFECTIVE_DATE')}
                  </p>
                </div>

                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Enrollment Date
                  </label>
                  <input
                    type="date"
                    value={currentScenario.enrollmentDate}
                    onChange={(e) => updateScenario(selectedScenario, { enrollmentDate: e.target.value })}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500"
                  />
                  <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">
                    {getSimulationFieldHelperText('ENROLLMENT_DATE')}
                  </p>
                </div>
              </div>

              {/* Run Button */}
              <button
                onClick={runSimulation}
                disabled={loading}
                className="w-full bg-purple-600 text-white py-3 rounded-xl font-black uppercase tracking-widest hover:bg-purple-700 disabled:opacity-50 transition flex items-center justify-center"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                    Running...
                  </>
                ) : (
                  <>
                    <Play className="w-5 h-5 mr-2" />
                    Run Simulation
                  </>
                )}
              </button>

              {/* Error */}
              {error && (
                <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl text-red-700 text-sm font-bold">
                  {error}
                </div>
              )}
            </div>
          </div>

          {/* Results */}
          {currentResult && (
            <div className="mt-8 pt-8 border-t border-gray-200 space-y-6">
              <div className="flex justify-between items-center">
                <h3 className="text-lg font-black text-gray-900 uppercase">Results</h3>
                <div className="flex space-x-3">
                  <button
                    onClick={exportToJSON}
                    className="flex items-center space-x-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg font-bold hover:bg-gray-200 transition"
                  >
                    <Download className="w-4 h-4" />
                    <span>Export</span>
                  </button>
                </div>
              </div>

              {/* Final Price */}
              <div className="bg-gradient-to-r from-purple-600 to-pink-600 rounded-2xl p-8 text-white">
                <p className="text-[10px] font-black uppercase tracking-widest text-purple-100 mb-2">
                  Final Chargeable Price
                </p>
                <div className="flex items-baseline space-x-2">
                  <span className="text-5xl font-black">{PricingService.formatCurrency(currentResult.finalChargeablePrice)}</span>
                  <span className="text-lg font-bold text-purple-100">USD</span>
                </div>
              </div>

              {/* Component Breakdown Table */}
              {currentResult.componentBreakdown && currentResult.componentBreakdown.length > 0 && (
                <div>
                  <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-4">
                    Component Breakdown
                  </h4>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-gray-50 border-b-2 border-gray-200">
                          <th className="px-4 py-3 text-left font-black text-gray-700 text-[10px] uppercase">Component</th>
                          <th className="px-4 py-3 text-left font-black text-gray-700 text-[10px] uppercase">Type</th>
                          <th className="px-4 py-3 text-right font-black text-gray-700 text-[10px] uppercase">Raw Value</th>
                          <th className="px-4 py-3 text-right font-black text-gray-700 text-[10px] uppercase">Calculated</th>
                          <th className="px-4 py-3 text-left font-black text-gray-700 text-[10px] uppercase">Source</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {currentResult.componentBreakdown.map((component, idx) => (
                          <tr key={idx} className="hover:bg-gray-50 transition">
                            <td className="px-4 py-3 font-bold text-gray-900">{formatComponentLabelWithProRata(component.componentCode, component)}</td>
                            <td className="px-4 py-3 text-xs font-bold text-gray-600">
                              {PricingService.getValueTypeLabel(component.valueType)}
                            </td>
                            <td className="px-4 py-3 text-right font-bold text-gray-900">
                              {component.rawValue}
                              {PricingService.isPercentageType(component.valueType) ? '%' : ''}
                            </td>
                            <td className={`px-4 py-3 text-right font-black ${
                              PricingService.isFeeType(component.valueType)
                                ? 'text-red-600'
                                : 'text-green-600'
                            }`}>
                              {PricingService.isFeeType(component.valueType) ? '+' : '-'}
                              {PricingService.formatCurrency(Math.abs(component.calculatedAmount))}
                            </td>
                            <td className="px-4 py-3 text-xs font-bold text-gray-600">
                              {component.sourceType}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PriceSimulationTool;

