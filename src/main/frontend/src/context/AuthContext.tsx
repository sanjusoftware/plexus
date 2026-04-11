import React, { createContext, useContext, useState, useEffect, ReactNode, useRef, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { authService, UserProfile } from '../services/AuthService';
import axios from 'axios';
import { useAbortSignal } from '../hooks/useAbortSignal';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  login: (bankId: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshAuthState: () => Promise<void>;
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
  const location = useLocation();
  const lastToastKey = useRef<string | null>(null);

  // Clear toast on navigation
  useEffect(() => {
    // If the location key changes and it's different from the one when the toast was set, clear the toast
    if (toast && lastToastKey.current && lastToastKey.current !== location.key) {
      setToast(null);
      lastToastKey.current = null;
    }
  }, [location.key, toast]);

  const setInternalToast = useCallback((newToast: { message: string; type: 'error' | 'success' } | null) => {
    setToast(newToast);
    // When setting a toast, we associate it with the CURRENT location key
    lastToastKey.current = newToast ? location.key : null;
  }, [location.key]);

  // Use a ref to keep track of the current user state for the interceptor
  // without re-running the initialization effect when the user changes.
  const userRef = useRef<UserProfile | null>(user);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

  const signal = useAbortSignal();

  const refreshAuthState = useCallback(async () => {
    const currentUser = await authService.getUser();
    setUser(currentUser);

    if (currentUser) {
      const map = await authService.getPermissionsMap();
      setPermissionsMap(map);
    } else {
      setPermissionsMap({});
    }
  }, []);

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
        await axios.get('/api/v1/auth/csrf', { signal });

        // 2. Fetch User Profile + permissions map
        const currentUser = await authService.getUser(signal);
        setUser(currentUser);
        if (currentUser) {
          const map = await authService.getPermissionsMap(signal);
          setPermissionsMap(map);
        }
      } catch (err) {
        if (axios.isCancel(err)) return;
        // If unauthenticated (401), currentUser will be null via authService.getUser()
        // We only log actual connection errors here
        console.log('Auth initialization: User is not logged in.');
      } finally {
        if (!signal.aborted) {
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
          setPermissionsMap({});
        }

        // Handle 403 Access Denied
        // The toast should appear for both unauthenticated (if they get 403)
        // and authenticated users lacking permissions.
        if (status === 403) {
          setInternalToast({
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
      axios.interceptors.request.eject(requestInterceptor);
      axios.interceptors.response.eject(responseInterceptor);
    };
  }, [signal, setInternalToast]);

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
      refreshAuthState,
      isAuthenticated: !!user,
      bankId: user?.bank_id || null,
      permissionsMap,
      toast,
      setToast: setInternalToast
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