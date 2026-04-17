import React, { useState, useEffect } from 'react';
import { Cpu, ArrowLeft, Loader2, ShieldAlert } from 'lucide-react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
  const [bankId, setBankId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated, loading: authLoading } = useAuth();

  useEffect(() => {
    if (!authLoading && isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, authLoading, navigate]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const errorParam = params.get('error');
    if (errorParam === 'auth_failed') {
      setError('Access Denied: Your account has no permissions assigned for this bank. Please contact your administrator.');
    }
  }, [location]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankId) return;

    setLoading(true);
    setError('');

    try {
      // Trigger OIDC redirect via backend BFF
      await login(bankId);
    } catch (err) {
      setError('Invalid Bank ID or bank not configured.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <Cpu className="h-12 w-12 text-blue-600" />
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-blue-900">
          Sign in to your account
        </h2>
        <p className="mt-2 text-center text-sm text-gray-600">
          Enter your Bank ID to be redirected to your secure login provider.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
          <form className="space-y-6" onSubmit={handleLogin}>
            <div>
              <label htmlFor="bankId" className="block text-sm font-medium text-gray-700">
                Bank Identifier (e.g., SYSTEM, GLOBAL-BANK-001)
              </label>
              <div className="mt-1">
                <input
                  id="bankId"
                  name="bankId"
                  type="text"
                  required
                  value={bankId}
                  onChange={(e) => setBankId(e.target.value.toUpperCase())}
                  className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                  placeholder="MY-BANK-ID"
                />
              </div>
              {error && (
                <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-md flex items-start space-x-2">
                  <ShieldAlert className="h-5 w-5 text-red-600 mt-0.5 flex-shrink-0" />
                  <p className="text-sm text-red-700">{error}</p>
                </div>
              )}
            </div>

            <div>
              <button
                type="submit"
                disabled={loading || !bankId}
                className="admin-primary-btn w-full justify-center py-2.5 text-sm uppercase tracking-widest"
              >
                {loading ? <Loader2 className="animate-spin h-5 w-5" /> : 'Continue to Login'}
              </button>
            </div>
          </form>

          <div className="mt-6">
            <button
              onClick={() => navigate('/')}
              className="w-full flex justify-center items-center px-4 py-2 text-sm text-gray-600 hover:text-blue-600 transition"
            >
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to landing page
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
