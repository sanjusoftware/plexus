import React, { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { useBreadcrumb } from '../context/BreadcrumbContext';

interface RouteMapping {
  path: string;
  label: string;
  parent?: string;
}

const routeConfig: Record<string, RouteMapping> = {
  '/dashboard': { path: '/dashboard', label: 'Dashboard' },
  '/banks': { path: '/banks', label: 'Bank Management' },
  '/product-types': { path: '/product-types', label: 'Product Types' },
  '/features': { path: '/features', label: 'Product Features' },
  '/pricing-metadata': { path: '/pricing-metadata', label: 'Pricing Metadata' },
  '/pricing-components': { path: '/pricing-components', label: 'Pricing Components' },
  '/products': { path: '/products', label: 'Product Catalog' },
  '/bundles': { path: '/bundles', label: 'Product Bundles' },
  '/roles': { path: '/roles', label: 'Roles & Permissions' },
  '/onboarding': { path: '/onboarding', label: 'Onboarding' }
};

const Breadcrumbs: React.FC = () => {
  const location = useLocation();
  const { entityName, setEntityName } = useBreadcrumb();
  const pathnames = location.pathname.split('/').filter((x) => x);

  // Reset entityName when moving to a non-subpage
  useEffect(() => {
    const isSubPage = location.pathname.includes('/edit/') ||
                      location.pathname.includes('/create') ||
                      location.pathname.includes('/register') ||
                      (/^\/products\/\d+$/.test(location.pathname)) ||
                      (/^\/bundles\/\d+$/.test(location.pathname));
    if (!isSubPage) {
      setEntityName(null);
    }
  }, [location.pathname, setEntityName]);

  const getBreadcrumbs = () => {
    const crumbs: { label: string; path: string; isLast: boolean }[] = [];

    // Handle Dashboard separately or as first item if it is the current page
    if (location.pathname === '/dashboard') {
      return [{ label: 'Dashboard', path: '/dashboard', isLast: true }];
    }

    // Determine the base page (top-level)
    const basePagePath = `/${pathnames[0]}`;
    const basePage = routeConfig[basePagePath];

    if (basePage) {
      const isLastBase = pathnames.length === 1;
      crumbs.push({
        label: basePage.label,
        path: basePage.path,
        isLast: isLastBase
      });

      if (pathnames.length > 1) {
        // Handle sub-pages (create/edit/register)
        const subAction = pathnames[1];
        let subLabel = '';

        const singularMap: Record<string, string> = {
          '/banks': 'Bank',
          '/product-types': 'Product Type',
          '/features': 'Product Feature',
          '/pricing-metadata': 'Attribute',
          '/pricing-components': 'Pricing Component',
          '/products': 'Product',
          '/bundles': 'Product Bundle',
          '/roles': 'Role'
        };

        const singularName = singularMap[basePagePath] || basePage.label;

        if (subAction === 'create' || subAction === 'register') {
          subLabel = `New ${singularName}`;
        } else if (subAction === 'edit') {
          subLabel = entityName || `Edit ${singularName}`;
        } else if (basePagePath === '/products' && /^\d+$/.test(subAction)) {
          subLabel = entityName || `Product Details`;
        } else if (basePagePath === '/bundles' && /^\d+$/.test(subAction)) {
          subLabel = entityName || `Bundle Details`;
        }

        if (subLabel) {
          crumbs.push({
            label: subLabel,
            path: location.pathname,
            isLast: true
          });
        }
      }
    }

    return crumbs;
  };

  const crumbs = getBreadcrumbs();

  if (crumbs.length === 0) return null;

  return (
    <nav className="mb-4 flex" aria-label="Breadcrumb">
      <ol className="inline-flex items-center space-x-1 md:space-x-3">
        {crumbs.map((crumb, index) => (
          <li key={crumb.path} className="inline-flex items-center">
            {index > 0 && (
              <ChevronRight className="mx-1 h-4 w-4 text-gray-400" />
            )}
            {crumb.isLast ? (
              <span className="text-xs font-bold text-gray-900 sm:text-sm">
                {crumb.label}
              </span>
            ) : (
              <Link
                to={crumb.path}
                className="text-xs font-semibold text-gray-500 transition hover:text-blue-600 sm:text-sm"
              >
                {crumb.label}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
};

export default Breadcrumbs;
