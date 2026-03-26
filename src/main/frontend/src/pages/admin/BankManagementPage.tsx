import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import {
  Building2, Loader2, Plus, ArrowRight, ExternalLink, CheckCircle2, XCircle, Clock, X, ShieldCheck, Info, Mail, Globe, DollarSign, AlertCircle, Edit
} from 'lucide-react';
import axios from 'axios';
import ConfirmationModal from '../../components/ConfirmationModal';

const BankManagementPage = () => {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const [banks, setBanks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedBank, setSelectedBank] = useState<any>(null);
  const [showDeactivateConfirm, setShowDeactivateConfirm] = useState(false);
  const [bankToDeactivate, setBankToDeactivate] = useState<string | null>(null);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');

  useEffect(() => {
    if (!authLoading && (!user || !isSystemAdmin)) {
      navigate('/dashboard');
    }
  }, [user, authLoading, isSystemAdmin, navigate]);

  const fetchBanks = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/v1/banks');
      setBanks(response.data || []);
    } catch (err) {
      console.error('Failed to fetch banks:', err);
      setError('Failed to fetch banks. Make sure you have SYSTEM_ADMIN permissions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authLoading && user && isSystemAdmin) {
      fetchBanks();
    }
  }, [user, authLoading, isSystemAdmin]);

  const handleStatusUpdate = async (targetBankId: string, action: 'activate' | 'deactivate' | 'REJECTED') => {
    setError('');
    setSuccess('');
    try {
      if (action === 'REJECTED') {
        await axios.put(`/api/v1/banks/${targetBankId}`, { status: 'REJECTED' });
      } else {
        await axios.post(`/api/v1/banks/${targetBankId}/${action}`);
      }

      await fetchBanks();
      setSuccess(`Bank ${targetBankId} has been successfully ${action === 'REJECTED' ? 'rejected' : action + 'd'}.`);
      setSelectedBank(null);
      setShowDeactivateConfirm(false);
      setBankToDeactivate(null);
    } catch (err: any) {
      console.error(`Failed to ${action} bank:`, err);
      setError(err.response?.data?.message || `Failed to ${action} bank.`);
    }
  };

  const confirmDeactivate = (targetBankId: string) => {
    setBankToDeactivate(targetBankId);
    setShowDeactivateConfirm(true);
  };

  const openBankDetails = async (bank: any) => {
    try {
      const response = await axios.get(`/api/v1/banks/${bank.bankId}`);
      setSelectedBank(response.data);
    } catch (err) {
      console.error('Failed to fetch bank details:', err);
    }
  };

  if (authLoading || (user && loading && banks.length === 0)) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  return (
    <>
      <div className="flex justify-between items-center mb-8">
        <div>
          <h2 className="text-3xl font-black text-blue-900 tracking-tight uppercase italic">Bank Management</h2>
          <p className="text-gray-500 font-bold">Global banking infrastructure and tenant onboarding.</p>
        </div>
        <button
          onClick={() => navigate('/onboarding?admin=true')}
          className="bg-blue-600 text-white px-6 py-3 rounded-2xl font-black text-sm hover:bg-blue-700 transition flex items-center shadow-lg shadow-blue-100 uppercase tracking-widest"
        >
          <Plus className="h-5 w-5 mr-2" /> Add Bank
        </button>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700 animate-in fade-in slide-in-from-top-1">
          <AlertCircle className="h-5 w-5 mr-3 flex-shrink-0" />
          <p className="text-sm font-bold">{error}</p>
          <button onClick={() => setError('')} className="ml-auto p-1 hover:bg-red-100 rounded-full transition">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {success && (
        <div className="mb-6 p-4 bg-green-50 border-l-4 border-green-500 rounded-r-xl flex items-center text-green-700 animate-in fade-in slide-in-from-top-1">
          <CheckCircle2 className="h-5 w-5 mr-3 flex-shrink-0" />
          <p className="text-sm font-bold">{success}</p>
          <button onClick={() => setSuccess('')} className="ml-auto p-1 hover:bg-green-100 rounded-full transition">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <div className="bg-white rounded-[2.5rem] border shadow-sm overflow-hidden">
        <div className="divide-y divide-gray-50">
          {banks.length === 0 ? (
            <div className="p-20 text-center">
               <Building2 className="h-16 w-16 text-gray-100 mx-auto mb-4" />
               <p className="text-gray-400 font-black uppercase tracking-widest text-xs">No managed banks found.</p>
            </div>
          ) : (
            banks.map((item, idx) => (
              <div
                key={idx}
                onClick={() => openBankDetails(item)}
                className="px-8 py-6 flex justify-between items-center hover:bg-blue-50/30 transition cursor-pointer group"
              >
                <div className="flex items-center space-x-6">
                  <div className="p-4 bg-blue-50 rounded-[1.5rem] group-hover:scale-110 transition duration-300">
                    <Building2 className="h-6 w-6 text-blue-600" />
                  </div>
                  <div>
                    <div className="flex items-center space-x-3 mb-1">
                      <p className="font-black text-gray-900 text-lg tracking-tight">
                        {item.name || item.bankId}
                        <span className="text-xs text-gray-400 font-bold ml-2 italic">({item.bankId})</span>
                      </p>
                      <span className={`text-[10px] px-3 py-1 rounded-full font-black uppercase tracking-widest ${
                        item.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                         item.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                         item.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                      }`}>
                        {item.status}
                      </span>
                    </div>
                    <p className="text-sm text-gray-400 font-medium mb-1">{item.issuerUrl}</p>
                    {item.adminName && (
                      <div className="flex items-center text-[10px] text-gray-400 font-bold uppercase tracking-wider">
                         <ShieldCheck className="h-3 w-3 mr-1 text-blue-400" /> {item.adminName} ({item.adminEmail})
                      </div>
                    )}
                  </div>
                </div>
                <div className="flex items-center space-x-3">
                  {item.status === 'DRAFT' && (
                    <>
                      <button
                        onClick={(e) => { e.stopPropagation(); navigate(`/banks/edit/${item.bankId}`); }}
                        className="px-4 py-2 bg-blue-50 text-blue-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-blue-100 transition flex items-center"
                      >
                        <Edit className="h-3 w-3 mr-1.5" /> Edit
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                        className="px-4 py-2 bg-green-50 text-green-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-green-100 transition flex items-center"
                      >
                        <CheckCircle2 className="h-3 w-3 mr-1.5" /> Approve
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'REJECTED'); }}
                        className="px-4 py-2 bg-red-50 text-red-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-red-100 transition flex items-center"
                      >
                        <XCircle className="h-3 w-3 mr-1.5" /> Reject
                      </button>
                    </>
                  )}
                  {item.status === 'ACTIVE' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); confirmDeactivate(item.bankId); }}
                      className="px-4 py-2 bg-gray-50 text-gray-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-gray-100 transition flex items-center"
                    >
                      <Clock className="h-3 w-3 mr-1.5" /> Deactivate
                    </button>
                  )}
                  {item.status === 'INACTIVE' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); handleStatusUpdate(item.bankId, 'activate'); }}
                      className="px-4 py-2 bg-green-50 text-green-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-green-100 transition flex items-center"
                    >
                      <CheckCircle2 className="h-3 w-3 mr-1.5" /> Re-activate
                    </button>
                  )}
                  <div className="h-10 w-10 flex items-center justify-center rounded-full group-hover:bg-blue-600 group-hover:text-white transition-all">
                    <ArrowRight className="h-5 w-5 text-gray-300 group-hover:text-white" />
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
        <div className="px-8 py-6 bg-gray-50/50 border-t flex justify-center">
           <a href="/swagger-ui/index.html" target="_blank" className="flex items-center text-xs font-black uppercase tracking-widest text-gray-400 hover:text-blue-600 transition">
             Explore Global API in Swagger <ExternalLink className="h-3 w-3 ml-2" />
           </a>
        </div>
      </div>

      {/* Bank Details Modal */}
      {selectedBank && (
        <div className="fixed inset-0 bg-blue-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4 animate-in fade-in duration-300">
          <div className="bg-white rounded-[3rem] shadow-2xl max-w-2xl w-full overflow-hidden border border-white/20">
            <div className="px-10 py-8 bg-blue-900 text-white flex justify-between items-center relative">
              <div className="absolute top-0 right-0 w-64 h-full bg-blue-800 -skew-x-12 translate-x-20 opacity-50"></div>
              <div className="relative">
                <h2 className="text-3xl font-black tracking-tight uppercase italic">{selectedBank.name}</h2>
                <p className="text-blue-300 text-xs font-bold uppercase tracking-widest mt-1">Entity ID: {selectedBank.bankId}</p>
              </div>
              <button onClick={() => setSelectedBank(null)} className="p-3 hover:bg-blue-800 rounded-full transition relative border border-white/10">
                <X className="h-6 w-6" />
              </button>
            </div>
            <div className="p-10 space-y-8">
              <div className="grid grid-cols-2 gap-10">
                <div className="flex items-start space-x-4">
                  <div className="p-2 bg-blue-50 rounded-xl"><Globe className="h-5 w-5 text-blue-600" /></div>
                  <div>
                    <p className="text-[10px] text-gray-400 font-black uppercase tracking-widest mb-1">OIDC Issuer URL</p>
                    <p className="text-sm font-bold text-gray-900 break-all leading-tight">{selectedBank.issuerUrl}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-4">
                  <div className="p-2 bg-blue-50 rounded-xl"><ShieldCheck className="h-5 w-5 text-blue-600" /></div>
                  <div>
                    <p className="text-[10px] text-gray-400 font-black uppercase tracking-widest mb-1">Application ID</p>
                    <p className="text-sm font-bold text-gray-900 break-all leading-tight">{selectedBank.clientId || 'N/A'}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-4">
                  <div className="p-2 bg-blue-50 rounded-xl"><Mail className="h-5 w-5 text-blue-600" /></div>
                  <div>
                    <p className="text-[10px] text-gray-400 font-black uppercase tracking-widest mb-1">Lead Admin</p>
                    <p className="text-sm font-bold text-gray-900 leading-tight">{selectedBank.adminName}</p>
                    <p className="text-[10px] text-gray-400 font-bold lowercase">{selectedBank.adminEmail}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-4">
                  <div className="p-2 bg-blue-50 rounded-xl"><DollarSign className="h-5 w-5 text-blue-600" /></div>
                  <div>
                    <p className="text-[10px] text-gray-400 font-black uppercase tracking-widest mb-1">Base Currency</p>
                    <p className="text-sm font-bold text-gray-900 uppercase">{selectedBank.currencyCode}</p>
                  </div>
                </div>
                <div className="flex items-start space-x-4">
                  <div className="p-2 bg-blue-50 rounded-xl"><Info className="h-5 w-5 text-blue-600" /></div>
                  <div>
                    <p className="text-[10px] text-gray-400 font-black uppercase tracking-widest mb-1">Lifecycle State</p>
                    <span className={`text-[10px] px-3 py-1 rounded-full font-black uppercase tracking-widest ${
                          selectedBank.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                           selectedBank.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-700' :
                           selectedBank.status === 'INACTIVE' ? 'bg-gray-100 text-gray-700' : 'bg-red-100 text-red-700'
                        }`}>
                      {selectedBank.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="pt-8 border-t flex space-x-4">
                {selectedBank.status === 'DRAFT' && (
                  <>
                    <button
                      onClick={() => navigate(`/banks/edit/${selectedBank.bankId}`)}
                      className="flex-1 bg-blue-600 text-white py-4 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-blue-700 transition shadow-lg shadow-blue-100 flex items-center justify-center"
                    >
                      <Edit className="h-4 w-4 mr-2" /> Edit Configuration
                    </button>
                    <button
                      onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                      className="flex-1 bg-green-600 text-white py-4 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-green-700 transition shadow-lg shadow-green-100"
                    >
                      Approve Bank
                    </button>
                    <button
                      onClick={() => handleStatusUpdate(selectedBank.bankId, 'REJECTED')}
                      className="flex-1 bg-red-100 text-red-700 py-4 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-red-200 transition"
                    >
                      Reject Request
                    </button>
                  </>
                )}
                {selectedBank.status === 'ACTIVE' && (
                  <button
                    onClick={() => confirmDeactivate(selectedBank.bankId)}
                    className="flex-1 bg-gray-100 text-gray-700 py-4 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-gray-200 transition"
                  >
                    Deactivate Bank
                  </button>
                )}
                {selectedBank.status === 'INACTIVE' && (
                  <button
                    onClick={() => handleStatusUpdate(selectedBank.bankId, 'activate')}
                    className="flex-1 bg-green-600 text-white py-4 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-green-700 transition shadow-lg shadow-green-100"
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
      <ConfirmationModal
        isOpen={showDeactivateConfirm}
        onClose={() => { setShowDeactivateConfirm(false); setBankToDeactivate(null); }}
        onConfirm={() => handleStatusUpdate(bankToDeactivate!, 'deactivate')}
        title="Confirm Deactivation"
        message={`Are you sure you want to deactivate ${bankToDeactivate}? This will prevent any users from this bank from logging into the platform.`}
        confirmText="Confirm & Deactivate"
        variant="danger"
      />
    </>
  );
};

export default BankManagementPage;
