import React, { createContext, useContext, useState, useEffect, ReactNode, useRef } from 'react';
import { authService, UserProfile } from '../services/AuthService';
import axios from 'axios';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  login: (bankId: string) => Promise<void>;
  logout: () => Promise<void>;
  isAuthenticated: boolean;
  bankId: string | null;
  permissionsMap: Record<string, string[]>;
  toast: { message: string; type: 'error' | 'success' } | null;
  setToast: (toast: { message: string; type: 'error' | 'success' } | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [permissionsMap, setPermissionsMap] = useState<Record<string, string[]>>({});
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: 'error' | 'success' } | null>(null);

  // Use a ref to keep track of the current user state for the interceptor
  // without re-running the initialization effect when the user changes.
  const userRef = useRef<UserProfile | null>(user);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

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

    const controller = new AbortController();

    const initAuth = async () => {
      try {
        // 1. Initialize CSRF token
        await axios.get('/api/v1/auth/csrf', { signal: controller.signal });

        // 2. Fetch User Profile
        const currentUser = await authService.getUser(controller.signal);
        setUser(currentUser);

        // 3. Fetch Permissions Map if authenticated
        if (currentUser) {
          const map = await authService.getPermissionsMap(controller.signal);
          setPermissionsMap(map);
        }
      } catch (err) {
        if (axios.isCancel(err)) return;
        // If unauthenticated (401), currentUser will be null via authService.getUser()
        // We only log actual connection errors here
        console.log('Auth initialization: User is not logged in.');
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
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

        // Handle 403 Access Denied
        // The toast should appear for both unauthenticated (if they get 403)
        // and authenticated users lacking permissions.
        if (status === 403) {
          setToast({
            message: 'Access Denied: You do not have permission to perform this action.',
            type: 'error'
          });
        }

        // Handle 500+ Errors
        if (status >= 500) {
          const { data } = error.response;
          window.location.href = `/error?status=${status}&message=${encodeURIComponent(data?.message || 'Server Error')}&timestamp=${encodeURIComponent(data?.timestamp || new Date().toISOString())}`;
        }
        return Promise.reject(error);
      }
    );

    return () => {
      controller.abort();
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
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      login,
      logout,
      isAuthenticated: !!user,
      bankId: user?.bank_id || null,
      permissionsMap,
      toast,
      setToast
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};