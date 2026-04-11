import React from 'react';
import { AlertTriangle, X } from 'lucide-react';
import { useEscapeKey } from '../hooks/useEscapeKey';

interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  children?: React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'warning' | 'info';
  confirmDisabled?: boolean;
}

const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  children,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
  confirmDisabled = false
}) => {
  useEscapeKey(onClose, isOpen);

  if (!isOpen) return null;

  const variantClasses = {
    danger: 'bg-red-600 hover:bg-red-700 text-white',
    warning: 'bg-amber-500 hover:bg-amber-600 text-white',
    info: 'bg-blue-600 hover:bg-blue-700 text-white'
  };

  const iconClasses = {
    danger: 'text-red-600 bg-red-50',
    warning: 'text-amber-600 bg-amber-50',
    info: 'text-blue-600 bg-blue-50'
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[100] p-4">
      <div className="bg-white rounded-xl shadow-2xl max-w-md w-full overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="px-5 py-3 flex justify-between items-center border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">{title}</h3>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-lg transition">
            <X className="w-4 h-4 text-gray-400" />
          </button>
        </div>

        <div className="p-6">
          <div className="flex items-center space-x-4 mb-5">
            <div className={`p-2.5 rounded-xl ${iconClasses[variant]}`}>
              <AlertTriangle className="w-5 h-5" />
            </div>
            <p className="text-sm text-gray-600 leading-relaxed font-medium">
              {message}
            </p>
          </div>

          {children && <div className="mb-5">{children}</div>}

          <div className="flex space-x-3">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2 rounded-lg font-bold text-gray-600 bg-gray-50 hover:bg-gray-100 transition border border-gray-200 text-sm"
            >
              {cancelText}
            </button>
            <button
              onClick={() => {
                if (confirmDisabled) return;
                onConfirm();
                onClose();
              }}
              disabled={confirmDisabled}
              className={`flex-1 px-4 py-2 rounded-lg font-bold transition shadow-md text-sm ${confirmDisabled ? 'bg-gray-200 text-gray-400 cursor-not-allowed shadow-none' : variantClasses[variant]}`}
            >
              {confirmText}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ConfirmationModal;
