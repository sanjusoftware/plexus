import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useHasPermission } from '../hooks/useHasPermission';
import { Loader2 } from 'lucide-react';

interface PermissionElementProps {
  children: React.ReactNode;
  action?: string;
  path?: string;
  permission?: string;
}

const PermissionElement: React.FC<PermissionElementProps> = ({
  children,
  action,
  path,
  permission
}) => {
  const { isAuthenticated, loading } = useAuth();
  const { hasPermission } = useHasPermission();

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

  const check = permission
    ? permission
    : (action && path)
    ? { action, path }
    : null;

  if (check && !hasPermission(check)) {
    // If not permitted, redirect to dashboard (or an access denied page)
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

export default PermissionElement;
