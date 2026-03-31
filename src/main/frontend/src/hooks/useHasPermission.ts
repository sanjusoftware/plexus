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
        // If the path starts with /api/ and is not in our map, we default to hidden
        // to prevent UI elements showing up for endpoints we don't know about or
        // that have been removed from the backend.
        if (path.startsWith('/api/')) {
          return false;
        }
        // Otherwise assume it's a public UI route or non-API path.
        return true;
      }

      // If multiple endpoints match, the user must have at least one permission for EACH matching entry.
      // This supports 'hasAnyAuthority' or 'OR' conditions in @PreAuthorize.
      return matchingEntries.every(([_, requiredPermissions]) =>
        requiredPermissions.some(p => userPermissions.has(p))
      );
    }

    return false;
  };

  return { hasPermission };
};
