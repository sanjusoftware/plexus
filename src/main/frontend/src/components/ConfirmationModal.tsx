import React from 'react';
import { AlertTriangle, X } from 'lucide-react';

interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'warning' | 'info';
}

const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger'
}) => {
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
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[100] p-4 backdrop-blur-sm">
      <div className="bg-white rounded-3xl shadow-2xl max-w-md w-full overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="px-6 py-4 flex justify-between items-center border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full transition">
            <X className="h-5 w-5 text-gray-400" />
          </button>
        </div>

        <div className="p-8">
          <div className="flex items-center space-x-4 mb-6">
            <div className={`p-3 rounded-2xl ${iconClasses[variant]}`}>
              <AlertTriangle className="h-6 w-6" />
            </div>
            <p className="text-gray-600 leading-relaxed font-medium">
              {message}
            </p>
          </div>

          <div className="flex space-x-3">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-3.5 rounded-2xl font-bold text-gray-600 bg-gray-50 hover:bg-gray-100 transition border border-gray-200"
            >
              {cancelText}
            </button>
            <button
              onClick={() => {
                onConfirm();
                onClose();
              }}
              className={`flex-1 px-4 py-3.5 rounded-2xl font-bold transition shadow-lg ${variantClasses[variant]}`}
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
