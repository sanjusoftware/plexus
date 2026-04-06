import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Building2, Loader2, Plus, ArrowRight, CheckCircle2, XCircle, Clock, X, ShieldCheck, Info, Mail, Globe, DollarSign, Edit
} from 'lucide-react';
import { HasPermission } from '../../components/HasPermission';
import axios from 'axios';
import ConfirmationModal from '../../components/ConfirmationModal';
import OnboardingSuccessModal from '../../components/OnboardingSuccessModal';
import { useEscapeKey } from '../../hooks/useEscapeKey';
import { useAbortSignal } from '../../hooks/useAbortSignal';
import { AdminPage, AdminPageHeader } from '../../components/AdminPageLayout';

const BankManagementPage = () => {
  const { user, loading: authLoading, setToast } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [banks, setBanks] = useState<any[]>([]);
  const [successState, setSuccessState] = useState<{ title: string; message: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedBank, setSelectedBank] = useState<any>(null);
  const [showDeactivateConfirm, setShowDeactivateConfirm] = useState(false);
  const [showRejectConfirm, setShowRejectConfirm] = useState(false);
  const [bankToDeactivate, setBankToDeactivate] = useState<string | null>(null);
  const [bankToReject, setBankToReject] = useState<string | null>(null);
  const signal = useAbortSignal();

  const fetchBanks = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/banks', { signal: abortSignal });
      setBanks(response.data || []);
    } catch (err) {
      if (axios.isCancel(err)) return;
      console.error('Failed to fetch banks:', err);
      setToast({ message: 'Failed to fetch banks. Make sure you have SYSTEM_ADMIN permissions.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast]);

  useEffect(() => {
    if (!authLoading && user) {
      fetchBanks(signal);
    }
  }, [user, authLoading, fetchBanks, signal]);

  useEffect(() => {
    if (location.state?.onboardingSuccess) {
      setSuccessState({
        title: location.state.title,
        message: location.state.message
      });
      // Clear navigation state to prevent modal from re-appearing on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleStatusUpdate = async (targetBankId: string, action: 'activate' | 'deactivate' | 'reject') => {
    try {
      await axios.post(`/api/v1/banks/${targetBankId}/${action}`);

      await fetchBanks(signal);
      setToast({ message: `Bank ${targetBankId} has been successfully ${action === 'reject' ? 'rejected' : action + 'd'}.`, type: 'success' });
      setSelectedBank(null);
      setShowDeactivateConfirm(false);
      setBankToDeactivate(null);
      setShowRejectConfirm(false);
      setBankToReject(null);
    } catch (err: any) {
      console.error(`Failed to ${action} bank:`, err);
      setToast({ message: err.response?.data?.message || `Failed to ${action} bank.`, type: 'error' });
    }
  };

  const confirmDeactivate = (targetBankId: string) => {
    setBankToDeactivate(targetBankId);
    setShowDeactivateConfirm(true);
  };

  const confirmReject = (targetBankId: string) => {
    setBankToReject(targetBankId);
    setShowRejectConfirm(true);
  };

  const openBankDetails = async (bank: any) => {
    try {
      const response = await axios.get(`/api/v1/banks/${bank.bankId}`);
      setSelectedBank(response.data);
    } catch (err) {
      console.error('Failed to fetch bank details:', err);
    }
  };

  useEscapeKey(() => setSelectedBank(null), !!selectedBank);

  if (authLoading || (user && loading && banks.length === 0)) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  return (
    <>
      <OnboardingSuccessModal
        isOpen={!!successState}
        onClose={() => setSuccessState(null)}
        title={successState?.title || ''}
        message={successState?.message || ''}
      />
      <AdminPage width="wide">
        <AdminPageHeader
          icon={Building2}
          title="Bank Management"
          description="Global banking infrastructure and tenant onboarding."
          actions={
            <HasPermission action="POST" path="/api/v1/banks">
              <button
                onClick={() => navigate('/onboarding?admin=true')}
                className="admin-primary-btn"
              >
                <Plus className="h-4 w-4" />
                Add Bank
              </button>
            </HasPermission>
          }
        />

        <div className="bg-white rounded-xl border shadow-sm overflow-hidden">
          <div className="divide-y divide-gray-50">
            {banks.length === 0 ? (
              <div className="p-12 text-center">
                 <Building2 className="h-10 w-10 text-gray-100 mx-auto mb-2" />
                 <p className="text-gray-400 font-bold uppercase tracking-widest text-[10px]">No managed banks found.</p>
              </div>
            ) : (
              banks.map((item, idx) => (
                <div
                  key={idx}
                  onClick={() => openBankDetails(item)}
                  className="px-6 py-4 flex justify-between items-center hover:bg-blue-50/30 transition cursor-pointer group"
                >
                  <div className="flex items-center space-x-4">
                    <div className="p-3 bg-blue-50 rounded-xl group-hover:scale-105 transition duration-300">
                      <Building2 className="h-5 w-5 text-blue-600" />
                    </div>
                    <div>
                      <div className="flex items-center space-x-2 mb-0.5">
                        <p className="font-bold text-gray-900 text-base tracking-tight">
                          {item.name || item.bankId}
                          <span className="text-[10px] text-gray-400 font-bold ml-1.5 italic">({item.bankId})</span>
                        </p>
                        <span className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase tracking-widest ${
                          item.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           item.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           item.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                          {item.status}
                        </span>
                      </div>
                      <p className="text-xs text-gray-400 font-medium mb-0.5">{item.issuerUrl}</p>
                      {item.adminName && (
                        <div className="flex items-center text-[9px] text-gray-400 font-bold uppercase tracking-wider">
                           <ShieldCheck className="h-2.5 w-2.5 mr-1 text-blue-400" /> {item.adminName} ({item.adminEmail})
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    {item.status === 'DRAFT' && (
                      <>
                        <HasPermission action="PUT" path="/api/v1/banks">
                          <button
                            onClick={(e) => { e.stopPropagation(); navigate(`/banks/edit/${item.bankId}`); }}
                            className="px-3 py-1.5 bg-blue-50 text-blue-600 rounded-lg text-[9px] font-bold uppercase tracking-widest hover:bg-blue-100 transition flex items-center"
                          >
                            <Edit className="h-2.5 w-2.5 mr-1" /> Edit
                          </button>
                        </HasPermission>
                        <HasPermission action="POST" path="/api/v1/banks/*/activate">
                          <button
                            onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                            className="px-3 py-1.5 bg-green-50 text-green-600 rounded-lg text-[9px] font-bold uppercase tracking-widest hover:bg-green-100 transition flex items-center"
                          >
                            <CheckCircle2 className="h-2.5 w-2.5 mr-1" /> Approve
                          </button>
                        </HasPermission>
                        <HasPermission action="POST" path="/api/v1/banks/*/reject">
                          <button
                            onClick={(e) => { e.stopPropagation(); confirmReject(item.bankId); }}
                            className="px-3 py-1.5 bg-red-50 text-red-600 rounded-lg text-[9px] font-bold uppercase tracking-widest hover:bg-red-100 transition flex items-center"
                          >
                            <XCircle className="h-2.5 w-2.5 mr-1" /> Reject
                          </button>
                        </HasPermission>
                      </>
                    )}
                    {item.status === 'ACTIVE' && (
                      <HasPermission action="POST" path="/api/v1/banks/*/deactivate">
                        <button
                          onClick={(e) => { e.stopPropagation(); confirmDeactivate(item.bankId); }}
                          className="px-3 py-1.5 bg-gray-50 text-gray-600 rounded-lg text-[9px] font-bold uppercase tracking-widest hover:bg-gray-100 transition flex items-center"
                        >
                          <Clock className="h-2.5 w-2.5 mr-1" /> Deactivate
                        </button>
                      </HasPermission>
                    )}
                    {item.status === 'INACTIVE' && (
                      <HasPermission action="POST" path="/api/v1/banks/*/activate">
                        <button
                          onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                          className="px-3 py-1.5 bg-green-50 text-green-600 rounded-lg text-[9px] font-bold uppercase tracking-widest hover:bg-green-100 transition flex items-center"
                        >
                          <CheckCircle2 className="h-2.5 w-2.5 mr-1" /> Re-activate
                        </button>
                      </HasPermission>
                    )}
                    <div className="h-8 w-8 flex items-center justify-center rounded-full group-hover:bg-blue-600 group-hover:text-white transition-all">
                      <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-white" />
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </AdminPage>

      {/* Bank Details Modal */}
      {selectedBank && (
        <div className="fixed inset-0 bg-blue-900/40 flex items-center justify-center z-50 p-4 animate-in fade-in duration-300">
          <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full overflow-hidden border border-white/20">
            <div className="px-8 py-6 bg-blue-900 text-white flex justify-between items-center relative">
              <div className="relative">
                <h2 className="text-xl font-bold tracking-tight uppercase italic">{selectedBank.name}</h2>
                <p className="text-blue-300 text-[10px] font-bold uppercase tracking-widest mt-0.5">Entity ID: {selectedBank.bankId}</p>
              </div>
              <button onClick={() => setSelectedBank(null)} className="p-2 hover:bg-blue-800 rounded-xl transition relative border border-white/10">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="p-8 space-y-6">
              <div className="grid grid-cols-2 gap-6">
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><Globe className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">OIDC Issuer URL</p>
                    <p className="text-xs font-bold text-gray-900 break-all leading-tight">{selectedBank.issuerUrl}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><ShieldCheck className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">Application ID</p>
                    <p className="text-xs font-bold text-gray-900 break-all leading-tight">{selectedBank.clientId || 'N/A'}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><Mail className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">Lead Admin</p>
                    <p className="text-xs font-bold text-gray-900 leading-tight">{selectedBank.adminName}</p>
                    <p className="text-[9px] text-gray-400 font-bold lowercase">{selectedBank.adminEmail}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><DollarSign className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">Base Currency</p>
                    <p className="text-xs font-bold text-gray-900 uppercase">{selectedBank.currencyCode}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><Info className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">Lifecycle State</p>
                    <span className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase tracking-widest ${
                          selectedBank.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           selectedBank.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           selectedBank.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                      {selectedBank.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="pt-6 border-t flex space-x-3">
                {selectedBank.status === 'DRAFT' && (
                  <>
                    <HasPermission action="PUT" path="/api/v1/banks">
                      <button
                        onClick={() => navigate(`/banks/edit/${selectedBank.bankId}`)}
                        className="flex-1 bg-blue-600 text-white py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-blue-700 transition shadow-md shadow-blue-100 flex items-center justify-center"
                      >
                        <Edit className="h-3 w-3 mr-1.5" /> Edit Configuration
                      </button>
                    </HasPermission>
                    <HasPermission action="POST" path="/api/v1/banks/*/activate">
                      <button
                        onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                        className="flex-1 bg-green-600 text-white py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-green-700 transition shadow-md shadow-blue-100"
                      >
                        Approve Bank
                      </button>
                    </HasPermission>
                    <HasPermission action="POST" path="/api/v1/banks/*/reject">
                      <button
                        onClick={() => confirmReject(selectedBank.bankId)}
                        className="flex-1 bg-red-100 text-red-700 py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-red-200 transition"
                      >
                        Reject Request
                      </button>
                    </HasPermission>
                  </>
                )}
                {selectedBank.status === 'ACTIVE' && (
                  <HasPermission action="POST" path="/api/v1/banks/*/deactivate">
                    <button
                      onClick={() => confirmDeactivate(selectedBank.bankId)}
                      className="flex-1 bg-gray-100 text-gray-700 py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-gray-200 transition"
                    >
                      Deactivate Bank
                    </button>
                  </HasPermission>
                )}
                {selectedBank.status === 'INACTIVE' && (
                  <HasPermission action="POST" path="/api/v1/banks/*/activate">
                    <button
                      onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                      className="flex-1 bg-green-600 text-white py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-green-700 transition shadow-md shadow-blue-100"
                    >
                      Re-activate Bank
                    </button>
                  </HasPermission>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Deactivation Confirmation Modal */}
      <ConfirmationModal
        isOpen={showDeactivateConfirm}
        onClose={() => { setShowDeactivateConfirm(false); setBankToDeactivate(null); }}
        onConfirm={() => handleStatusUpdate(bankToDeactivate!, 'deactivate')}
        title="Confirm Deactivation"
        message={`Are you sure you want to deactivate ${bankToDeactivate}? This will prevent any users from this bank from logging into the platform.`}
        confirmText="Confirm & Deactivate"
        variant="danger"
      />

      <ConfirmationModal
        isOpen={showRejectConfirm}
        onClose={() => { setShowRejectConfirm(false); setBankToReject(null); }}
        onConfirm={() => handleStatusUpdate(bankToReject!, 'reject')}
        title="Confirm Rejection"
        message={`Are you sure you want to reject ${bankToReject}? This action will change the bank status to REJECTED.`}
        confirmText="Confirm & Reject"
        variant="danger"
      />
    </>
  );
};

export default BankManagementPage;
