import React from 'react';
import { useHasPermission } from '../hooks/useHasPermission';

interface HasPermissionProps {
  permission?: string;
  action?: string;
  path?: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export const HasPermission: React.FC<HasPermissionProps> = ({
  permission,
  action,
  path,
  children,
  fallback = null
}) => {
  const { hasPermission } = useHasPermission();

  const canAccess = permission
    ? hasPermission(permission)
    : action && path
    ? hasPermission({ action, path })
    : true;

  if (canAccess) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
};
