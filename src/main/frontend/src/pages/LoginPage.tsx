import React, { useState } from 'react';
import { Cpu, ArrowLeft, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
  const [bankId, setBankId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();

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
              {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
            </div>

            <div>
              <button
                type="submit"
                disabled={loading || !bankId}
                className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 transition"
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
