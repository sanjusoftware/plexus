import React from 'react';
import { CheckCircle2, X } from 'lucide-react';
import { useEscapeKey } from '../hooks/useEscapeKey';

interface OnboardingSuccessModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  message: string;
}

const OnboardingSuccessModal: React.FC<OnboardingSuccessModalProps> = ({ isOpen, onClose, title, message }) => {
  useEscapeKey(onClose, isOpen);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-blue-900/40 flex items-center justify-center z-[100] p-4 animate-in fade-in duration-300">
      <div className="max-w-md w-full bg-white rounded-3xl shadow-2xl p-10 text-center relative border border-white/20">
        <button
          onClick={onClose}
          className="absolute right-6 top-6 p-2 text-gray-400 hover:bg-gray-100 rounded-xl transition"
        >
          <X className="h-5 w-5" />
        </button>

        <CheckCircle2 className="h-20 w-20 text-green-500 mx-auto mb-6" />
        <h2 className="text-3xl font-bold text-gray-900 mb-4">
          {title}
        </h2>
        <p className="text-gray-600 mb-8">{message}</p>

        <button
          onClick={onClose}
          className="w-full bg-blue-600 text-white py-3 rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-100"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
};

export default OnboardingSuccessModal;
