import React from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert, ShieldCheck, X } from 'lucide-react';

const GlobalToast: React.FC = () => {
  const { toast, setToast } = useAuth();

  if (!toast) return null;

  return (
    <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[9999] animate-in fade-in slide-in-from-top-4 duration-300">
      <div className={`flex items-center space-x-3 p-4 rounded-xl shadow-2xl border ${
        toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-green-50 border-green-200 text-green-800'
      }`}>
        <div className={`p-2 rounded-lg ${toast.type === 'error' ? 'bg-red-100' : 'bg-green-100'}`}>
          {toast.type === 'error' ? (
            <ShieldAlert className="h-5 w-5" />
          ) : (
            <ShieldCheck className="h-5 w-5" />
          )}
        </div>
        <div className="flex-1 pr-4 whitespace-nowrap">
          <p className="text-sm font-bold">
            {toast.type === 'error' ? 'Error' : 'Success'}
          </p>
          <p className="text-xs opacity-90">{toast.message}</p>
        </div>
        <button
          onClick={() => setToast(null)}
          className="p-1 hover:bg-black/5 rounded-full transition"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
};

export default GlobalToast;
