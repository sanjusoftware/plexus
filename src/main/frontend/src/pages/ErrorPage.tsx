import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Home, AlertCircle, RefreshCcw, Cpu, ShieldAlert } from 'lucide-react';

interface ErrorState {
  status?: number;
  message?: string;
  timestamp?: string;
}

const ErrorPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const queryParams = new URLSearchParams(location.search);
  const state = location.state as ErrorState;

  const status = Number(queryParams.get('status')) || state?.status || 404;
  const message = queryParams.get('message') || state?.message || (status === 404 ? "The page you are looking for doesn't exist or has been moved." : "An unexpected error occurred on our server.");
  const timestamp = queryParams.get('timestamp') || state?.timestamp || new Date().toISOString();

  const is404 = status === 404;
  const isAuthError = status === 401 || status === 403 || (status === 400 && message.toLowerCase().includes('bank'));

  const getHeading = () => {
    if (is404) return "Oops! Page Not Found";
    if (isAuthError) return status === 403 ? "Access Denied" : "Authentication Error";
    return "Internal Server Error";
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header Branding */}
      <header className="p-6 flex items-center space-x-2">
        <Cpu className="h-8 w-8 text-blue-600" />
        <span className="text-xl font-bold tracking-tight text-blue-900">Plexus</span>
      </header>

      <main className="flex-1 flex flex-col items-center justify-center p-6 text-center">
        {/* Animated SVG Illustration */}
        <div className="relative mb-8 group">
          <div className="absolute -inset-1 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full blur opacity-25 group-hover:opacity-50 transition duration-1000 group-hover:duration-200"></div>
          <div className="relative bg-white p-8 rounded-full shadow-2xl">
            {is404 ? (
              <svg className="w-40 h-40 text-blue-600 animate-pulse" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <path d="M16 16s-1.5-2-4-2-4 2-4 2" />
                <line x1="9" y1="9" x2="9.01" y2="9" />
                <line x1="15" y1="9" x2="15.01" y2="9" />
              </svg>
            ) : status === 403 ? (
              <ShieldAlert className="w-40 h-40 text-orange-500 animate-pulse" strokeWidth={1} />
            ) : (
              <svg className="w-40 h-40 text-red-500 animate-bounce" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
            )}
          </div>
        </div>

        <h1 className="text-6xl font-extrabold text-blue-900 mb-4 tracking-tighter">
          {status}
        </h1>
        <h2 className="text-2xl font-bold text-gray-800 mb-2">
          {getHeading()}
        </h2>
        <p className="text-gray-600 max-w-md mb-8">
          {message}
        </p>

        <div className="bg-white border rounded-xl p-6 shadow-sm mb-8 w-full max-w-lg text-left overflow-hidden">
          <div className="flex items-center space-x-2 text-gray-500 mb-2 border-b pb-2">
            <AlertCircle className="h-4 w-4" />
            <span className="text-xs font-semibold uppercase tracking-wider">Technical Details</span>
          </div>
          <div className="space-y-2">
            <div className="flex justify-between">
              <span className="text-xs font-mono text-gray-400">Timestamp:</span>
              <span className="text-xs font-mono text-gray-600">{timestamp}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-xs font-mono text-gray-400">Status Type:</span>
              <span className="text-xs font-mono text-gray-600">
                {is404 ? 'Not Found' : (isAuthError ? 'Authentication/Authorization' : 'Server Error')}
              </span>
            </div>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row space-y-4 sm:space-y-0 sm:space-x-4">
          <button
            onClick={() => navigate('/')}
            className="flex items-center justify-center space-x-2 bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-xl font-bold transition transform hover:-translate-y-1 shadow-lg"
          >
            <Home className="h-5 w-5" />
            <span>Go Back Home</span>
          </button>
          {!is404 && (
            <button
              onClick={() => window.location.reload()}
              className="flex items-center justify-center space-x-2 bg-white hover:bg-gray-50 text-gray-700 border-2 px-8 py-3 rounded-xl font-bold transition shadow-sm"
            >
              <RefreshCcw className="h-5 w-5" />
              <span>Try Again</span>
            </button>
          )}
        </div>
      </main>

      <footer className="p-8 text-center text-gray-400 text-sm">
        &copy; {new Date().getFullYear()} Plexus Banking Product Engine. All rights reserved.
      </footer>
    </div>
  );
};

export default ErrorPage;
