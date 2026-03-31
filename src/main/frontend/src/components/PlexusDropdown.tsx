import React from 'react';
import { Menu, Transition } from '@headlessui/react';
import { ChevronDown } from 'lucide-react';

export interface PlexusDropdownItem {
  label: string;
  onClick?: () => void;
  icon?: React.ComponentType<{ className?: string }>;
  variant?: 'default' | 'danger';
}

interface PlexusDropdownProps {
  label: string;
  items: PlexusDropdownItem[];
  variant?: 'primary' | 'secondary' | 'ghost';
  icon?: React.ComponentType<{ className?: string }>;
}

const PlexusDropdown: React.FC<PlexusDropdownProps> = ({
  label,
  items,
  variant = 'primary',
  icon: Icon
}) => {
  const getButtonClasses = () => {
    switch (variant) {
      case 'primary':
        return 'bg-blue-600 text-white hover:bg-blue-700 shadow-lg shadow-blue-200';
      case 'secondary':
        return 'bg-white border-2 border-gray-100 text-gray-700 hover:border-gray-200 shadow-sm';
      case 'ghost':
        return 'bg-transparent text-gray-500 hover:bg-gray-100';
      default:
        return '';
    }
  };

  return (
    <Menu as="div" className="relative inline-block text-left">
      <div>
        <Menu.Button className={`inline-flex w-full items-center justify-center gap-x-2 rounded-2xl px-6 py-4 text-xs font-black uppercase tracking-widest transition ${getButtonClasses()}`}>
          {Icon && <Icon className="h-4 w-4" />}
          {label}
          <ChevronDown className="-mr-1 h-4 w-4 opacity-50" aria-hidden="true" />
        </Menu.Button>
      </div>

      <Transition
        as={React.Fragment}
        enter="transition ease-out duration-100"
        enterFrom="transform opacity-0 scale-95"
        enterTo="transform opacity-100 scale-100"
        leave="transition ease-in duration-75"
        leaveFrom="transform opacity-100 scale-100"
        leaveTo="transform opacity-0 scale-95"
      >
        <Menu.Items className="absolute right-0 z-50 mt-2 w-56 origin-top-right rounded-2xl bg-white p-2 shadow-2xl ring-1 ring-black ring-opacity-5 focus:outline-none border border-gray-100">
          <div className="space-y-1">
            {items.map((item, idx) => (
              <Menu.Item key={idx}>
                {({ active }) => (
                  <button
                    onClick={item.onClick}
                    className={`${
                      active
                        ? item.variant === 'danger' ? 'bg-red-50 text-red-600' : 'bg-blue-50 text-blue-600'
                        : item.variant === 'danger' ? 'text-red-500' : 'text-gray-700'
                    } group flex w-full items-center rounded-xl px-4 py-3 text-xs font-black uppercase tracking-widest transition`}
                  >
                    {item.icon && <item.icon className={`mr-3 h-4 w-4 ${active ? (item.variant === 'danger' ? 'text-red-600' : 'text-blue-600') : 'text-gray-400'}`} aria-hidden="true" />}
                    {item.label}
                  </button>
                )}
              </Menu.Item>
            ))}
          </div>
        </Menu.Items>
      </Transition>
    </Menu>
  );
};

export default PlexusDropdown;
