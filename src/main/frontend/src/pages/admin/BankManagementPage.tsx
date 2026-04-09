import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Building2, Loader2, Plus, ArrowRight, CheckCircle2, XCircle, Clock, X, ShieldCheck, Info, Mail, Globe, DollarSign, Edit,
  Layers, AlertTriangle
} from 'lucide-react';
import { HasPermission } from '../../components/HasPermission';
import axios from 'axios';
import {
  AdminDataTable,
  AdminDataTableActionButton,
  AdminDataTableActionCell,
  AdminDataTableActionsHeader,
  AdminDataTableEmptyRow,
  AdminDataTableRow
} from '../../components/AdminDataTable';
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
  const [myBank, setMyBank] = useState<any>(null);
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
      const isSystemAdmin = user?.permissions?.includes('system:bank:read');
      const canReadConfig = user?.permissions?.includes('bank:config:read');

      let otherBanks: any[] = [];
      let ownBank: any = null;

      if (isSystemAdmin) {
        const response = await axios.get('/api/v1/banks', { signal: abortSignal });
        const allBanks = response.data || [];
        // Identify own bank within the full list if present
        ownBank = allBanks.find((b: any) => b.bankId === user?.bank_id);
        otherBanks = allBanks.filter((b: any) => b.bankId !== user?.bank_id);
      }

      // If not system admin, or if ownBank not found in allBanks (unlikely for sys admin),
      // and user has config read permission, fetch it specifically
      if (!ownBank && canReadConfig && user?.bank_id) {
        try {
          const myBankRes = await axios.get(`/api/v1/banks/${user.bank_id}`, { signal: abortSignal });
          ownBank = myBankRes.data;
        } catch (err) {
          console.error('Failed to fetch own bank details:', err);
        }
      }

      setBanks(otherBanks);
      setMyBank(ownBank);
    } catch (err) {
      if (axios.isCancel(err)) return;
      console.error('Failed to fetch banks:', err);
      setToast({ message: 'Failed to fetch banks.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [setToast, user]);

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
                onClick={() => navigate('/banks/create')}
                className="admin-primary-btn"
              >
                <Plus className="h-4 w-4" />
                Add Bank
              </button>
            </HasPermission>
          }
        />

        <AdminDataTable aria-label="Managed banks table">
          <thead>
            <tr>
              <th>Bank Information</th>
              <th>Issuer URL</th>
              <th>Status</th>
              <AdminDataTableActionsHeader>Actions</AdminDataTableActionsHeader>
            </tr>
          </thead>
          <tbody>
            {banks.length === 0 && !myBank ? (
              <AdminDataTableEmptyRow colSpan={4}>No managed banks found.</AdminDataTableEmptyRow>
            ) : (
              <>
                {banks.map((item, idx) => (
                  <AdminDataTableRow
                    key={item.bankId}
                    onClick={() => openBankDetails(item)}
                    interactive
                  >
                    <td className="whitespace-nowrap">
                      <div className="flex items-center space-x-3">
                        <div className="p-2 bg-blue-50 rounded-lg">
                          <Building2 className="h-4 w-4 text-blue-600" />
                        </div>
                        <div>
                          <div className="text-sm font-bold text-gray-900 leading-tight">
                            {item.name || item.bankId}
                          </div>
                          <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest uppercase">
                            ID: {item.bankId}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="whitespace-nowrap text-xs text-gray-500 font-medium max-w-[200px] truncate" title={item.issuerUrl}>
                      {item.issuerUrl}
                    </td>
                    <td className="whitespace-nowrap">
                      <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${
                        item.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                         item.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                         item.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                      }`}>
                        {item.status}
                      </span>
                    </td>
                    <AdminDataTableActionCell>
                      <HasPermission action="PUT" path="/api/v1/banks/*">
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); navigate(`/banks/edit/${item.bankId}`); }}
                          tone="primary"
                          size="compact"
                          title="Edit"
                        >
                          <Edit className="h-3.5 w-3.5" /> Edit
                        </AdminDataTableActionButton>
                      </HasPermission>
                      {item.status === 'DRAFT' && (
                        <>
                          <HasPermission action="POST" path="/api/v1/banks/*/activate">
                            <AdminDataTableActionButton
                              onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                              tone="success"
                              size="compact"
                              title="Approve"
                            >
                              <CheckCircle2 className="h-3.5 w-3.5" /> Approve
                            </AdminDataTableActionButton>
                          </HasPermission>
                          <HasPermission action="POST" path="/api/v1/banks/*/reject">
                            <AdminDataTableActionButton
                              onClick={(e) => { e.stopPropagation(); confirmReject(item.bankId); }}
                              tone="danger"
                              size="compact"
                              title="Reject"
                            >
                              <XCircle className="h-3.5 w-3.5" /> Reject
                            </AdminDataTableActionButton>
                          </HasPermission>
                        </>
                      )}
                      {item.status === 'ACTIVE' && (
                        <HasPermission action="POST" path="/api/v1/banks/*/deactivate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); confirmDeactivate(item.bankId); }}
                            tone="neutral"
                            size="compact"
                            title="Deactivate"
                          >
                            <Clock className="h-3.5 w-3.5" /> Deactivate
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      {item.status === 'INACTIVE' && (
                        <HasPermission action="POST" path="/api/v1/banks/*/activate">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                            tone="success"
                            size="compact"
                            title="Re-activate"
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" /> Re-activate
                          </AdminDataTableActionButton>
                        </HasPermission>
                      )}
                      <AdminDataTableActionButton
                        onClick={(e) => { e.stopPropagation(); openBankDetails(item); }}
                        tone="neutral"
                        size="compact"
                        title="View Details"
                      >
                        <ArrowRight className="h-3.5 w-3.5 text-gray-300" />
                      </AdminDataTableActionButton>
                    </AdminDataTableActionCell>
                  </AdminDataTableRow>
                ))}

                {myBank && (
                  <>
                    {banks.length > 0 && (
                      <tr className="bg-gray-50/30">
                        <td colSpan={4} className="px-5 py-3">
                          <div className="flex items-center gap-3">
                            <div className="h-px flex-1 bg-gray-200"></div>
                            <span className="text-[10px] font-black uppercase tracking-[0.2em] text-gray-400">My Bank Settings</span>
                            <div className="h-px flex-1 bg-gray-200"></div>
                          </div>
                        </td>
                      </tr>
                    )}
                    <AdminDataTableRow
                      onClick={() => openBankDetails(myBank)}
                      interactive
                      className="bg-blue-50/50"
                    >
                      <td className="whitespace-nowrap relative">
                        <div className="absolute left-0 top-0 bottom-0 w-1 bg-blue-600 rounded-r-lg"></div>
                        <div className="flex items-center space-x-3">
                          <div className="p-2 bg-blue-100 rounded-lg">
                            <Building2 className="h-4 w-4 text-blue-700" />
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <div className="text-sm font-black text-gray-900 leading-tight">
                                {myBank.name || myBank.bankId}
                              </div>
                              <span className="px-1.5 py-0.5 bg-blue-600 text-[8px] font-bold text-white rounded uppercase tracking-wider">OWN</span>
                            </div>
                            <div className="text-[10px] text-gray-400 font-mono mt-0.5 tracking-widest uppercase">
                              ID: {myBank.bankId}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="whitespace-nowrap text-xs text-gray-600 font-bold max-w-[200px] truncate" title={myBank.issuerUrl}>
                        {myBank.issuerUrl}
                      </td>
                      <td className="whitespace-nowrap">
                        <span className={`px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider ${
                          myBank.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           myBank.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           myBank.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                          {myBank.status}
                        </span>
                      </td>
                      <AdminDataTableActionCell>
                        <HasPermission action="PUT" path="/api/v1/banks">
                          <AdminDataTableActionButton
                            onClick={(e) => { e.stopPropagation(); navigate(`/my-bank`); }}
                            tone="primary"
                            size="compact"
                            title="Edit My Bank Settings"
                          >
                            <Edit className="h-3.5 w-3.5" /> Edit
                          </AdminDataTableActionButton>
                        </HasPermission>
                        <AdminDataTableActionButton
                          onClick={(e) => { e.stopPropagation(); openBankDetails(myBank); }}
                          tone="neutral"
                          size="compact"
                          title="View Details"
                        >
                          <ArrowRight className="h-3.5 w-3.5 text-gray-300" />
                        </AdminDataTableActionButton>
                      </AdminDataTableActionCell>
                    </AdminDataTableRow>
                  </>
                )}
              </>
            )}
          </tbody>
        </AdminDataTable>
      </AdminPage>

      {/* Bank Details Modal */}
      {selectedBank && (
        <div className="fixed inset-0 bg-blue-900/40 flex items-center justify-center z-50 p-4 animate-in fade-in duration-300">
          <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full overflow-hidden border border-white/20">
            <div className="px-8 py-6 bg-blue-900 text-white flex justify-between items-center relative">
              <div className="relative">
                <h2 className="text-xl font-bold tracking-tight uppercase italic">{selectedBank.name || selectedBank.bankId}</h2>
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
                <div className="flex items-start space-x-3">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><Layers className="h-4 w-4 text-blue-600" /></div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-0.5">Allow Multi-Bundle Products</p>
                    <div className="flex items-center h-[18px]">
                      <div className={`relative inline-flex h-4 w-7 items-center rounded-full transition-colors ${selectedBank.allowProductInMultipleBundles ? 'bg-blue-600' : 'bg-gray-200'}`}>
                        <span className={`inline-block h-2.5 w-2.5 transform rounded-full bg-white transition-transform ${selectedBank.allowProductInMultipleBundles ? 'translate-x-4' : 'translate-x-0.5'}`} />
                      </div>
                      <span className="ml-2 text-[10px] font-bold text-gray-500 uppercase tracking-tight">
                        {selectedBank.allowProductInMultipleBundles ? 'Enabled' : 'Disabled'}
                      </span>
                    </div>
                  </div>
                </div>
                <div className="flex items-start space-x-3 col-span-2">
                  <div className="p-1.5 bg-blue-50 rounded-lg"><AlertTriangle className="h-4 w-4 text-blue-600" /></div>
                  <div className="flex-1">
                    <p className="text-[9px] text-gray-400 font-bold uppercase tracking-widest mb-1.5">Category Conflict Rules</p>
                    {selectedBank.categoryConflictRules && selectedBank.categoryConflictRules.length > 0 ? (
                      <div className="grid grid-cols-2 gap-2">
                        {selectedBank.categoryConflictRules.map((rule: any, idx: number) => (
                          <div key={idx} className="flex items-center space-x-2 bg-gray-50 px-2 py-1.5 rounded-lg border border-gray-100">
                            <span className="text-[10px] font-black text-gray-700">{rule.categoryA}</span>
                            <span className="text-[8px] font-bold text-gray-300 uppercase tracking-tighter italic">vs</span>
                            <span className="text-[10px] font-black text-gray-700">{rule.categoryB}</span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs font-bold text-gray-300 italic">No conflict rules defined</p>
                    )}
                  </div>
                </div>
              </div>

              <div className="pt-6 border-t flex space-x-3">
                <HasPermission action="PUT" path="/api/v1/banks/*">
                  <button
                    onClick={() => navigate(`/banks/edit/${selectedBank.bankId}`)}
                    className="flex-1 bg-blue-600 text-white py-2.5 rounded-xl font-bold text-[10px] uppercase tracking-widest hover:bg-blue-700 transition shadow-md shadow-blue-100 flex items-center justify-center"
                  >
                    <Edit className="h-3 w-3 mr-1.5" /> Edit Configuration
                  </button>
                </HasPermission>
                {selectedBank.status === 'DRAFT' && (
                  <>
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
