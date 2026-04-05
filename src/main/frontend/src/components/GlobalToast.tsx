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
    <div
      className="fixed z-[9999] transition-all duration-300 animate-in fade-in slide-in-from-top-2"
      style={{
        top: '28px',
        left: 'calc(50% + (var(--sidebar-width, 0px) / 2))',
        transform: 'translate(-50%, -50%)'
      } as React.CSSProperties}
    >
      <div className={`flex items-center space-x-2 px-2.5 py-1 rounded-lg shadow-lg border ${
        toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-green-50 border-green-200 text-green-800'
      }`}>
        <div className={`p-1 rounded-md ${toast.type === 'error' ? 'bg-red-100' : 'bg-green-100'}`}>
          {toast.type === 'error' ? (
            <ShieldAlert className="h-3.5 w-3.5" />
          ) : (
            <ShieldCheck className="h-3.5 w-3.5" />
          )}
        </div>
        <div className="flex-1 pr-1.5 whitespace-nowrap">
          <p className="text-[11px] font-bold leading-none mb-0.5">
            {toast.type === 'error' ? 'Error' : 'Success'}
          </p>
          <p className="text-[10px] opacity-90 leading-none">{toast.message}</p>
        </div>
        <button
          onClick={() => setToast(null)}
          className="p-0.5 hover:bg-black/5 rounded-full transition"
        >
          <X className="h-3 w-3" />
        </button>
      </div>
    </div>
  );
};

export default GlobalToast;
