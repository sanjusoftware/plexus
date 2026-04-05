import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useHasPermission } from '../hooks/useHasPermission';
import { useNavigate, useLocation } from 'react-router-dom';
import Breadcrumbs from './Breadcrumbs';
import {
  Cpu, LayoutDashboard, Building2, Package, LogOut, User as UserIcon, List, Database, Tag, Layers, Shield, ChevronLeft, ChevronRight, ShieldCheck, ExternalLink
} from 'lucide-react';

interface LoggedInUserLayoutProps {
  children: React.ReactNode;
}

const LoggedInUserLayout: React.FC<LoggedInUserLayoutProps> = ({ children }) => {
  const { user, logout, bankId } = useAuth();
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
      <aside className={`${isCollapsed ? 'w-20' : 'w-60'} bg-blue-900 text-white flex-shrink-0 relative transition-all duration-300 flex flex-col`}>
        <div className={`${isCollapsed ? 'p-4' : 'p-5'} flex ${isCollapsed ? 'flex-col space-y-4 px-0' : 'flex-row justify-between items-center'} border-b border-blue-800`}>
          <div className={`flex items-center ${isCollapsed ? 'justify-center w-full' : 'space-x-2'} cursor-pointer overflow-hidden`} onClick={() => navigate('/dashboard')}>
            <Cpu className="h-7 w-7 text-blue-400 flex-shrink-0" />
            {!isCollapsed && <span className="text-lg font-bold tracking-tight whitespace-nowrap">Plexus</span>}
          </div>
          <button
            onClick={() => setIsCollapsed(!isCollapsed)}
            className={`p-1 hover:bg-blue-800 rounded-lg transition-colors ${isCollapsed ? 'mx-auto' : ''}`}
          >
            {isCollapsed ? <ChevronRight className="h-4 w-4 text-blue-300" /> : <ChevronLeft className="h-4 w-4 text-blue-300" />}
          </button>
        </div>
        <nav className="mt-4 px-3 space-y-1 flex-1">
          {navItems.filter(item => item.show).map((item) => (
            <button
              key={item.label}
              onClick={() => navigate(item.path)}
              title={isCollapsed ? item.label : undefined}
              className={`flex items-center ${isCollapsed ? 'justify-center' : 'space-x-2.5'} w-full p-2.5 rounded-lg transition ${
                location.pathname === item.path ? 'bg-blue-800' : 'hover:bg-blue-800'
              }`}
            >
              <item.icon className={`h-4 w-4 flex-shrink-0 ${location.pathname === item.path ? 'text-blue-300' : 'text-blue-300'}`} />
              {!isCollapsed && <span className={`${location.pathname === item.path ? 'font-medium' : ''} text-sm whitespace-nowrap`}>{item.label}</span>}
            </button>
          ))}
        </nav>

        {/* Bottom Menu Item with Divider */}
        <div className="mt-auto px-4 pb-6 space-y-2">
          <div className="border-t border-blue-800 my-4" />
          <a
            href="/swagger-ui/index.html"
            target="_blank"
            rel="noopener noreferrer"
            title={isCollapsed ? 'Swagger API' : undefined}
            className={`flex items-center ${isCollapsed ? 'justify-center' : 'space-x-3'} w-full p-3 rounded-lg transition hover:bg-blue-800 text-blue-300`}
          >
            <ExternalLink className="h-5 w-5 flex-shrink-0" />
            {!isCollapsed && <span className="text-white whitespace-nowrap">Swagger API</span>}
          </a>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden relative">
        {/* Header */}
        <header className="bg-white border-b px-6 py-3 flex justify-between items-center z-10">
          <h1 className="text-xl font-bold text-gray-900 capitalize truncate mr-4">
            {user?.bankName || bankId}
          </h1>
          <div className="flex items-center space-x-3">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-semibold text-gray-900">{user?.name || user?.sub}</p>
              <p className="text-[10px] text-gray-500 mb-0.5">{user?.email}</p>
              <p className="text-[10px] text-gray-400 font-medium uppercase tracking-wider">{roles.join(', ')}</p>
            </div>
            <div className="h-9 w-9 rounded-full bg-blue-100 flex items-center justify-center overflow-hidden border-2 border-white shadow-sm">
              {user?.picture ? (
                <img src={user.picture} alt={user.name} className="h-full w-full object-cover" />
              ) : (
                <UserIcon className="h-5.5 w-5.5 text-blue-600" />
              )}
            </div>
            <button
              onClick={logout}
              className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
              title="Logout"
            >
              <LogOut className="h-4.5 w-4.5" />
            </button>
          </div>
        </header>

        {/* Content Area */}
        <section className="flex-1 overflow-y-auto p-6">
          <Breadcrumbs />
          {children}
        </section>
      </main>
    </div>
  );
};

export default LoggedInUserLayout;
