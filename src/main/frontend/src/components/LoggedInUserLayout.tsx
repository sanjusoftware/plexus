import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useHasPermission } from '../hooks/useHasPermission';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Cpu, LayoutDashboard, Building2, Package, LogOut, User as UserIcon, List, Database, Tag, Shield
} from 'lucide-react';

interface LoggedInUserLayoutProps {
  children: React.ReactNode;
}

const LoggedInUserLayout: React.FC<LoggedInUserLayoutProps> = ({ children }) => {
  const { user, logout, bankId } = useAuth();
  const { hasPermission } = useHasPermission();
  const navigate = useNavigate();
  const location = useLocation();

  const roles = (user?.roles as string[]) || [];

  const navItems = [
    {
      label: 'Dashboard',
      icon: LayoutDashboard,
      path: '/dashboard',
      show: true
    },
    {
      label: 'Bank Management',
      icon: Building2,
      path: '/banks',
      show: hasPermission({ action: 'GET', path: '/api/v1/banks' })
    },
    {
      label: 'Product Types',
      icon: List,
      path: '/product-types',
      show: hasPermission({ action: 'GET', path: '/api/v1/product-types' })
    },
    {
      label: 'Pricing Metadata',
      icon: Database,
      path: '/pricing-metadata',
      show: hasPermission({ action: 'GET', path: '/api/v1/pricing-metadata' })
    },
    {
      label: 'Pricing Components',
      icon: Tag,
      path: '/pricing-components',
      show: hasPermission({ action: 'GET', path: '/api/v1/pricing-components' })
    },
    {
      label: 'Product Catalog',
      icon: Package,
      path: '/products',
      show: hasPermission({ action: 'GET', path: '/api/v1/products' })
    },
    {
      label: 'Roles & Permissions',
      icon: Shield,
      path: '/roles',
      show: hasPermission({ action: 'GET', path: '/api/v1/roles' })
    }
  ];

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <aside className="w-64 bg-blue-900 text-white flex-shrink-0 relative">
        <div className="p-6 flex items-center space-x-2 border-b border-blue-800 cursor-pointer" onClick={() => navigate('/dashboard')}>
          <Cpu className="h-8 w-8 text-blue-400" />
          <span className="text-xl font-bold tracking-tight">Plexus</span>
        </div>
        <nav className="mt-6 px-4 space-y-2">
          {navItems.filter(item => item.show).map((item) => (
            <button
              key={item.label}
              onClick={() => navigate(item.path)}
              className={`flex items-center space-x-3 w-full p-3 rounded-lg transition ${
                location.pathname === item.path ? 'bg-blue-800' : 'hover:bg-blue-800'
              }`}
            >
              <item.icon className={`h-5 w-5 ${location.pathname === item.path ? 'text-blue-300' : 'text-blue-300'}`} />
              <span className={location.pathname === item.path ? 'font-medium' : ''}>{item.label}</span>
            </button>
          ))}
        </nav>
        <div className="absolute bottom-0 w-64 p-6 border-t border-blue-800">
          <button
            onClick={logout}
            className="flex items-center space-x-3 text-blue-300 hover:text-white transition w-full"
          >
            <LogOut className="h-5 w-5" />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="bg-white border-b px-8 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold text-gray-900 capitalize">
            {user?.bankName || bankId}
          </h1>
          <div className="flex items-center space-x-4">
            <div className="text-right">
              <p className="text-sm font-semibold text-gray-900">{user?.name || user?.sub}</p>
              <p className="text-xs text-gray-500 mb-0.5">{user?.email}</p>
              <p className="text-[10px] text-gray-400 font-medium uppercase tracking-wider">{roles.join(', ')}</p>
            </div>
            <div className="h-10 w-10 rounded-full bg-blue-100 flex items-center justify-center overflow-hidden border-2 border-white shadow-sm">
              {user?.picture ? (
                <img src={user.picture} alt={user.name} className="h-full w-full object-cover" />
              ) : (
                <UserIcon className="h-6 w-6 text-blue-600" />
              )}
            </div>
          </div>
        </header>

        {/* Content Area */}
        <section className="flex-1 overflow-y-auto p-8">
          {children}
        </section>
      </main>
    </div>
  );
};

export default LoggedInUserLayout;
