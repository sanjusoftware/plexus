import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import {
  Cpu, LayoutDashboard, Building2, Package, LogOut, User as UserIcon,
  ShieldCheck, Loader2, Plus, ArrowRight, ExternalLink
} from 'lucide-react';
import axios from 'axios';

const Dashboard = () => {
  const { user, logout, bankId, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');
  const isBankAdmin = authorities.includes('BANK_ADMIN');

  useEffect(() => {
    if (!authLoading && !user) {
      navigate('/login');
      return;
    }

    const fetchData = async () => {
      if (!user) return;
      setLoading(true);
      try {
        if (isSystemAdmin) {
          // System Admin sees all banks
          const response = await axios.get('/api/v1/banks');
          setData(response.data || []);
        } else {
          // Bank Admin sees their products
          const response = await axios.get('/api/v1/products');
          setData(response.data.content || []);
        }
      } catch (err) {
        console.error('Failed to fetch dashboard data:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [user, authLoading, navigate, isSystemAdmin]);

  if (authLoading || (user && loading && data.length === 0)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <aside className="w-64 bg-blue-900 text-white flex-shrink-0">
        <div className="p-6 flex items-center space-x-2 border-b border-blue-800">
          <Cpu className="h-8 w-8 text-blue-400" />
          <span className="text-xl font-bold tracking-tight">Plexus</span>
        </div>
        <nav className="mt-6 px-4 space-y-2">
          {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
          <a href="#" className="flex items-center space-x-3 bg-blue-800 p-3 rounded-lg">
            <LayoutDashboard className="h-5 w-5 text-blue-300" />
            <span className="font-medium">Dashboard</span>
          </a>
          {isSystemAdmin && (
            /* eslint-disable-next-line jsx-a11y/anchor-is-valid */
            <a href="#" className="flex items-center space-x-3 p-3 rounded-lg hover:bg-blue-800 transition">
              <Building2 className="h-5 w-5 text-blue-300" />
              <span>Bank Management</span>
            </a>
          )}
          {(isBankAdmin || !isSystemAdmin) && (
            /* eslint-disable-next-line jsx-a11y/anchor-is-valid */
            <a href="#" className="flex items-center space-x-3 p-3 rounded-lg hover:bg-blue-800 transition">
              <Package className="h-5 w-5 text-blue-300" />
              <span>Product Catalog</span>
            </a>
          )}
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
            {isSystemAdmin ? 'System Overview' : `${bankId} Dashboard`}
          </h1>
          <div className="flex items-center space-x-4">
            <div className="text-right">
              <p className="text-sm font-semibold text-gray-900">{user?.name || user?.sub}</p>
              <p className="text-xs text-gray-500">{authorities.join(', ')}</p>
            </div>
            <div className="h-10 w-10 rounded-full bg-blue-100 flex items-center justify-center">
              <UserIcon className="h-6 w-6 text-blue-600" />
            </div>
          </div>
        </header>

        {/* Content Area */}
        <section className="flex-1 overflow-y-auto p-8">
          {/* Welcome Card */}
          <div className="bg-white rounded-2xl shadow-sm border p-8 mb-8 flex justify-between items-center">
            <div>
              <h2 className="text-3xl font-bold text-blue-900 mb-2">Welcome back!</h2>
              <p className="text-gray-600">
                {isSystemAdmin
                  ? "Manage your global banking infrastructure and onboard new tenants."
                  : `Manage products and pricing rules for ${bankId}.`}
              </p>
            </div>
            <div className="hidden md:block">
               <ShieldCheck className="h-20 w-20 text-blue-100" />
            </div>
          </div>

          {/* Action Stats */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <div className="bg-white p-6 rounded-2xl border shadow-sm">
              <p className="text-gray-500 text-sm font-medium uppercase mb-1">Status</p>
              <h3 className="text-2xl font-bold text-green-600">Active</h3>
            </div>
            <div className="bg-white p-6 rounded-2xl border shadow-sm">
              <p className="text-gray-500 text-sm font-medium uppercase mb-1">Tenant ID</p>
              <h3 className="text-2xl font-bold text-blue-600">{bankId}</h3>
            </div>
            <div className="bg-white p-6 rounded-2xl border shadow-sm">
              <p className="text-gray-500 text-sm font-medium uppercase mb-1">Email</p>
              <h3 className="text-xl font-bold text-gray-700 truncate">{user?.email}</h3>
            </div>
          </div>

          {/* Data List */}
          <div className="bg-white rounded-2xl border shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b flex justify-between items-center bg-gray-50">
              <h3 className="font-bold text-gray-900">
                {isSystemAdmin ? 'Managed Banks' : 'Available Products'}
              </h3>
              <button className="flex items-center text-sm font-semibold text-blue-600 hover:text-blue-700 transition">
                <Plus className="h-4 w-4 mr-1" />
                {isSystemAdmin ? 'Add Bank' : 'New Product'}
              </button>
            </div>
            <div className="divide-y">
              {data.length === 0 ? (
                <div className="p-12 text-center text-gray-500">
                  No {isSystemAdmin ? 'banks' : 'products'} found. Get started by creating your first entry.
                </div>
              ) : (
                data.map((item, idx) => (
                  <div key={idx} className="px-6 py-4 flex justify-between items-center hover:bg-gray-50 transition cursor-pointer">
                    <div className="flex items-center space-x-4">
                      <div className="p-2 bg-blue-50 rounded-lg">
                        {isSystemAdmin ? <Building2 className="h-5 w-5 text-blue-600" /> : <Package className="h-5 w-5 text-blue-600" />}
                      </div>
                      <div>
                        <p className="font-semibold text-gray-900">{isSystemAdmin ? item.bankId : item.name}</p>
                        <p className="text-sm text-gray-500">{isSystemAdmin ? item.issuerUrl : item.code}</p>
                      </div>
                    </div>
                    <ArrowRight className="h-5 w-5 text-gray-300" />
                  </div>
                ))
              )}
            </div>
            <div className="px-6 py-4 bg-gray-50 border-t flex justify-center">
               <a href="/swagger-ui.html" target="_blank" className="flex items-center text-sm text-gray-500 hover:text-blue-600">
                 Explore full API in Swagger <ExternalLink className="h-3 w-3 ml-1" />
               </a>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};

export default Dashboard;
