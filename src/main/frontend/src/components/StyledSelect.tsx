import React, { SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';

interface StyledSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  containerClassName?: string;
}

const StyledSelect: React.FC<StyledSelectProps> = ({ className, containerClassName, children, ...props }) => {
  return (
    <div className={`relative ${containerClassName || 'w-full'}`}>
      <select
        {...props}
        className={`appearance-none w-full border-2 border-gray-100 rounded-2xl p-4 bg-white font-black transition focus:ring-4 focus:ring-blue-500/10 focus:border-blue-500 pr-12 ${className || ''}`}
      >
        {children}
      </select>
      <div className="absolute inset-y-0 right-0 flex items-center pr-4 pointer-events-none">
        <ChevronDown className="h-5 w-5 text-gray-400" strokeWidth={3} />
      </div>
    </div>
  );
};

export default StyledSelect;
