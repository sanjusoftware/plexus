import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import AuthCallback from './pages/AuthCallback';
import Dashboard from './pages/Dashboard';
import OnboardingPage from './pages/OnboardingPage';
import BankManagementPage from './pages/admin/BankManagementPage';
import ProductTypesPage from './pages/admin/ProductTypesPage';
import ProductCategoriesPage from './pages/admin/ProductCategoriesPage';
import PricingMetadataPage from './pages/admin/PricingMetadataPage';
import PricingMetadataFormPage from './pages/admin/PricingMetadataFormPage';
import PricingComponentsPage from './pages/admin/PricingComponentsPage';
import ProductManagementPage from './pages/admin/ProductManagementPage';
import ProductFormPage from './pages/admin/ProductFormPage';
import ProductTypeFormPage from './pages/admin/ProductTypeFormPage';
import ProductCategoryFormPage from './pages/admin/ProductCategoryFormPage';
import FeatureComponentsPage from './pages/admin/FeatureComponentsPage';
import FeatureComponentFormPage from './pages/admin/FeatureComponentFormPage';
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
  const location = useLocation();

  if (loading) return null;
  if (isAuthenticated) return <Navigate to="/dashboard" replace state={location.state} />;

  return <LandingPage />;
};

function App() {
  return (
    <Router>
      <AuthProvider>
        <div className="min-h-screen bg-gray-50">
          <Routes>
            <Route path="/" element={<PublicHome />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/onboarding" element={
              <BreadcrumbProvider>
                <OnboardingPage />
              </BreadcrumbProvider>
            } />
            <Route path="/auth/callback" element={<AuthCallback />} />
            <Route path="/error" element={<ErrorPage />} />

            <Route element={<ProtectedLayout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/banks" element={
                <PermissionElement permission="bank:management:access">
                  <BankManagementPage />
                </PermissionElement>
              } />
              <Route path="/banks/create" element={
                <PermissionElement action="POST" path="/api/v1/banks">
                  <OnboardingPage />
                </PermissionElement>
              } />
              <Route path="/my-bank" element={
                <PermissionElement action="PUT" path="/api/v1/banks">
                  <OnboardingPage />
                </PermissionElement>
              } />
              <Route path="/banks/edit/:id" element={
                <PermissionElement action="PUT" path="/api/v1/banks/*">
                  <OnboardingPage />
                </PermissionElement>
              } />
              <Route path="/product-types" element={
                <PermissionElement permission="catalog:product-type:read">
                  <ProductTypesPage />
                </PermissionElement>
              } />
              <Route path="/product-categories" element={
                <PermissionElement permission="catalog:product-category:read">
                  <ProductCategoriesPage />
                </PermissionElement>
              } />
              <Route path="/product-categories/create" element={
                <PermissionElement permission="catalog:product-category:create">
                  <ProductCategoryFormPage />
                </PermissionElement>
              } />
              <Route path="/product-categories/edit/:id" element={
                <PermissionElement permission="catalog:product-category:update">
                  <ProductCategoryFormPage />
                </PermissionElement>
              } />
              <Route path="/product-types/create" element={
                <PermissionElement permission="catalog:product-type:create">
                  <ProductTypeFormPage />
                </PermissionElement>
              } />
              <Route path="/product-types/edit/:id" element={
                <PermissionElement permission="catalog:product-type:update">
                  <ProductTypeFormPage />
                </PermissionElement>
              } />
              <Route path="/features" element={
                <PermissionElement action="GET" path="/api/v1/features">
                  <FeatureComponentsPage />
                </PermissionElement>
              } />
              <Route path="/features/create" element={
                <PermissionElement action="POST" path="/api/v1/features">
                  <FeatureComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/features/edit/:id" element={
                <PermissionElement action="PUT" path="/api/v1/features/*">
                  <FeatureComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata" element={
                <PermissionElement permission="pricing:metadata:read">
                  <PricingMetadataPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata/create" element={
                <PermissionElement permission="pricing:metadata:create">
                  <PricingMetadataFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-metadata/edit/:attributeKey" element={
                <PermissionElement permission="pricing:metadata:update">
                  <PricingMetadataFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components" element={
                <PermissionElement permission="pricing:component:read">
                  <PricingComponentsPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components/create" element={
                <PermissionElement permission="pricing:component:create">
                  <PricingComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/pricing-components/edit/:id" element={
                <PermissionElement permission="pricing:component:update">
                  <PricingComponentFormPage />
                </PermissionElement>
              } />
              <Route path="/products" element={
                <PermissionElement permission="catalog:product:read">
                  <ProductManagementPage />
                </PermissionElement>
              } />
              <Route path="/products/create" element={
                <PermissionElement permission="catalog:product:create">
                  <ProductFormPage />
                </PermissionElement>
              } />
              <Route path="/products/edit/:id" element={
                <PermissionElement permission="catalog:product:update">
                  <ProductFormPage />
                </PermissionElement>
              } />
              <Route path="/roles" element={
                <PermissionElement permission="auth:role:read">
                  <RoleManagementPage />
                </PermissionElement>
              } />
              <Route path="/roles/register" element={
                <PermissionElement permission="auth:role:write">
                  <RoleFormPage />
                </PermissionElement>
              } />
              <Route path="/roles/edit/:roleName" element={
                <PermissionElement permission="auth:role:write">
                  <RoleFormPage />
                </PermissionElement>
              } />
            </Route>

            {/* Fallback for client-side routing */}
            <Route path="*" element={<ErrorPage />} />
          </Routes>
        </div>
      </AuthProvider>
    </Router>
  );
}

export default App;
