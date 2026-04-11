import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Building2, Package, Loader2, ShieldCheck, Users, Tag, Layers, X
} from 'lucide-react';
import axios from 'axios';
import OnboardingSuccessModal from '../components/OnboardingSuccessModal';
import { useHasPermission } from '../hooks/useHasPermission';
import { useAbortSignal } from '../hooks/useAbortSignal';

interface StatsSet {
  products: Record<string, number>;
  productTypes: Record<string, number>;
  roles: Record<string, number>;
  pricingComponents: Record<string, number>;
  pricingTiers: Record<string, number>;
  totalBanks?: number;
}

const Dashboard = () => {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { hasPermission } = useHasPermission();
  const [localStats, setLocalStats] = useState<StatsSet | null>(null);
  const [successState, setSuccessState] = useState<{ title: string; message: string } | null>(null);
  const [globalStats, setGlobalStats] = useState<StatsSet | null>(null);
  const [loading, setLoading] = useState(true);
  const [showWelcome, setShowWelcome] = useState(false);
  const signal = useAbortSignal();

  const authorities = (user?.permissions as string[]) || [];
  const canReadBankStats = authorities.includes('bank:stats:read');
  const canReadSystemStats = authorities.includes('system:stats:read');

  useEffect(() => {
    if (!authLoading && !user) {
      navigate('/login');
    }
  }, [user, authLoading, navigate]);

  useEffect(() => {
    // Check if welcome message was already shown in this session
    const welcomeShown = sessionStorage.getItem('welcomeShown');
    if (!welcomeShown && user) {
      setShowWelcome(true);
      sessionStorage.setItem('welcomeShown', 'true');
      const timer = setTimeout(() => setShowWelcome(false), 5000);
      return () => clearTimeout(timer);
    }
  }, [user]);

  const fetchStats = async (abortSignal: AbortSignal) => {
    if (!user) return;
    setLoading(true);
    try {
      const requests = [];
      if (canReadBankStats) {
        requests.push(axios.get('/api/v1/dashboard/stats/local', { signal: abortSignal }).then(res => setLocalStats(res.data)));
      }
      if (canReadSystemStats) {
        requests.push(axios.get('/api/v1/dashboard/stats/global', { signal: abortSignal }).then(res => setGlobalStats(res.data)));
      }
      await Promise.all(requests);
    } catch (err) {
      if (axios.isCancel(err)) return;
      console.error('Failed to fetch dashboard stats:', err);
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    if (!authLoading && user) {
      fetchStats(signal);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, authLoading, canReadBankStats, canReadSystemStats, signal]);

  useEffect(() => {
    if (location.state?.onboardingSuccess) {
      setSuccessState({
        title: location.state.title,
        message: location.state.message
      });
      // Clear navigation state to prevent modal from re-appearing on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  if (authLoading || (user && loading)) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  const StatCard = ({ title, icon: Icon, stats, colorClass, onClick }: { title: string, icon: any, stats: Record<string, number>, colorClass: string, onClick?: () => void }) => {
    const total = Object.values(stats).reduce((a, b) => a + b, 0);
    const isClickable = !!onClick;

    return (
      <div
        onClick={onClick}
        className={`bg-white rounded-2xl border shadow-sm p-6 transition ${
          isClickable ? 'hover:shadow-md hover:border-blue-300 cursor-pointer' : ''
        }`}
      >
        <div className="flex items-center justify-between mb-4">
          <div className={`p-3 rounded-xl ${colorClass}`}>
            <Icon className="h-6 w-6" />
          </div>
          <div className="text-right">
            <p className="text-sm text-gray-500 font-medium">{title}</p>
            <p className="text-2xl font-bold text-gray-900">{total}</p>
          </div>
        </div>

        <div className="space-y-3">
          {/* Simple distribution bar with zero guard */}
          <div className="h-2 w-full bg-gray-100 rounded-full overflow-hidden flex">
            {total > 0 && (
              <>
                {stats.ACTIVE > 0 && <div className="bg-green-500 h-full" style={{ width: `${(stats.ACTIVE / total) * 100}%` }} />}
                {stats.DRAFT > 0 && <div className="bg-yellow-400 h-full" style={{ width: `${(stats.DRAFT / total) * 100}%` }} />}
                {stats.ARCHIVED > 0 && <div className="bg-red-400 h-full" style={{ width: `${(stats.ARCHIVED / total) * 100}%` }} />}
                {stats.INACTIVE > 0 && <div className="bg-gray-400 h-full" style={{ width: `${(stats.INACTIVE / total) * 100}%` }} />}
              </>
            )}
          </div>

          <div className="grid grid-cols-2 gap-2">
            {Object.entries(stats).map(([status, count]) => (
              <div key={status} className="flex items-center text-xs">
                <span className={`w-2 h-2 rounded-full mr-2 ${
                  status === 'ACTIVE' ? 'bg-green-500' :
                  status === 'DRAFT' ? 'bg-yellow-400' :
                  status === 'ARCHIVED' ? 'bg-red-400' : 'bg-gray-400'
                }`} />
                <span className="text-gray-600 capitalize">{status.toLowerCase()}:</span>
                <span className="ml-auto font-bold text-gray-900">{count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  const SummarySection = ({ title, stats, icon: Icon, showBanksCount }: { title: string, stats: StatsSet, icon: any, showBanksCount?: boolean }) => {
    const navMapping = [
      { title: 'Products', icon: Package, stats: stats.products, colorClass: 'bg-blue-50 text-blue-600', path: '/products', api: '/api/v1/products' },
      { title: 'Product Types', icon: Layers, stats: stats.productTypes, colorClass: 'bg-purple-50 text-purple-600', path: '/product-types', api: '/api/v1/product-types' },
      { title: 'Pricing Components', icon: Tag, stats: stats.pricingComponents, colorClass: 'bg-emerald-50 text-emerald-600', path: '/pricing-components', api: '/api/v1/pricing-components' },
      { title: 'Roles', icon: Users, stats: stats.roles, colorClass: 'bg-orange-50 text-orange-600', path: '/roles', api: '/api/v1/roles' },
    ];

    return (
      <div className="mb-10">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-gray-900 flex items-center">
            <Icon className="h-6 w-6 mr-2 text-blue-600" /> {title}
          </h2>
          {showBanksCount && (
            <div className="px-4 py-2 bg-blue-50 text-blue-700 rounded-lg text-sm font-bold border border-blue-100">
              Total Banks Managed: {stats.totalBanks}
            </div>
          )}
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {navMapping.map((item) => (
            <StatCard
              key={item.title}
              title={item.title}
              icon={item.icon}
              stats={item.stats}
              colorClass={item.colorClass}
              onClick={hasPermission({ action: 'GET', path: item.api }) ? () => navigate(item.path) : undefined}
            />
          ))}
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-7xl mx-auto">
      <OnboardingSuccessModal
        isOpen={!!successState}
        onClose={() => setSuccessState(null)}
        title={successState?.title || ''}
        message={successState?.message || ''}
      />
      {/* Temporary Welcome Banner */}
      {showWelcome && (
        <div className="mb-4 p-3 bg-blue-900 text-white rounded-xl shadow-lg flex items-center justify-between animate-in fade-in slide-in-from-top-4 duration-500">
          <div className="flex items-center space-x-3">
            <div className="p-1.5 bg-blue-800 rounded-lg">
              <ShieldCheck className="h-5 w-5 text-blue-200" />
            </div>
            <div>
              <h3 className="font-bold text-sm">Welcome back, {user?.name}!</h3>
              <p className="text-xs text-blue-200">You are logged into {user?.bankName}.</p>
            </div>
          </div>
          <button onClick={() => setShowWelcome(false)} className="p-1.5 hover:bg-blue-800 rounded-full transition">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">Dashboard Overview</h1>
          <p className="text-gray-500 text-xs mt-0.5 font-medium">Real-time metrics and platform health.</p>
        </div>
      </div>

      {globalStats && (
        <SummarySection title="Platform-wide Statistics" stats={globalStats} icon={Globe} showBanksCount={true} />
      )}

      {localStats && (
        <SummarySection
          title={canReadSystemStats ? 'System Bank Statistics' : `${user?.bankName || 'Bank'} Statistics`}
          stats={localStats}
          icon={Building2}
        />
      )}
    </div>
  );
};

// Internal Globe icon for platform stats
const Globe = ({ className }: { className?: string }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
  </svg>
);

export default Dashboard;
