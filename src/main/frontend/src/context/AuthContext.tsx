import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authService, UserProfile } from '../services/AuthService';
import axios from 'axios';

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
        // If we get a 401, clear user state
        if (error.response && error.response.status === 401) {
          setUser(null);
        }

        // Handle 500+ Errors
        if (error.response && error.response.status >= 500) {
          const { status, data } = error.response;
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
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};