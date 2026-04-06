import React, { useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert, ShieldCheck, X } from 'lucide-react';

const GlobalToast: React.FC = () => {
  const { toast, setToast } = useAuth();

  useEffect(() => {
    if (toast && toast.type === 'success') {
      const timer = setTimeout(() => {
        setToast(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [toast, setToast]);

  if (!toast) return null;

  return (
    <div className={`mb-4 p-3 rounded-xl shadow-lg border flex items-center justify-between animate-in fade-in slide-in-from-top-4 duration-500 relative z-50 ${
      toast.type === 'error' ? 'bg-red-50 border-red-200' : 'bg-green-50 border-green-200'
    }`}>
      <div className={`flex items-center space-x-3 ${
        toast.type === 'error' ? 'text-red-800' : 'text-green-800'
      }`}>
        <div className={`p-1.5 rounded-lg ${toast.type === 'error' ? 'bg-red-100 text-red-600' : 'bg-green-100 text-green-600'}`}>
          {toast.type === 'error' ? (
            <ShieldAlert className="h-5 w-5" />
          ) : (
            <ShieldCheck className="h-5 w-5" />
          )}
        </div>
        <div>
          <h3 className="font-bold text-sm">
            {toast.type === 'error' ? 'Error' : 'Success'}
          </h3>
          <p className="text-xs opacity-90">{toast.message}</p>
        </div>
      </div>
      <button
        onClick={() => setToast(null)}
        className={`p-1.5 rounded-full transition ${
          toast.type === 'error' ? 'hover:bg-red-100 text-red-400' : 'hover:bg-green-100 text-green-400'
        }`}
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
};

export default GlobalToast;
