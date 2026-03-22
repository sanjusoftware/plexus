import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authService, UserProfile } from '../services/AuthService';
import axios from 'axios';
import { ShieldAlert, X } from 'lucide-react';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  login: (bankId: string) => Promise<void>;
  logout: () => Promise<void>;
  isAuthenticated: boolean;
  bankId: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: 'error' | 'success' } | null>(null);

  useEffect(() => {
    axios.defaults.withCredentials = true;

    // Interceptor to attach CSRF token to every request
    const requestInterceptor = axios.interceptors.request.use((config) => {
      const token = authService.getCsrfToken();
      if (token) {
        config.headers['X-XSRF-TOKEN'] = token;
      }
      return config;
    });

    const initAuth = async () => {
      try {
        // 1. Initialize CSRF token
        await axios.get('/api/v1/auth/csrf');

        // 2. Fetch User Profile
        const currentUser = await authService.getUser();
        setUser(currentUser);
      } catch (err) {
        // If unauthenticated (401), currentUser will be null via authService.getUser()
        // We only log actual connection errors here
        console.log('Auth initialization: User is not logged in.');
      } finally {
        setLoading(false);
      }
    };

    initAuth();

    // Response interceptor to handle Global Errors
    const responseInterceptor = axios.interceptors.response.use(
      (response) => response,
      (error) => {
        const status = error.response?.status;

        // If we get a 401, clear user state
        if (status === 401) {
          setUser(null);
        }

        // Handle 403 Access Denied for Logged in Users
        if (status === 403 && user) {
          setToast({
            message: 'Access Denied: You do not have permission to perform this action.',
            type: 'error'
          });
        }

        // Handle 500+ Errors
        if (status >= 500) {
          const { data } = error.response;
          window.location.href = `/error?status=${status}&message=${encodeURIComponent(data.message || 'Server Error')}&timestamp=${encodeURIComponent(data.timestamp || new Date().toISOString())}`;
        }
        return Promise.reject(error);
      }
    );

    return () => {
      axios.interceptors.request.eject(requestInterceptor);
      axios.interceptors.response.eject(responseInterceptor);
    };
  }, []);

  const login = async (bankId: string) => {
    await authService.login(bankId);
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
    // Force redirect to login page after state is cleared
    window.location.href = '/login-view';
  };

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      login,
      logout,
      isAuthenticated: !!user,
      bankId: user?.bank_id || null
    }}>
      {children}

      {/* Global Toast Notification */}
      {toast && (
        <div className="fixed bottom-5 right-5 z-50 animate-in fade-in slide-in-from-bottom-5 duration-300">
          <div className={`flex items-center space-x-3 p-4 rounded-xl shadow-2xl border ${
            toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-green-50 border-green-200 text-green-800'
          }`}>
            <div className={`p-2 rounded-lg ${toast.type === 'error' ? 'bg-red-100' : 'bg-green-100'}`}>
              <ShieldAlert className="h-5 w-5" />
            </div>
            <div className="flex-1 pr-4">
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
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};