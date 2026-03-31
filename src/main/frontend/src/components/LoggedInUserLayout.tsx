import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useHasPermission } from '../hooks/useHasPermission';
import { useNavigate, useLocation } from 'react-router-dom';
import Breadcrumbs from './Breadcrumbs';
import {
  Cpu, LayoutDashboard, Building2, Package, LogOut, User as UserIcon, List, Database, Tag, Layers, Shield, ChevronLeft, ChevronRight, ShieldAlert, X, ShieldCheck
} from 'lucide-react';

interface LoggedInUserLayoutProps {
  children: React.ReactNode;
}

const LoggedInUserLayout: React.FC<LoggedInUserLayoutProps> = ({ children }) => {
  const { user, logout, bankId, toast, setToast } = useAuth();
  const { hasPermission } = useHasPermission();
  const navigate = useNavigate();
  const location = useLocation();
  const [isCollapsed, setIsCollapsed] = React.useState(false);

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
      label: 'Feature Components',
      icon: ShieldCheck,
      path: '/features',
      show: hasPermission({ action: 'GET', path: '/api/v1/features' })
    },
    {
      label: 'Pricing Tiers',
      icon: Layers,
      path: '/pricing-tiers',
      show: hasPermission({ action: 'GET', path: '/api/v1/pricing-tiers' })
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
      <aside className={`${isCollapsed ? 'w-20' : 'w-64'} bg-blue-900 text-white flex-shrink-0 relative transition-all duration-300 flex flex-col`}>
        <div className={`p-6 flex ${isCollapsed ? 'flex-col space-y-4 px-0' : 'flex-row justify-between items-center'} border-b border-blue-800`}>
          <div className={`flex items-center ${isCollapsed ? 'justify-center w-full' : 'space-x-2'} cursor-pointer overflow-hidden`} onClick={() => navigate('/dashboard')}>
            <Cpu className="h-8 w-8 text-blue-400 flex-shrink-0" />
            {!isCollapsed && <span className="text-xl font-bold tracking-tight whitespace-nowrap">Plexus</span>}
          </div>
          <button
            onClick={() => setIsCollapsed(!isCollapsed)}
            className={`p-1 hover:bg-blue-800 rounded-lg transition-colors ${isCollapsed ? 'mx-auto' : ''}`}
          >
            {isCollapsed ? <ChevronRight className="h-5 w-5 text-blue-300" /> : <ChevronLeft className="h-5 w-5 text-blue-300" />}
          </button>
        </div>
        <nav className="mt-6 px-4 space-y-2 flex-1">
          {navItems.filter(item => item.show).map((item) => (
            <button
              key={item.label}
              onClick={() => navigate(item.path)}
              title={isCollapsed ? item.label : undefined}
              className={`flex items-center ${isCollapsed ? 'justify-center' : 'space-x-3'} w-full p-3 rounded-lg transition ${
                location.pathname === item.path ? 'bg-blue-800' : 'hover:bg-blue-800'
              }`}
            >
              <item.icon className={`h-5 w-5 flex-shrink-0 ${location.pathname === item.path ? 'text-blue-300' : 'text-blue-300'}`} />
              {!isCollapsed && <span className={`${location.pathname === item.path ? 'font-medium' : ''} whitespace-nowrap`}>{item.label}</span>}
            </button>
          ))}
        </nav>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden relative">
        {/* Header */}
        <header className="bg-white border-b px-8 py-4 flex justify-between items-center z-10">
          <h1 className="text-2xl font-bold text-gray-900 capitalize truncate mr-4">
            {user?.bankName || bankId}
          </h1>
          <div className="flex items-center space-x-4">
            <div className="text-right hidden sm:block">
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
            <button
              onClick={logout}
              className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
              title="Logout"
            >
              <LogOut className="h-5 w-5" />
            </button>
          </div>
        </header>

        {/* Global Toast Notification - Centered in main content area */}
        {toast && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-50 animate-in fade-in slide-in-from-top-4 duration-300">
            <div className={`flex items-center space-x-3 p-4 rounded-xl shadow-2xl border ${
              toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-green-50 border-green-200 text-green-800'
            }`}>
              <div className={`p-2 rounded-lg ${toast.type === 'error' ? 'bg-red-100' : 'bg-green-100'}`}>
                <ShieldAlert className="h-5 w-5" />
              </div>
              <div className="flex-1 pr-4 whitespace-nowrap">
                <p className="text-sm font-bold">Access Denied</p>
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
        )}

        {/* Content Area */}
        <section className="flex-1 overflow-y-auto p-8">
          <Breadcrumbs />
          {children}
        </section>
      </main>
    </div>
  );
};

export default LoggedInUserLayout;
