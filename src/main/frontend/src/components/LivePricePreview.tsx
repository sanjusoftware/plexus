import React, { useState, useEffect, useCallback } from 'react';
import { ChevronDown, ChevronUp, DollarSign, AlertCircle, Loader2, TrendingUp } from 'lucide-react';
import { PricingService, ProductPriceRequest, ProductPricingCalculationResult } from '../services/PricingService';
import { useAbortSignal } from '../hooks/useAbortSignal';
import axios from 'axios';

interface LivePricePreviewProps {
  productId?: number;
  currentFormData?: any; // From ProductFormPage
}

const LivePricePreview: React.FC<LivePricePreviewProps> = ({ productId, currentFormData }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<ProductPricingCalculationResult | null>(null);

  // Preview input parameters
  const [transactionAmount, setTransactionAmount] = useState<number>(1000);
  const [customerSegment, setCustomerSegment] = useState<string>('RETAIL');
  const [enrollmentDate, setEnrollmentDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const signal = useAbortSignal();

  // Trigger calculation when inputs change
  const calculatePreview = useCallback(async (abortSignal: AbortSignal) => {
    if (!productId) {
      setError('Product ID required for price calculation');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const request: ProductPriceRequest = {
        productId,
        transactionAmount,
        customerSegment,
        effectiveDate: new Date().toISOString().split('T')[0],
        enrollmentDate,
      };

      const pricing = await PricingService.calculateProductPrice(request, abortSignal);
      setResult(pricing);
    } catch (err: any) {
      if (axios.isCancel(err)) return;
      setError(err.response?.data?.message || 'Failed to calculate price. Product may not be activated.');
      setResult(null);
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [productId, transactionAmount, customerSegment, enrollmentDate]);

  useEffect(() => {
    if (productId) {
      calculatePreview(signal);
    }
  }, [productId, calculatePreview, signal]);

  return (
    <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-2xl border-2 border-blue-200 overflow-hidden mt-8">
      {/* Header */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full px-8 py-6 flex justify-between items-center hover:bg-blue-100/50 transition group"
      >
        <div className="flex items-center space-x-4">
          <div className="p-3 bg-white rounded-xl shadow-sm group-hover:bg-blue-600 group-hover:text-white transition">
            <DollarSign className="w-6 h-6 text-blue-600 group-hover:text-white" />
          </div>
          <div className="text-left">
            <h3 className="text-lg font-black text-gray-900 uppercase tracking-tight">Live Price Preview</h3>
            <p className="text-xs text-gray-500 font-bold uppercase tracking-widest mt-1">
              Test pricing with different scenarios
            </p>
          </div>
        </div>
        {isExpanded ? (
          <ChevronUp className="w-6 h-6 text-blue-600" />
        ) : (
          <ChevronDown className="w-6 h-6 text-blue-600" />
        )}
      </button>

      {/* Content */}
      {isExpanded && (
        <div className="border-t border-blue-200 bg-white">
          <div className="p-8 space-y-8">
            {/* Input Controls */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">
                  Transaction Amount
                </label>
                <div className="relative">
                  <span className="absolute left-4 top-1/2 transform -translate-y-1/2 text-gray-500 font-bold">$</span>
                  <input
                    type="number"
                    value={transactionAmount}
                    onChange={(e) => setTransactionAmount(parseFloat(e.target.value) || 0)}
                    className="w-full border-2 border-gray-100 rounded-xl p-3 pl-8 font-black text-gray-900 transition focus:border-blue-500 focus:ring-blue-200"
                    placeholder="1000.00"
                    step="100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">
                  Customer Segment
                </label>
                <select
                  value={customerSegment}
                  onChange={(e) => setCustomerSegment(e.target.value)}
                  className="w-full border-2 border-gray-100 rounded-xl p-3 font-black text-gray-900 transition focus:border-blue-500 focus:ring-blue-200"
                >
                  <option value="RETAIL">Retail</option>
                  <option value="PREMIUM">Premium</option>
                  <option value="CORPORATE">Corporate</option>
                  <option value="VIP">VIP</option>
                </select>
              </div>

              <div>
                <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3">
                  Enrollment Date
                </label>
                <input
                  type="date"
                  value={enrollmentDate}
                  onChange={(e) => setEnrollmentDate(e.target.value)}
                  className="w-full border-2 border-gray-100 rounded-xl p-3 font-black text-gray-900 transition focus:border-blue-500 focus:ring-blue-200"
                />
              </div>
            </div>

            {/* Error Message */}
            {error && (
              <div className="p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700">
                <AlertCircle className="w-5 h-5 mr-3 flex-shrink-0" />
                <p className="text-sm font-bold">{error}</p>
              </div>
            )}

            {/* Loading State */}
            {loading && (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-8 h-8 animate-spin text-blue-600 mr-3" />
                <p className="text-gray-600 font-bold">Calculating price...</p>
              </div>
            )}

            {/* Results */}
            {result && !loading && (
              <div className="space-y-6">
                {/* Final Price */}
                <div className="bg-gradient-to-r from-blue-600 to-indigo-600 rounded-2xl p-8 text-white">
                  <p className="text-[10px] font-black uppercase tracking-widest text-blue-100 mb-2">
                    Final Chargeable Price
                  </p>
                  <div className="flex items-baseline space-x-2">
                    <span className="text-5xl font-black">{PricingService.formatCurrency(result.finalChargeablePrice)}</span>
                    <span className="text-lg font-bold text-blue-100">USD</span>
                  </div>
                </div>

                {/* Component Breakdown */}
                {result.componentBreakdown && result.componentBreakdown.length > 0 && (
                  <div>
                    <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-4 flex items-center">
                      <TrendingUp className="w-4 h-4 mr-2 text-blue-600" /> Component Breakdown
                    </h4>
                    <div className="space-y-3">
                      {result.componentBreakdown.map((component, idx) => (
                        <div
                          key={idx}
                          className={`p-4 rounded-xl border-2 flex justify-between items-center ${
                            PricingService.isFeeType(component.valueType)
                              ? 'bg-red-50 border-red-100'
                              : 'bg-green-50 border-green-100'
                          }`}
                        >
                          <div className="flex-1">
                            <p className="font-black text-gray-900 text-sm">{component.componentCode}</p>
                            <p className="text-xs text-gray-500 font-bold mt-1">
                              {PricingService.getValueTypeLabel(component.valueType)}
                              {component.sourceType === 'RULES_ENGINE' && (
                                <span className="ml-2 text-amber-600 font-black">[Rules Engine]</span>
                              )}
                              {component.proRataApplicable && (
                                <span className="ml-2 text-blue-600 font-black">[Pro-rata]</span>
                              )}
                            </p>
                          </div>
                          <div className="text-right">
                            <p className={`text-lg font-black ${
                              PricingService.isFeeType(component.valueType)
                                ? 'text-red-600'
                                : 'text-green-600'
                            }`}>
                              {PricingService.isFeeType(component.valueType) ? '+' : '-'}
                              {PricingService.formatCurrency(Math.abs(component.calculatedAmount))}
                            </p>
                            <p className="text-xs text-gray-500 font-bold mt-1">
                              {component.rawValue}
                              {PricingService.isPercentageType(component.valueType) ? '%' : ''}
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Placeholder when no calculation */}
            {!loading && !result && !error && (
              <div className="py-8 text-center text-gray-400">
                <p className="text-sm font-bold uppercase tracking-widest">
                  Adjust parameters above to see price calculation
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default LivePricePreview;

