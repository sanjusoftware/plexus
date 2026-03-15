import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import {
  Building2, Package, Loader2, Plus, ArrowRight, ExternalLink, CheckCircle2, XCircle, Clock, X, ShieldCheck, Info, Mail, Globe, DollarSign
} from 'lucide-react';
import axios from 'axios';

const Dashboard = () => {
  const { user, bankId, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedBank, setSelectedBank] = useState<any>(null);
  const [showDeactivateConfirm, setShowDeactivateConfirm] = useState(false);
  const [bankToDeactivate, setBankToDeactivate] = useState<string | null>(null);

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');

  const fetchData = async () => {
    if (!user) return;
    setLoading(true);
    try {
      if (isSystemAdmin) {
        // System Admin sees all banks
        const response = await axios.get('/api/v1/banks');
        setData(response.data || []);
      } else {
        // Bank Admin sees their products
        const response = await axios.get('/api/v1/products');
        setData(response.data.content || []);
      }
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authLoading && user) {
      fetchData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, authLoading, isSystemAdmin]);

  const handleStatusUpdate = async (targetBankId: string, action: 'activate' | 'deactivate' | 'REJECTED') => {
    try {
      if (action === 'REJECTED') {
        await axios.put(`/api/v1/banks/${targetBankId}`, { status: 'REJECTED' });
      } else {
        await axios.post(`/api/v1/banks/${targetBankId}/${action}`);
      }

      await fetchData();
      setSelectedBank(null);
      setShowDeactivateConfirm(false);
      setBankToDeactivate(null);
    } catch (err: any) {
      console.error(`Failed to ${action} bank:`, err);
      alert(err.response?.data?.message || `Failed to ${action} bank. Make sure you have SYSTEM_ADMIN permissions.`);
    }
  };

  const confirmDeactivate = (targetBankId: string) => {
    setBankToDeactivate(targetBankId);
    setShowDeactivateConfirm(true);
  };

  const openBankDetails = async (bank: any) => {
    if (!isSystemAdmin) return;
    try {
      const response = await axios.get(`/api/v1/banks/${bank.bankId}`);
      setSelectedBank(response.data);
    } catch (err) {
      console.error('Failed to fetch bank details:', err);
    }
  };

  if (authLoading || (user && loading && data.length === 0)) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  return (
    <>
      {/* Welcome Card */}
      <div className="bg-white rounded-2xl shadow-sm border p-8 mb-8 flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-blue-900 mb-2">Welcome back!</h2>
          <p className="text-gray-600">
            {isSystemAdmin
              ? "Manage your global banking infrastructure and onboard new tenants."
              : `Manage products and pricing rules for ${user?.bankName || bankId}.`}
          </p>
        </div>
        <div className="hidden md:block">
           <ShieldCheck className="h-20 w-20 text-blue-100" />
        </div>
      </div>

      {/* Data List */}
      <div className="bg-white rounded-2xl border shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b flex justify-between items-center bg-gray-50">
          <h3 className="font-bold text-gray-900">
            {isSystemAdmin ? 'Managed Banks' : 'Available Products'}
          </h3>
          <button
            onClick={() => isSystemAdmin ? navigate('/onboarding?admin=true') : null}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-blue-700 transition flex items-center"
          >
            <Plus className="h-4 w-4 mr-2" />
            {isSystemAdmin ? 'Add Bank' : 'New Product'}
          </button>
        </div>
        <div className="divide-y">
          {data.length === 0 ? (
            <div className="p-12 text-center text-gray-500">
              No {isSystemAdmin ? 'banks' : 'products'} found. Get started by creating your first entry.
            </div>
          ) : (
            data.map((item, idx) => (
              <div
                key={idx}
                onClick={() => openBankDetails(item)}
                className="px-6 py-4 flex justify-between items-center hover:bg-gray-50 transition cursor-pointer"
              >
                <div className="flex items-center space-x-4">
                  <div className="p-2 bg-blue-50 rounded-lg">
                    {isSystemAdmin ? <Building2 className="h-5 w-5 text-blue-600" /> : <Package className="h-5 w-5 text-blue-600" />}
                  </div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <p className="font-semibold text-gray-900">
                        {isSystemAdmin ? (item.name || item.bankId) : item.name}
                        {isSystemAdmin && item.name && <span className="text-xs text-gray-400 font-normal ml-2">({item.bankId})</span>}
                      </p>
                      {isSystemAdmin && (
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase ${
                          item.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           item.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           item.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                          {item.status}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-gray-500">{isSystemAdmin ? item.issuerUrl : item.code}</p>
                    {isSystemAdmin && item.adminName && (
                      <p className="text-xs text-gray-400">Admin: {item.adminName} ({item.adminEmail})</p>
                    )}
                  </div>
                </div>
                <div className="flex items-center space-x-3">
                  {isSystemAdmin && item.status === 'DRAFT' && (
                    <>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                        className="px-3 py-1 bg-green-100 text-green-700 rounded-lg text-xs font-bold hover:bg-green-200 transition flex items-center"
                      >
                        <CheckCircle2 className="h-3 w-3 mr-1" /> Approve
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'REJECTED'); }}
                        className="px-3 py-1 bg-red-100 text-red-700 rounded-lg text-xs font-bold hover:bg-red-200 transition flex items-center"
                      >
                        <XCircle className="h-3 w-3 mr-1" /> Reject
                      </button>
                    </>
                  )}
                  {isSystemAdmin && item.status === 'ACTIVE' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); confirmDeactivate(item.bankId); }}
                      className="px-3 py-1 bg-gray-100 text-gray-600 rounded-lg text-xs font-bold hover:bg-gray-200 transition flex items-center"
                    >
                      <Clock className="h-3 w-3 mr-1" /> Deactivate
                    </button>
                  )}
                  {isSystemAdmin && item.status === 'INACTIVE' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                      className="px-3 py-1 bg-green-100 text-green-700 rounded-lg text-xs font-bold hover:bg-green-200 transition flex items-center"
                    >
                      <CheckCircle2 className="h-3 w-3 mr-1" /> Re-activate
                    </button>
                  )}
                  <ArrowRight className="h-5 w-5 text-gray-300" />
                </div>
              </div>
            ))
          )}
        </div>
        <div className="px-6 py-4 bg-gray-50 border-t flex justify-center">
           <a href="/swagger-ui/index.html" target="_blank" className="flex items-center text-sm text-gray-500 hover:text-blue-600">
             Explore full API in Swagger <ExternalLink className="h-3 w-3 ml-1" />
           </a>
        </div>
      </div>

      {/* Bank Details Modal */}
      {selectedBank && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl shadow-2xl max-w-2xl w-full overflow-hidden">
            <div className="px-8 py-6 bg-blue-900 text-white flex justify-between items-center">
              <div>
                <h2 className="text-2xl font-bold">{selectedBank.name}</h2>
                <p className="text-blue-200 text-sm">Bank ID: {selectedBank.bankId}</p>
              </div>
              <button onClick={() => setSelectedBank(null)} className="p-2 hover:bg-blue-800 rounded-full transition">
                <X className="h-6 w-6" />
              </button>
            </div>
            <div className="p-8 space-y-6">
              <div className="grid grid-cols-2 gap-6">
                <div className="flex items-start space-x-3">
                  <Globe className="h-5 w-5 text-blue-600 mt-1" />
                  <div>
                    <p className="text-xs text-gray-400 font-bold uppercase">Issuer URL</p>
                    <p className="text-sm font-medium break-all">{selectedBank.issuerUrl}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <ShieldCheck className="h-5 w-5 text-blue-600 mt-1" />
                  <div>
                    <p className="text-xs text-gray-400 font-bold uppercase">Client ID</p>
                    <p className="text-sm font-medium break-all">{selectedBank.clientId || 'N/A'}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <Mail className="h-5 w-5 text-blue-600 mt-1" />
                  <div>
                    <p className="text-xs text-gray-400 font-bold uppercase">Admin Contact</p>
                    <p className="text-sm font-medium">{selectedBank.adminName}</p>
                    <p className="text-xs text-gray-500">{selectedBank.adminEmail}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <DollarSign className="h-5 w-5 text-blue-600 mt-1" />
                  <div>
                    <p className="text-xs text-gray-400 font-bold uppercase">Currency</p>
                    <p className="text-sm font-medium">{selectedBank.currencyCode}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <Info className="h-5 w-5 text-blue-600 mt-1" />
                  <div>
                    <p className="text-xs text-gray-400 font-bold uppercase">Status</p>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-bold uppercase ${
                          selectedBank.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           selectedBank.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           selectedBank.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                      {selectedBank.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="pt-6 border-t flex space-x-4">
                {selectedBank.status === 'DRAFT' && (
                  <>
                    <button
                      onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                      className="flex-1 bg-green-600 text-white py-3 rounded-xl font-bold hover:bg-green-700 transition"
                    >
                      Approve Bank
                    </button>
                    <button
                      onClick={() => handleStatusUpdate(selectedBank.bankId, 'REJECTED')}
                      className="flex-1 bg-red-100 text-red-700 py-3 rounded-xl font-bold hover:bg-red-200 transition"
                    >
                      Reject Request
                    </button>
                  </>
                )}
                {selectedBank.status === 'ACTIVE' && (
                  <button
                    onClick={() => confirmDeactivate(selectedBank.bankId)}
                    className="flex-1 bg-gray-100 text-gray-700 py-3 rounded-xl font-bold hover:bg-gray-200 transition"
                  >
                    Deactivate Bank
                  </button>
                )}
                {selectedBank.status === 'INACTIVE' && (
                  <button
                    onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                    className="flex-1 bg-green-600 text-white py-3 rounded-xl font-bold hover:bg-green-700 transition"
                  >
                    Re-activate Bank
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Deactivation Confirmation Modal */}
      {showDeactivateConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-8">
            <div className="flex items-center space-x-3 text-amber-600 mb-4">
              <AlertCircle className="h-8 w-8" />
              <h3 className="text-xl font-bold">Confirm Deactivation</h3>
            </div>
            <p className="text-gray-600 mb-8 leading-relaxed">
              Are you sure you want to deactivate <strong>{bankToDeactivate}</strong>? This will prevent any users from this bank from logging into the platform.
            </p>
            <div className="flex space-x-4">
              <button
                onClick={() => { setShowDeactivateConfirm(false); setBankToDeactivate(null); }}
                className="flex-1 px-4 py-3 rounded-xl font-bold text-gray-600 hover:bg-gray-100 transition"
              >
                Cancel
              </button>
              <button
                onClick={() => handleStatusUpdate(bankToDeactivate!, 'deactivate')}
                className="flex-1 px-4 py-3 rounded-xl font-bold bg-red-600 text-white hover:bg-red-700 transition"
              >
                Confirm & Deactivate
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

const AlertCircle = ({ className }: { className?: string }) => (
  <svg className={className} xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </svg>
);

export default Dashboard;
