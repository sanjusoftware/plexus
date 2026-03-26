import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import AuthCallback from './pages/AuthCallback';
import Dashboard from './pages/Dashboard';
import OnboardingPage from './pages/OnboardingPage';
import BankManagementPage from './pages/admin/BankManagementPage';
import ProductTypesPage from './pages/admin/ProductTypesPage';
import PricingMetadataPage from './pages/admin/PricingMetadataPage';
import PricingComponentsPage from './pages/admin/PricingComponentsPage';
import PricingTiersPage from './pages/admin/PricingTiersPage';
import ProductManagementPage from './pages/admin/ProductManagementPage';
import ProductFormPage from './pages/admin/ProductFormPage';
import RoleManagementPage from './pages/admin/RoleManagementPage';
import RoleFormPage from './pages/admin/RoleFormPage';
import PricingComponentFormPage from './pages/admin/PricingComponentFormPage';
import ErrorPage from './pages/ErrorPage';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoggedInUserLayout from './components/LoggedInUserLayout';
import { Loader2 } from 'lucide-react';

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="h-12 w-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <LoggedInUserLayout>{children}</LoggedInUserLayout>;
};

const PublicHome = () => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) return null;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  return <LandingPage />;
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <div className="min-h-screen bg-gray-50">
          <Routes>
            <Route path="/" element={<PublicHome />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/onboarding" element={<OnboardingWrapper />} />
            <Route path="/auth/callback" element={<AuthCallback />} />
            <Route path="/dashboard" element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            } />
            <Route path="/banks" element={
              <ProtectedRoute>
                <BankManagementPage />
              </ProtectedRoute>
            } />
            <Route path="/banks/edit/:id" element={
              <ProtectedRoute>
                <OnboardingPage />
              </ProtectedRoute>
            } />
            <Route path="/product-types" element={
              <ProtectedRoute>
                <ProductTypesPage />
              </ProtectedRoute>
            } />
            <Route path="/pricing-metadata" element={
              <ProtectedRoute>
                <PricingMetadataPage />
              </ProtectedRoute>
            } />
            <Route path="/pricing-components" element={
              <ProtectedRoute>
                <PricingComponentsPage />
              </ProtectedRoute>
            } />
            <Route path="/pricing-components/create" element={
              <ProtectedRoute>
                <PricingComponentFormPage />
              </ProtectedRoute>
            } />
            <Route path="/pricing-components/edit/:id" element={
              <ProtectedRoute>
                <PricingComponentFormPage />
              </ProtectedRoute>
            } />
            <Route path="/pricing-tiers" element={
              <ProtectedRoute>
                <PricingTiersPage />
              </ProtectedRoute>
            } />
            <Route path="/products" element={
              <ProtectedRoute>
                <ProductManagementPage />
              </ProtectedRoute>
            } />
            <Route path="/products/create" element={
              <ProtectedRoute>
                <ProductFormPage />
              </ProtectedRoute>
            } />
            <Route path="/products/edit/:id" element={
              <ProtectedRoute>
                <ProductFormPage />
              </ProtectedRoute>
            } />
            <Route path="/roles" element={
              <ProtectedRoute>
                <RoleManagementPage />
              </ProtectedRoute>
            } />
            <Route path="/roles/register" element={
              <ProtectedRoute>
                <RoleFormPage />
              </ProtectedRoute>
            } />
            <Route path="/roles/edit/:roleName" element={
              <ProtectedRoute>
                <RoleFormPage />
              </ProtectedRoute>
            } />
            <Route path="/error" element={<ErrorPage />} />
            {/* Fallback for client-side routing */}
            <Route path="*" element={<ErrorPage />} />
          </Routes>
        </div>
      </Router>
    </AuthProvider>
  );
}

const OnboardingWrapper = () => {
  const { isAuthenticated } = useAuth();
  const isAdmin = new URLSearchParams(window.location.search).get('admin') === 'true';

  if (isAdmin && isAuthenticated) {
    return (
      <LoggedInUserLayout>
        <OnboardingPage />
      </LoggedInUserLayout>
    );
  }

  return <OnboardingPage />;
};

export default App;
