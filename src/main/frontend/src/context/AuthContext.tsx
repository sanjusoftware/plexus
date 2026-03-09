import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User } from 'oidc-client-ts';
import { authService } from '../services/AuthService';

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (bankId: string, issuerUrl: string, clientId?: string) => Promise<void>;
  logout: () => Promise<void>;
  isAuthenticated: boolean;
  bankId: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [bankId, setBankId] = useState<string | null>(null);

  useEffect(() => {
    const initAuth = async () => {
      const storedBankId = localStorage.getItem('plexus_bank_id');
      const currentUser = await authService.getUser();
      setUser(currentUser);
      setBankId(storedBankId);
      setLoading(false);
    };
    initAuth();
  }, []);

  const login = async (bankId: string, issuerUrl: string, clientId?: string) => {
    await authService.init(bankId, issuerUrl, clientId);
    await authService.login();
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
    setBankId(null);
  };

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      login,
      logout,
      isAuthenticated: !!user,
      bankId
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
