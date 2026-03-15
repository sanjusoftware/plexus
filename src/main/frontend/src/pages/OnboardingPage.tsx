import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Cpu, ShieldCheck, Rocket, Info, CheckCircle2, AlertCircle } from 'lucide-react';
import axios from 'axios';

const OnboardingPage = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    bankId: '',
    issuerUrl: '',
    clientId: '',
    adminName: '',
    adminEmail: '',
    currencyCode: 'USD',
    captchaAnswer: ''
  });

  const [captcha, setCaptcha] = useState({ question: '', id: '' });
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [message, setMessage] = useState('');

  useEffect(() => {
    fetchCaptcha();
  }, []);

  const fetchCaptcha = async () => {
    try {
      const response = await axios.get('/api/v1/public/onboarding/captcha');
      setCaptcha(response.data);
    } catch (err) {
      console.error('Failed to fetch captcha');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setStatus('idle');
    try {
      await axios.post('/api/v1/public/onboarding/submit', formData);
      setStatus('success');
      setMessage('Your onboarding request has been submitted successfully! A system administrator will review your request shortly.');
    } catch (err: any) {
      setStatus('error');
      setMessage(err.response?.data?.message || 'Failed to submit request. Please check your inputs and captcha.');
      fetchCaptcha(); // Refresh captcha on error
    } finally {
      setLoading(false);
    }
  };

  if (status === 'success') {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white rounded-3xl shadow-xl p-10 text-center">
          <CheckCircle2 className="h-20 w-20 text-green-500 mx-auto mb-6" />
          <h2 className="text-3xl font-bold text-gray-900 mb-4">Request Submitted!</h2>
          <p className="text-gray-600 mb-8">{message}</p>
          <button
            onClick={() => navigate('/')}
            className="w-full bg-blue-600 text-white py-3 rounded-xl font-bold hover:bg-blue-700 transition"
          >
            Return to Home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col md:flex-row">
      {/* Information Panel */}
      <div className="md:w-1/3 bg-blue-900 text-white p-12 flex flex-col">
        <div className="flex items-center space-x-2 mb-12">
          <Cpu className="h-8 w-8 text-blue-400" />
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
      <div className="flex-1 p-8 md:p-20 flex items-center justify-center">
        <div className="max-w-xl w-full">
          <h2 className="text-3xl font-bold text-gray-900 mb-2">Get Started</h2>
          <p className="text-gray-500 mb-10">Fill out the form below to submit an onboarding request for your institution.</p>

          {status === 'error' && (
            <div className="mb-8 p-4 bg-red-50 border border-red-200 rounded-xl flex items-center text-red-700">
              <AlertCircle className="h-5 w-5 mr-3 flex-shrink-0" />
              <p className="text-sm font-medium">{message}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-2">Bank ID</label>
                <input
                  required
                  type="text"
                  placeholder="e.g. GLOBAL-BANK-001"
                  className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition"
                  value={formData.bankId}
                  onChange={e => setFormData({ ...formData, bankId: e.target.value.toUpperCase().replace(/\s/g, '-') })}
                />
              </div>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-2">Currency</label>
                <select
                  className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
                  value={formData.currencyCode}
                  onChange={e => setFormData({ ...formData, currencyCode: e.target.value })}
                >
                  <option value="USD">USD - US Dollar</option>
                  <option value="EUR">EUR - Euro</option>
                  <option value="GBP">GBP - British Pound</option>
                  <option value="JPY">JPY - Japanese Yen</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">OIDC Issuer URL</label>
              <input
                required
                type="url"
                placeholder="https://login.microsoftonline.com/..."
                className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
                value={formData.issuerUrl}
                onChange={e => setFormData({ ...formData, issuerUrl: e.target.value })}
              />
            </div>

            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">Client ID (optional)</label>
              <input
                type="text"
                placeholder="The application ID in your IDP"
                className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none transition"
                value={formData.clientId}
                onChange={e => setFormData({ ...formData, clientId: e.target.value })}
              />
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

            <button
              disabled={loading}
              type="submit"
              className="w-full bg-blue-600 text-white py-4 rounded-xl font-bold text-lg hover:bg-blue-700 transition shadow-lg disabled:opacity-50 flex items-center justify-center"
            >
              {loading ? <><Loader2 className="h-5 w-5 mr-2 animate-spin" /> Submitting...</> : 'Submit Request'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

const Loader2 = ({ className }: { className?: string }) => (
  <svg className={className} xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 12a9 9 0 1 1-6.219-8.56" />
  </svg>
);

export default OnboardingPage;
