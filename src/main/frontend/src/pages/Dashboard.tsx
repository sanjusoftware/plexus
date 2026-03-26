import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import {
  Building2, Package, Loader2, Plus, ArrowRight, ExternalLink, CheckCircle2, X, ShieldCheck, AlertCircle
} from 'lucide-react';
import axios from 'axios';

const Dashboard = () => {
  const { user, bankId, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');

  useEffect(() => {
    if (!authLoading && !user) {
      navigate('/login-view');
    }
  }, [user, authLoading, navigate]);

  const fetchData = async () => {
    if (!user || isSystemAdmin) return;
    setLoading(true);
    try {
      // Bank Admin sees their products
      const response = await axios.get('/api/v1/products');
      setData(response.data.content || []);
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authLoading && user) {
      fetchData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, authLoading, isSystemAdmin]);

  if (authLoading || (user && loading && data.length === 0)) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  if (isSystemAdmin) {
    return (
      <>
        {/* Welcome Card for System Admin */}
        <div className="bg-white rounded-2xl shadow-sm border p-8 mb-8 flex justify-between items-center">
          <div>
            <h2 className="text-3xl font-bold text-blue-900 mb-2">System Administration</h2>
            <p className="text-gray-600">
              Manage your global banking infrastructure and onboard new tenants.
            </p>
          </div>
          <div className="hidden md:block">
             <ShieldCheck className="h-20 w-20 text-blue-100" />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div
            onClick={() => navigate('/banks')}
            className="bg-white p-8 rounded-3xl border shadow-sm hover:shadow-xl hover:border-blue-200 transition cursor-pointer group"
          >
            <div className="p-4 bg-blue-50 rounded-2xl w-fit mb-6 group-hover:scale-110 transition">
              <Building2 className="h-8 w-8 text-blue-600" />
            </div>
            <h3 className="text-xl font-bold text-gray-900 mb-2">Bank Management</h3>
            <p className="text-gray-500 text-sm mb-6">Review onboarding requests, activate or deactivate banking tenants.</p>
            <div className="flex items-center text-blue-600 font-bold text-sm">
              Manage Banks <ArrowRight className="h-4 w-4 ml-2" />
            </div>
          </div>

          <div
            onClick={() => navigate('/onboarding?admin=true')}
            className="bg-white p-8 rounded-3xl border shadow-sm hover:shadow-xl hover:border-green-200 transition cursor-pointer group"
          >
            <div className="p-4 bg-green-50 rounded-2xl w-fit mb-6 group-hover:scale-110 transition">
              <Plus className="h-8 w-8 text-green-600" />
            </div>
            <h3 className="text-xl font-bold text-gray-900 mb-2">Onboard New Bank</h3>
            <p className="text-gray-500 text-sm mb-6">Directly register a new banking institution into the Plexus engine.</p>
            <div className="flex items-center text-green-600 font-bold text-sm">
              Start Onboarding <ArrowRight className="h-4 w-4 ml-2" />
            </div>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      {error && (
        <div className="mb-6 p-4 bg-red-50 border-l-4 border-red-500 rounded-r-xl flex items-center text-red-700 animate-in fade-in slide-in-from-top-1">
          <AlertCircle className="h-5 w-5 mr-3 flex-shrink-0" />
          <p className="text-sm font-bold">{error}</p>
          <button onClick={() => setError('')} className="ml-auto p-1 hover:bg-red-100 rounded-full transition">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {success && (
        <div className="mb-6 p-4 bg-green-50 border-l-4 border-green-500 rounded-r-xl flex items-center text-green-700 animate-in fade-in slide-in-from-top-1">
          <CheckCircle2 className="h-5 w-5 mr-3 flex-shrink-0" />
          <p className="text-sm font-bold">{success}</p>
          <button onClick={() => setSuccess('')} className="ml-auto p-1 hover:bg-green-100 rounded-full transition">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Welcome Card */}
      <div className="bg-white rounded-2xl shadow-sm border p-8 mb-8 flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-blue-900 mb-2">Welcome back!</h2>
          <p className="text-gray-600">
            {isSystemAdmin
              ? "Manage your global banking infrastructure and onboard new tenants."
              : `Manage products and pricing rules for ${user?.bankName || bankId}.`}
          </p>
        </div>
        <div className="hidden md:block">
           <ShieldCheck className="h-20 w-20 text-blue-100" />
        </div>
      </div>

      {/* Data List */}
      <div className="bg-white rounded-2xl border shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b flex justify-between items-center bg-gray-50">
          <h3 className="font-bold text-gray-900">
            Available Products
          </h3>
          <button
            onClick={() => navigate('/products/create')}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-blue-700 transition flex items-center"
          >
            <Plus className="h-4 w-4 mr-2" />
            New Product
          </button>
        </div>
        <div className="divide-y">
          {data.length === 0 ? (
            <div className="p-12 text-center text-gray-500">
              No products found. Get started by creating your first entry.
            </div>
          ) : (
            data.map((item, idx) => (
              <div
                key={idx}
                onClick={() => navigate(`/products/edit/${item.id}`)}
                className="px-6 py-4 flex justify-between items-center hover:bg-gray-50 transition cursor-pointer"
              >
                <div className="flex items-center space-x-4">
                  <div className="p-2 bg-blue-50 rounded-lg">
                    <Package className="h-5 w-5 text-blue-600" />
                  </div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <p className="font-semibold text-gray-900">{item.name}</p>
                    </div>
                    <p className="text-sm text-gray-500">{item.code}</p>
                  </div>
                </div>
                <div className="flex items-center space-x-3">
                  <ArrowRight className="h-5 w-5 text-gray-300" />
                </div>
              </div>
            ))
          )}
        </div>
        <div className="px-6 py-4 bg-gray-50 border-t flex justify-center">
           <a href="/swagger-ui/index.html" target="_blank" className="flex items-center text-sm text-gray-500 hover:text-blue-600">
             Explore full API in Swagger <ExternalLink className="h-3 w-3 ml-1" />
           </a>
        </div>
      </div>
    </>
  );
};

export default Dashboard;
