import { useAuth } from '../context/AuthContext';

type PermissionCheck =
  | { permission: string }
  | { action: string; path: string };

export const useHasPermission = () => {
  const { user, permissionsMap } = useAuth();

  const matchesPath = (requestPath: string, mapPath: string): boolean => {
    // Escape regex special characters except *
    const regexSource = mapPath.replace(/[.+^${}()|[\]\\]/g, '\\$&')
                               .replace(/\*/g, '[^/]+');
    const regex = new RegExp(`^${regexSource}$`);
    return regex.test(requestPath);
  };

  const hasPermission = (check: PermissionCheck | string): boolean => {
    if (!user) return false;

    const userPermissions = new Set(user.permissions || []);

    if (typeof check === 'string') {
      return userPermissions.has(check);
    }

    if ('permission' in check) {
      return userPermissions.has(check.permission);
    }

    if ('action' in check && 'path' in check) {
      const action = check.action.toUpperCase();
      const path = check.path;

      // Find all matching endpoints in the map
      const matchingEntries = Object.entries(permissionsMap).filter(([key]) => {
        const [mAction, mPath] = key.split(':');
        return mAction === action && matchesPath(path, mPath);
      });

      if (matchingEntries.length === 0) {
        // If no permissions are mapped to this endpoint, we assume it's public
        // or requires just authentication.
        return true;
      }

      // If multiple endpoints match, the user must have all permissions for ALL matching entries
      // (This is a strict approach to ensure they can perform the action on the requested path)
      return matchingEntries.every(([_, requiredPermissions]) =>
        requiredPermissions.every(p => userPermissions.has(p))
      );
    }

    return false;
  };

  return { hasPermission };
};
