import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { ShieldCheck, Rocket, Info, CheckCircle2, AlertCircle, ArrowLeft, Loader2, Eye, EyeOff, Save, X } from 'lucide-react';
import axios from 'axios';
import StyledSelect from '../components/StyledSelect';
import { useAuth } from '../context/AuthContext';
import { useBreadcrumb } from '../context/BreadcrumbContext';

const OnboardingPage = () => {
  const { user } = useAuth();
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { setEntityName } = useBreadcrumb();

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');

  const isAdmin = (new URLSearchParams(location.search).get('admin') === 'true' || !!id) && isSystemAdmin;
  const isEditing = !!id && isSystemAdmin;

  const [formData, setFormData] = useState({
    bankId: '',
    name: '',
    issuerUrl: '',
    clientId: '',
    clientSecret: '',
    adminName: '',
    adminEmail: '',
    currencyCode: 'USD',
    captchaAnswer: ''
  });

  const [customCurrency, setCustomCurrency] = useState('');
  const [isCustomCurrency, setIsCustomCurrency] = useState(false);
  const [isBankIdEdited, setIsBankIdEdited] = useState(false);
  const [captcha, setCaptcha] = useState({ question: '', id: '' });
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [message, setMessage] = useState('');
  const [showSecret, setShowSecret] = useState(false);

  const fetchBankData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get(`/api/v1/banks/${id}`);
      const data = response.data;
      setFormData({
        bankId: data.bankId,
        name: data.name,
        issuerUrl: data.issuerUrl,
        clientId: data.clientId,
        clientSecret: '', // Leave empty. Backend will only update if not blank.
        adminName: data.adminName,
        adminEmail: data.adminEmail,
        currencyCode: data.currencyCode,
        captchaAnswer: ''
      });
      setEntityName(data.name);
      setIsBankIdEdited(true);
      if (!['USD', 'EUR', 'GBP', 'JPY'].includes(data.currencyCode)) {
        setIsCustomCurrency(true);
        setCustomCurrency(data.currencyCode);
      }
    } catch (err) {
      console.error('Failed to fetch bank data', err);
      setStatus('error');
      setMessage('Failed to load bank configuration for editing.');
    } finally {
      setLoading(false);
    }
  }, [id, setEntityName]);

  useEffect(() => {
    if (!isAdmin) {
      fetchCaptcha();
    }

    if (isEditing) {
      fetchBankData();
    }
  }, [id, isAdmin, isEditing, fetchBankData]);

  const fetchCaptcha = async () => {
    try {
      const response = await axios.get('/api/v1/public/onboarding/captcha');
      setCaptcha(response.data);
    } catch (err) {
      console.error('Failed to fetch captcha');
    }
  };

  const validateForm = () => {
    if (formData.adminName.length < 3 || formData.adminName.length > 50) {
      setMessage('Admin Name must be between 3 and 50 characters.');
      return false;
    }
    const urlPattern = /^(https?:\/\/)[^\s$.?#].[^\s]*$/i;
    if (!urlPattern.test(formData.issuerUrl)) {
      setMessage('Please enter a valid OIDC Issuer URL (starting with http:// or https://).');
      return false;
    }
    if (!formData.clientId) {
      setMessage('Client ID is required.');
      return false;
    }
    const guidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!guidPattern.test(formData.clientId)) {
      setMessage('Client ID must be a valid GUID (e.g., 12345678-1234-1234-1234-1234567890ab).');
      return false;
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) {
      setStatus('error');
      return;
    }
    setLoading(true);
    setStatus('idle');
    try {
      const bankDetails = {
        ...formData,
        currencyCode: isCustomCurrency ? customCurrency.toUpperCase() : formData.currencyCode
      };

      if (isEditing) {
        await axios.put(`/api/v1/banks/${id}`, bankDetails);
      } else if (isAdmin) {
        await axios.post('/api/v1/banks', bankDetails);
      } else {
        const submissionData = {
          bankDetails,
          captchaId: captcha.id,
          captchaAnswer: formData.captchaAnswer
        };
        await axios.post('/api/v1/public/onboarding', submissionData);
      }

      setStatus('success');
      setMessage(
        isEditing ? 'Bank configuration has been updated successfully!' :
        isAdmin ? 'New bank has been added successfully!' :
        'Your onboarding request has been submitted successfully! A system administrator will review your request shortly.'
      );
    } catch (err: any) {
      setStatus('error');
      const errorDetail = err.response?.data?.message || err.response?.data || 'Failed to submit request. Please check your inputs and captcha.';
      setMessage(typeof errorDetail === 'string' ? errorDetail : JSON.stringify(errorDetail));
      fetchCaptcha(); // Refresh captcha on error
    } finally {
      setLoading(false);
    }
  };

  if (status === 'success') {
    return (
      <div className={`${isAdmin ? '' : 'min-h-screen bg-gray-50'} flex items-center justify-center p-4`}>
        <div className="max-w-md w-full bg-white rounded-3xl shadow-xl p-10 text-center">
          <CheckCircle2 className="h-20 w-20 text-green-500 mx-auto mb-6" />
          <h2 className="text-3xl font-bold text-gray-900 mb-4">
            {isEditing ? 'Update Successful!' : isAdmin ? 'Bank Created!' : 'Request Submitted!'}
          </h2>
          <p className="text-gray-600 mb-8">{message}</p>
          <button
            onClick={() => navigate(isAdmin ? '/banks' : '/dashboard')}
            className="w-full bg-blue-600 text-white py-3 rounded-xl font-bold hover:bg-blue-700 transition"
          >
            {isAdmin ? 'Back to Bank Management' : 'Return to Home'}
          </button>
        </div>
      </div>
    );
  }

  const renderForm = () => (
    <div className="max-w-xl w-full mx-auto">
      {!isAdmin && (
        <button
          onClick={() => navigate('/')}
          className="flex items-center text-blue-600 hover:text-blue-800 font-medium mb-8 transition"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Home
        </button>
      )}

      <div className="flex justify-between items-start mb-2">
        <h2 className="text-3xl font-bold text-gray-900">
          {isEditing ? 'Edit Bank Configuration' : isAdmin ? 'Add New Bank' : 'Get Started'}
        </h2>
        {isAdmin && (
          <button
            onClick={() => navigate('/banks')}
            className="bg-gray-50 text-gray-400 p-2 rounded-xl hover:bg-gray-100 transition border border-gray-100 shadow-sm"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>
      <p className="text-gray-500 mb-10">
        {isEditing ? `Updating configuration for ${formData.name || id}` :
         isAdmin ? 'Fill out the form below to register a new bank in the system.' :
         'Fill out the form below to submit an onboarding request for your institution.'}
      </p>

      {status === 'error' && (
        <div className="mb-8 p-4 bg-red-50 border border-red-200 rounded-xl flex items-center text-red-700">
          <AlertCircle className="h-5 w-5 mr-3 flex-shrink-0" />
          <p className="text-sm font-medium">{message}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Bank Name</label>
            <input
              required
              type="text"
              placeholder="e.g. Global Bank"
              className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition"
              value={formData.name}
              onChange={e => {
                const name = e.target.value;
                let bankId = formData.bankId;
                if (!isEditing && !isBankIdEdited) {
                  bankId = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                }
                setFormData({ ...formData, name, bankId });
              }}
            />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Bank ID {isEditing ? '(Read Only)' : '(Generated)'}</label>
            <input
              required
              disabled={isEditing}
              type="text"
              placeholder="e.g. GLOBAL-BANK-001"
              className={`w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition ${isEditing ? 'bg-gray-50 text-gray-500' : ''}`}
              value={formData.bankId}
              onChange={e => {
                setIsBankIdEdited(true);
                setFormData({ ...formData, bankId: e.target.value.toUpperCase().replace(/\s/g, '_') });
              }}
            />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Currency</label>
            <div className="relative">
              {!isCustomCurrency ? (
                  <StyledSelect
                    className="border-gray-300 rounded-xl px-4 py-3 border focus:ring-2 focus:ring-blue-500"
                    value={formData.currencyCode}
                    onChange={e => {
                      if (e.target.value === 'OTHER') {
                        setIsCustomCurrency(true);
                      } else {
                        setFormData({ ...formData, currencyCode: e.target.value });
                      }
                    }}
                  >
                    <option value="USD">USD - US Dollar</option>
                    <option value="EUR">EUR - Euro</option>
                    <option value="GBP">GBP - British Pound</option>
                    <option value="JPY">JPY - Japanese Yen</option>
                    <option value="OTHER">Other (Enter code...)</option>
                  </StyledSelect>
              ) : (
                <div className="flex space-x-2">
                  <input
                    autoFocus
                    type="text"
                    placeholder="e.g. AUD"
                    className="flex-1 px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
                    value={customCurrency}
                    onChange={e => setCustomCurrency(e.target.value.toUpperCase())}
                  />
                  <button
                    type="button"
                    onClick={() => setIsCustomCurrency(false)}
                    className="px-3 text-sm text-blue-600 hover:text-blue-800"
                  >
                    Reset
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">OIDC Issuer URL</label>
          <p className="text-xs text-gray-500 mb-2">
            The discovery URL of your Identity Provider.
            <br />Example (Entra ID): <code className="bg-gray-100 px-1 rounded">https://login.microsoftonline.com/&#123;tenant-id&#125;/v2.0</code>
          </p>
          <input
            required
            type="text"
            placeholder="https://login.microsoftonline.com/..."
            className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
            value={formData.issuerUrl}
            onChange={e => setFormData({ ...formData, issuerUrl: e.target.value })}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Client ID</label>
            <p className="text-xs text-gray-500 mb-2">
              The Application (client) ID registered in your IDP.
            </p>
            <input
              required
              type="text"
              placeholder="e.g. 12345678-...90ab"
              className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
              value={formData.clientId}
              onChange={e => setFormData({ ...formData, clientId: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Client Secret (Optional)</label>
            <p className="text-xs text-gray-500 mb-2">
              The Application Secret if your IDP requires it.
            </p>
            <div className="relative">
              <input
                type={showSecret ? "text" : "password"}
                placeholder="••••••••••••"
                className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition pr-12"
                value={formData.clientSecret}
                onChange={e => setFormData({ ...formData, clientSecret: e.target.value })}
              />
              <button
                type="button"
                onClick={() => setShowSecret(!showSecret)}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-2 hover:bg-gray-100 rounded-lg text-gray-400"
              >
                {showSecret ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Admin Name</label>
            <input
              required
              type="text"
              placeholder="Full Name"
              className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
              value={formData.adminName}
              onChange={e => setFormData({ ...formData, adminName: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Admin Email</label>
            <input
              required
              type="email"
              placeholder="email@bank.com"
              className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
              value={formData.adminEmail}
              onChange={e => setFormData({ ...formData, adminEmail: e.target.value })}
            />
          </div>
        </div>

        {!isAdmin && (
          <div className="bg-blue-50 p-6 rounded-2xl border border-blue-100 flex items-center justify-between">
            <div>
              <p className="text-sm font-bold text-blue-900 mb-1">Security Check</p>
              <p className="text-blue-700 text-lg font-mono">{captcha.question}</p>
            </div>
            <input
              required
              type="text"
              placeholder="?"
              className="w-20 px-4 py-3 rounded-xl border border-blue-200 focus:ring-2 focus:ring-blue-500 outline-none text-center font-bold"
              value={formData.captchaAnswer}
              onChange={e => setFormData({ ...formData, captchaAnswer: e.target.value })}
            />
          </div>
        )}

        <div className="flex space-x-4">
          {isAdmin && (
            <button
              type="button"
              onClick={() => navigate('/banks')}
              className="flex-1 px-8 py-4 border-2 border-gray-100 rounded-xl font-bold text-gray-400 hover:bg-gray-50 transition uppercase tracking-widest text-sm"
            >
              Cancel
            </button>
          )}
          <button
            disabled={loading}
            type="submit"
            className="flex-[2] bg-blue-600 text-white py-4 rounded-xl font-bold text-lg hover:bg-blue-700 transition shadow-lg disabled:opacity-50 flex items-center justify-center"
          >
            {loading ? <><Loader2 className="h-5 w-5 mr-2 animate-spin" /> {isEditing ? 'Updating...' : 'Submitting...'}</> :
             isEditing ? <><Save className="h-5 w-5 mr-2" /> Update Bank</> : 'Submit Request'}
          </button>
        </div>
      </form>
    </div>
  );

  if (isAdmin) {
    return <div className="p-8">{renderForm()}</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col md:flex-row">
      {/* Information Panel */}
      <div className="md:w-1/3 bg-blue-900 text-white p-12 flex flex-col">
        <div className="flex items-center space-x-2 mb-12 cursor-pointer" onClick={() => navigate('/')}>
          <img src="/logo-plexus.svg" alt="Plexus Logo" className="h-8 w-8 brightness-0 invert" />
          <span className="text-2xl font-bold tracking-tight">Plexus</span>
        </div>

        <div className="flex-1">
          <h1 className="text-4xl font-bold mb-8 leading-tight">Join the Modern Banking Revolution</h1>
          <p className="text-blue-100 text-lg mb-10 leading-relaxed">
            Plexus provides a flexible, component-based engine for managing global banking products and pricing.
          </p>

          <div className="space-y-8">
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><Rocket className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Rapid Onboarding</h3>
                <p className="text-blue-200 text-sm">Onboard your bank in minutes with our secret-less OIDC integration.</p>
              </div>
            </div>
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><ShieldCheck className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Secure Isolation</h3>
                <p className="text-blue-200 text-sm">Row-level data isolation ensures your product data is never exposed to other tenants.</p>
              </div>
            </div>
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><Info className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Flexible Pricing</h3>
                <p className="text-blue-200 text-sm">Manage complex tiered pricing and dynamic fees via a powerful rules engine.</p>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-12 pt-8 border-t border-blue-800 text-blue-300 text-sm">
          © 2024 Plexus Bank Engine. All rights reserved.
        </div>
      </div>

      {/* Form Panel */}
      <div className="flex-1 p-8 md:p-20 flex items-center justify-center relative">
        {renderForm()}
      </div>
    </div>
  );
};

export default OnboardingPage;
