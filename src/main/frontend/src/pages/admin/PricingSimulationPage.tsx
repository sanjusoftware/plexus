import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Zap, Settings, Play, Download, Loader2, Plus, Trash2, RefreshCw } from 'lucide-react';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';
import { PricingService, ProductPriceRequest, ProductPricingCalculationResult, PricingMetadata } from '../../services/PricingService';
import PlexusSelect from '../../components/PlexusSelect';
import { formatComponentLabelWithProRata, formatPercentageBaseHint, getSimulationFieldHelperText } from './ProductManagementPage.utils';
import { useAuth } from '../../context/AuthContext';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { useSystemPricingKeys } from '../../hooks/useSystemPricingKeys';

interface Product {
  id: number;
  code: string;
  name: string;
  status: string;
}

interface SimulationScenario {
  name: string;
  productId: number;
  inputs: Record<string, any>;
}

const PricingSimulationPage = () => {
  const { setToast } = useAuth();
  const signal = useAbortSignal();
  const {
    keys,
    isHiddenSystemKey,
    isSystemAmountKey,
    isSystemDateKey,
    isSystemCustomerSegmentKey,
    hasSystemPricingKey,
  } = useSystemPricingKeys();

  const [products, setProducts] = useState<Product[]>([]);
  const [metadata, setMetadata] = useState<PricingMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [simulating, setSimulating] = useState(false);
  const [selectedScenarioIdx, setSelectedScenarioIdx] = useState(0);
  const [results, setResults] = useState<Map<number, ProductPricingCalculationResult>>(new Map());
  const [error, setError] = useState('');

  const [scenarios, setScenarios] = useState<SimulationScenario[]>([
    {
      name: 'Scenario 1: Retail Customer',
      productId: 0,
      inputs: {},
    },
  ]);

  const fetchInitialData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const [productsRes, metadataRes] = await Promise.all([
        axios.get('/api/v1/products', { signal: abortSignal }),
        PricingService.getPricingMetadata(abortSignal).catch(() => []),
      ]);

      const allProducts: Product[] = productsRes.data.content || [];
      const filteredSortedProducts = allProducts
        .filter(p => p.status === 'ACTIVE' || p.status === 'DRAFT')
        .sort((a, b) => a.name.localeCompare(b.name));
      setProducts(filteredSortedProducts);

      const dynamicFields = (metadataRes || [])
        .filter((m: PricingMetadata) => (m.sourceType || 'CUSTOM_ATTRIBUTE') === 'CUSTOM_ATTRIBUTE')
        .filter((m: PricingMetadata) => !isHiddenSystemKey(m.attributeKey || ''));
      setMetadata(dynamicFields);

      // Initialize inputs for the first scenario if not already set
      setScenarios(prev => {
        const next = [...prev];
        if (next[0]) {
          const defaultInputs: Record<string, any> = {};
          dynamicFields.forEach((m: PricingMetadata) => {
            const key = m.attributeKey;
            switch ((m.dataType || '').toUpperCase()) {
              case 'DECIMAL':
              case 'INTEGER':
              case 'LONG':
                defaultInputs[key] = isSystemAmountKey(key) ? 1000 : 0;
                break;
              case 'BOOLEAN':
                defaultInputs[key] = false;
                break;
              case 'DATE':
                defaultInputs[key] = new Date().toISOString().split('T')[0];
                break;
              default:
                defaultInputs[key] = isSystemCustomerSegmentKey(key) ? 'RETAIL' : '';
            }
          });
          next[0].inputs = defaultInputs;
        }
        return next;
      });

    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setToast({ message: 'Failed to fetch simulation data.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast, isHiddenSystemKey, isSystemAmountKey, isSystemCustomerSegmentKey]);

  useEffect(() => {
    fetchInitialData(signal);
  }, [fetchInitialData, signal]);

  const addScenario = () => {
    const defaultInputs: Record<string, any> = {};
    metadata.forEach((m: PricingMetadata) => {
      const key = m.attributeKey;
      switch ((m.dataType || '').toUpperCase()) {
        case 'DECIMAL':
        case 'INTEGER':
        case 'LONG':
          defaultInputs[key] = isSystemAmountKey(key) ? 1000 : 0;
          break;
        case 'BOOLEAN':
          defaultInputs[key] = false;
          break;
        case 'DATE':
          defaultInputs[key] = new Date().toISOString().split('T')[0];
          break;
        default:
          defaultInputs[key] = isSystemCustomerSegmentKey(key) ? 'RETAIL' : '';
      }
    });

    setScenarios([
      ...scenarios,
      {
        name: `Scenario ${scenarios.length + 1}`,
        productId: 0,
        inputs: defaultInputs,
      },
    ]);
    setSelectedScenarioIdx(scenarios.length);
  };

  const removeScenario = (index: number) => {
    const newScenarios = scenarios.filter((_, i) => i !== index);
    setScenarios(newScenarios);
    if (selectedScenarioIdx >= newScenarios.length) {
      setSelectedScenarioIdx(Math.max(0, newScenarios.length - 1));
    }
    const newResults = new Map(results);
    newResults.delete(index);
    setResults(newResults);
  };

  const updateScenario = (index: number, updates: Partial<SimulationScenario>) => {
    const updated = [...scenarios];
    updated[index] = { ...updated[index], ...updates };
    setScenarios(updated);
  };

  const updateScenarioInput = (index: number, key: string, value: any) => {
    const updated = [...scenarios];
    updated[index].inputs = { ...updated[index].inputs, [key]: value };
    setScenarios(updated);
  };

  const runSimulation = async () => {
    setSimulating(true);
    setError('');
    const newResults = new Map(results);

    try {
      for (let i = 0; i < scenarios.length; i++) {
        const scenario = scenarios[i];
        if (scenario.productId === 0) {
          setError(`Please select a product for "${scenario.name}"`);
          setSimulating(false);
          return;
        }

        const effectiveDateKey = metadata.find(m => isSystemDateKey(m.attributeKey))?.attributeKey || keys.EFFECTIVE_DATE;
        const enrollmentDateKey = metadata.find(m => m.attributeKey.toUpperCase() === 'ENROLLMENT_DATE')?.attributeKey || 'ENROLLMENT_DATE';

        const request: ProductPriceRequest = {
          productId: scenario.productId,
          enrollmentDate: scenario.inputs[enrollmentDateKey] || new Date().toISOString().split('T')[0],
          customAttributes: { ...scenario.inputs }
        };

        // Ensure effective date is set
        if (!request.customAttributes[effectiveDateKey]) {
          request.customAttributes[effectiveDateKey] = new Date().toISOString().split('T')[0];
        }

        const result = await PricingService.calculateProductPrice(request);
        newResults.set(i, result);
      }
      setResults(newResults);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Simulation failed');
    } finally {
      setSimulating(false);
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

  const parseInputValue = (dataType: string, rawValue: any) => {
    const normalizedType = (dataType || '').toUpperCase();
    if (rawValue === '' || rawValue === undefined || rawValue === null) return undefined;

    if (normalizedType === 'DECIMAL') return Number(rawValue);
    if (normalizedType === 'INTEGER' || normalizedType === 'LONG') return parseInt(rawValue, 10);
    if (normalizedType === 'BOOLEAN') return rawValue === true || rawValue === 'true';
    return rawValue;
  };

  const renderDynamicField = (meta: PricingMetadata, scenarioIdx: number) => {
    const key = meta.attributeKey;
    const value = scenarios[scenarioIdx].inputs[key] ?? '';
    const dataType = (meta.dataType || '').toUpperCase();
    const label = (meta.displayName || key).toUpperCase();
    const helperText = getSimulationFieldHelperText(key);

    if (dataType === 'BOOLEAN') {
      return (
        <div key={key}>
          <label className="flex items-center space-x-3 cursor-pointer h-full pt-4">
            <div className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus-within:outline-none focus-within:ring-2 focus-within:ring-purple-500 focus-within:ring-offset-2 ${ (value === true || value === 'true') ? 'bg-purple-600' : 'bg-gray-200' }`}>
              <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow-lg transition-transform ${ (value === true || value === 'true') ? 'translate-x-6' : 'translate-x-1' }`} />
              <input
                type="checkbox"
                checked={value === true || value === 'true'}
                onChange={(e) => updateScenarioInput(scenarioIdx, key, e.target.checked)}
                className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
              />
            </div>
            <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{label}</span>
          </label>
          {helperText && <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">{helperText}</p>}
        </div>
      );
    }

    if (isSystemCustomerSegmentKey(key)) {
      return (
        <div key={key}>
          <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">{label}</label>
          <PlexusSelect
            options={[
              { value: 'RETAIL', label: 'RETAIL' },
              { value: 'PREMIUM', label: 'PREMIUM' },
              { value: 'CORPORATE', label: 'CORPORATE' },
              { value: 'VIP', label: 'VIP' }
            ]}
            value={{ value: String(value || 'RETAIL'), label: String(value || 'RETAIL') }}
            onChange={(opt) => updateScenarioInput(scenarioIdx, key, opt ? opt.value : 'RETAIL')}
          />
        </div>
      );
    }

    const inputType = dataType === 'DATE'
      ? 'date'
      : (dataType === 'DECIMAL' || dataType === 'INTEGER' || dataType === 'LONG')
        ? 'number'
        : 'text';

    return (
      <div key={key}>
        <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">{label}</label>
        <div className="relative">
          {isSystemAmountKey(key) && <span className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 font-bold">$</span>}
          <input
            type={inputType}
            className={`w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500 ${isSystemAmountKey(key) ? 'pl-8' : ''}`}
            value={value}
            onChange={(e) => updateScenarioInput(scenarioIdx, key, e.target.value)}
          />
        </div>
        {helperText && <p className="mt-1 text-[10px] font-medium leading-snug text-gray-500">{helperText}</p>}
      </div>
    );
  };

  const productOptions = products.map(p => ({
    value: p.id.toString(),
    label: `${p.name} (${p.code})`,
    code: p.code
  }));

  const currentScenario = scenarios[selectedScenarioIdx];
  const currentResult = results.get(selectedScenarioIdx);

  if (loading) {
    return (
      <AdminPage>
        <div className="flex justify-center p-20">
          <Loader2 className="h-12 w-12 animate-spin text-purple-600" />
        </div>
      </AdminPage>
    );
  }

  return (
    <AdminPage>
      <AdminPageHeader
        icon={Zap}
        title="Price Simulation Tool"
        description="Test and compare pricing across multiple scenarios and products."
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Scenario List */}
        <div className="space-y-4">
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-lg font-black text-gray-900 uppercase">Scenarios</h3>
            <button
              onClick={addScenario}
              className="bg-purple-600 text-white px-3 py-1.5 rounded-lg text-xs font-black hover:bg-purple-700 transition flex items-center gap-1.5"
            >
              <Plus className="w-3 h-3" /> Add
            </button>
          </div>

          <div className="space-y-2">
            {scenarios.map((scenario, idx) => (
              <div
                key={idx}
                onClick={() => setSelectedScenarioIdx(idx)}
                className={`p-4 rounded-xl border-2 cursor-pointer transition ${
                  selectedScenarioIdx === idx
                    ? 'bg-purple-50 border-purple-300'
                    : 'bg-white border-gray-100 hover:border-purple-200'
                }`}
              >
                <div className="flex justify-between items-start mb-2">
                  <p className="font-black text-sm text-gray-900 truncate pr-2">{scenario.name}</p>
                  {scenarios.length > 1 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        removeScenario(idx);
                      }}
                      className="text-gray-400 hover:text-red-600 transition"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
                <div className="flex items-center justify-between">
                  <p className="text-[10px] text-gray-500 font-bold uppercase tracking-tight">
                    {scenario.inputs[keys.CUSTOMER_SEGMENT] || 'RETAIL'} • {PricingService.formatCurrency(scenario.inputs[keys.TRANSACTION_AMOUNT] || 0)}
                  </p>
                  {results.has(idx) && (
                    <span className="text-[10px] font-black text-purple-600 bg-purple-100 px-1.5 py-0.5 rounded">
                      {PricingService.formatCurrency(results.get(idx)!.finalChargeablePrice)}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Scenario Editor */}
        <div className="space-y-6 lg:col-span-2">
          <div className="bg-white rounded-2xl border border-gray-100 p-8 shadow-sm">
            <div className="flex items-center space-x-3 mb-8">
              <div className="p-3 bg-purple-50 rounded-xl">
                <Settings className="w-5 h-5 text-purple-600" />
              </div>
              <div>
                <h3 className="text-lg font-black text-gray-900 uppercase">Configure Scenario</h3>
                <p className="text-xs text-gray-500 font-bold uppercase mt-1 tracking-wider">{currentScenario.name}</p>
              </div>
            </div>

            <div className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Scenario Name
                  </label>
                  <input
                    type="text"
                    value={currentScenario.name}
                    onChange={(e) => updateScenario(selectedScenarioIdx, { name: e.target.value })}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 font-bold text-gray-900 transition focus:border-purple-500"
                    placeholder="Enter scenario name..."
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2">
                    Select Product
                  </label>
                  <PlexusSelect
                    options={productOptions}
                    value={productOptions.find(opt => opt.value === currentScenario.productId.toString()) || null}
                    onChange={(opt) => updateScenario(selectedScenarioIdx, { productId: opt ? parseInt(opt.value) : 0 })}
                    placeholder="Search and select product..."
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {metadata.map(meta => renderDynamicField(meta, selectedScenarioIdx))}
              </div>

              <div className="pt-4 flex flex-col sm:flex-row gap-4">
                <button
                  onClick={runSimulation}
                  disabled={simulating}
                  className="flex-1 bg-purple-600 text-white py-4 rounded-xl font-black uppercase tracking-widest hover:bg-purple-700 disabled:opacity-50 transition flex items-center justify-center shadow-lg shadow-purple-100"
                >
                  {simulating ? (
                    <>
                      <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                      Running Simulation...
                    </>
                  ) : (
                    <>
                      <Play className="w-5 h-5 mr-2" />
                      Run Simulation
                    </>
                  )}
                </button>
                {results.size > 0 && (
                  <button
                    onClick={exportToJSON}
                    className="px-6 py-4 border-2 border-gray-100 text-gray-600 rounded-xl font-black uppercase tracking-widest hover:bg-gray-50 transition flex items-center justify-center"
                  >
                    <Download className="w-5 h-5 mr-2" />
                    Export
                  </button>
                )}
              </div>

              {error && (
                <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl text-red-700 text-sm font-bold">
                  {error}
                </div>
              )}
            </div>
          </div>

          {/* Results Area */}
          {currentResult ? (
            <div className="animate-in fade-in slide-in-from-top-4 duration-500 space-y-6">
              <div className="bg-gradient-to-r from-purple-600 to-pink-600 rounded-2xl p-8 text-white shadow-xl shadow-purple-200">
                <div className="flex justify-between items-start">
                  <div>
                    <p className="text-[10px] font-black uppercase tracking-widest text-purple-100 mb-2">
                      Final Chargeable Price
                    </p>
                    <div className="flex items-baseline space-x-2">
                      <span className="text-5xl font-black">{PricingService.formatCurrency(currentResult.finalChargeablePrice)}</span>
                      <span className="text-lg font-bold text-purple-100 uppercase tracking-tighter">USD / CYCLE</span>
                    </div>
                  </div>
                  <div className="p-3 bg-white/20 rounded-2xl backdrop-blur-sm">
                    <Zap className="w-8 h-8 text-white" />
                  </div>
                </div>
              </div>

              {/* Breakdown */}
              <div className="bg-white rounded-2xl border border-gray-100 p-8 shadow-sm">
                <div className="flex items-center justify-between mb-6">
                  <h3 className="text-lg font-black text-gray-900 uppercase">Calculation Breakdown</h3>
                  <button
                    onClick={() => runSimulation()}
                    className="p-2 text-purple-600 hover:bg-purple-50 rounded-lg transition"
                    title="Refresh"
                  >
                    <RefreshCw className={`w-5 h-5 ${simulating ? 'animate-spin' : ''}`} />
                  </button>
                </div>

                {currentResult.componentBreakdown && currentResult.componentBreakdown.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-gray-50 border-b-2 border-gray-100">
                          <th className="px-4 py-4 text-left font-black text-gray-700 text-[10px] uppercase tracking-widest">Component</th>
                          <th className="px-4 py-4 text-left font-black text-gray-700 text-[10px] uppercase tracking-widest">Type</th>
                          <th className="px-4 py-4 text-right font-black text-gray-700 text-[10px] uppercase tracking-widest">Raw Value</th>
                          <th className="px-4 py-4 text-right font-black text-gray-700 text-[10px] uppercase tracking-widest">Calculated</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-50">
                        {currentResult.componentBreakdown.map((component, idx) => (
                          <tr key={idx} className="hover:bg-gray-50/50 transition">
                            <td className="px-4 py-4 font-bold text-gray-900">
                              <div className="flex flex-col">
                                <span className="leading-tight">{formatComponentLabelWithProRata(component.componentCode, component)}</span>
                                <span className="text-[9px] font-mono text-gray-400 mt-1 uppercase tracking-tighter">{component.sourceType}</span>
                              </div>
                            </td>
                            <td className="px-4 py-4">
                              <span className="text-[10px] font-black text-gray-500 uppercase bg-gray-100 px-2 py-1 rounded">
                                {PricingService.getValueTypeLabel(component.valueType)}
                              </span>
                            </td>
                            <td className="px-4 py-4 text-right font-bold text-gray-900">
                              {component.rawValue}
                              {PricingService.isPercentageType(component.valueType) ? '%' : ''}
                            </td>
                            <td className={`px-4 py-4 text-right font-black ${
                              PricingService.isFeeType(component.valueType)
                                ? 'text-red-600'
                                : 'text-green-600'
                            }`}>
                              {PricingService.isFeeType(component.valueType) ? '+' : '-'}
                              {PricingService.formatCurrency(Math.abs(component.calculatedAmount))}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="py-12 text-center text-gray-400 font-bold uppercase tracking-widest text-[10px]">
                    No breakdown details available for this run.
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-2xl border-2 border-dashed border-gray-200 p-20 flex flex-col items-center justify-center text-center">
              <div className="p-4 bg-gray-50 rounded-full mb-4">
                <Play className="w-8 h-8 text-gray-300" />
              </div>
              <h4 className="text-gray-900 font-black uppercase tracking-tight mb-2">Ready to Simulate</h4>
              <p className="text-gray-500 text-sm max-w-sm">
                Select a product and configure your scenario parameters above, then click <b>Run Simulation</b> to see the results.
              </p>
            </div>
          )}
        </div>
      </div>
    </AdminPage>
  );
};

export default PricingSimulationPage;
