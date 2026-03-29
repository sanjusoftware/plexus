import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import AuthCallback from './pages/AuthCallback';
import Dashboard from './pages/Dashboard';
import OnboardingPage from './pages/OnboardingPage';
import BankManagementPage from './pages/admin/BankManagementPage';
import ProductTypesPage from './pages/admin/ProductTypesPage';
import PricingMetadataPage from './pages/admin/PricingMetadataPage';
import PricingMetadataFormPage from './pages/admin/PricingMetadataFormPage';
import PricingComponentsPage from './pages/admin/PricingComponentsPage';
import ProductManagementPage from './pages/admin/ProductManagementPage';
import ProductFormPage from './pages/admin/ProductFormPage';
import ProductTypeFormPage from './pages/admin/ProductTypeFormPage';
import RoleManagementPage from './pages/admin/RoleManagementPage';
import RoleFormPage from './pages/admin/RoleFormPage';
import PricingComponentFormPage from './pages/admin/PricingComponentFormPage';
import ErrorPage from './pages/ErrorPage';
import { AuthProvider, useAuth } from './context/AuthContext';
import { BreadcrumbProvider } from './context/BreadcrumbContext';
import LoggedInUserLayout from './components/LoggedInUserLayout';
import PermissionElement from './components/PermissionElement';
import { Loader2 } from 'lucide-react';

const ProtectedLayout = () => {
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

  return (
    <BreadcrumbProvider>
      <LoggedInUserLayout>
        <Outlet />
      </LoggedInUserLayout>
    </BreadcrumbProvider>
  );
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
            <Route path="/auth/callback" element={<AuthCallback />} />
            <Route path="/error" element={<ErrorPage />} />

            <Route element={<ProtectedLayout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/onboarding" element={<OnboardingPage />} />
              <Route path="/banks" element={
                <PermissionElement action="GET" path="/api/v1/banks">
                  <BankManagementPage />
                </PermissionElement>
              } />
              <Route path="/banks/edit/:id" element={
                <PermissionElement action="PUT" path="/api/v1/banks/*">
                  <OnboardingPage />
                </PermissionElement>
              } />
              <Route path="/product-types" element={
                <PermissionElement action="GET" path="/api/v1/product-types">
                  <ProductTypesPage />
                </PermissionElement>
              } />
              <Route path="/product-types/create" element={
                <PermissionElement action="POST" path="/api/v1/product-types">
                  <ProductTypeFormPage />
                </PermissionElement>
              } />
              <Route path="/product-types/edit/:id" element={
                <PermissionElement action="PUT" path="/api/v1/product-types/*">
                  <ProductTypeFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata" element={
                <PermissionElement action="GET" path="/api/v1/pricing-metadata">
                  <PricingMetadataPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata/create" element={
                <PermissionElement action="POST" path="/api/v1/pricing-metadata">
                  <PricingMetadataFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata/edit/:attributeKey" element={
                <PermissionElement action="PUT" path="/api/v1/pricing-metadata/*">
                  <PricingMetadataFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components" element={
                <PermissionElement action="GET" path="/api/v1/pricing-components">
                  <PricingComponentsPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components/create" element={
                <PermissionElement action="POST" path="/api/v1/pricing-components">
                  <PricingComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components/edit/:id" element={
                <PermissionElement action="PATCH" path="/api/v1/pricing-components/*">
                  <PricingComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/products" element={
                <PermissionElement action="GET" path="/api/v1/products">
                  <ProductManagementPage />
                </PermissionElement>
              } />
              <Route path="/products/create" element={
                <PermissionElement action="POST" path="/api/v1/products">
                  <ProductFormPage />
                </PermissionElement>
              } />
              <Route path="/products/edit/:id" element={
                <PermissionElement action="PATCH" path="/api/v1/products/*">
                  <ProductFormPage />
                </PermissionElement>
              } />
              <Route path="/roles" element={
                <PermissionElement action="GET" path="/api/v1/roles">
                  <RoleManagementPage />
                </PermissionElement>
              } />
              <Route path="/roles/register" element={
                <PermissionElement action="POST" path="/api/v1/roles/mapping">
                  <RoleFormPage />
                </PermissionElement>
              } />
              <Route path="/roles/edit/:roleName" element={
                <PermissionElement action="POST" path="/api/v1/roles/mapping">
                  <RoleFormPage />
                </PermissionElement>
              } />
            </Route>

            {/* Fallback for client-side routing */}
            <Route path="*" element={<ErrorPage />} />
          </Routes>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App;
