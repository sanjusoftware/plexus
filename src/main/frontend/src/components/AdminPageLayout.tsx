import React from 'react';
import { X } from 'lucide-react';

interface AdminPageProps {
  children: React.ReactNode;
  width?: 'narrow' | 'medium' | 'default' | 'wide';
  className?: string;
}

interface AdminPageHeaderProps {
  title: string;
  description?: string;
  icon?: React.ComponentType<{ className?: string }>;
  tone?: 'blue' | 'purple' | 'indigo' | 'amber';
  actions?: React.ReactNode;
  className?: string;
}

interface AdminFormHeaderProps extends Omit<AdminPageHeaderProps, 'actions'> {
  onClose: () => void;
  closeLabel?: string;
}

interface AdminSectionCardProps {
  children: React.ReactNode;
  className?: string;
}

interface AdminInfoBannerProps {
  children: React.ReactNode;
  icon?: React.ComponentType<{ className?: string }>;
  title?: string;
  tone?: 'blue' | 'purple' | 'indigo' | 'amber';
  className?: string;
}

const joinClasses = (...classes: Array<string | false | null | undefined>) => classes.filter(Boolean).join(' ');

const widthClasses: Record<NonNullable<AdminPageProps['width']>, string> = {
  narrow: 'max-w-3xl',
  medium: 'max-w-5xl',
  default: 'max-w-6xl',
  wide: 'max-w-7xl'
};

const toneClasses = {
  blue: {
    icon: 'bg-blue-50 text-blue-600',
    banner: 'border-blue-300 bg-blue-50/70 text-blue-900',
    bannerIcon: 'bg-blue-100 text-blue-700'
  },
  purple: {
    icon: 'bg-purple-50 text-purple-600',
    banner: 'border-purple-300 bg-purple-50/70 text-purple-900',
    bannerIcon: 'bg-purple-100 text-purple-700'
  },
  indigo: {
    icon: 'bg-indigo-50 text-indigo-600',
    banner: 'border-indigo-300 bg-indigo-50/70 text-indigo-900',
    bannerIcon: 'bg-indigo-100 text-indigo-700'
  },
  amber: {
    icon: 'bg-amber-50 text-amber-600',
    banner: 'border-amber-300 bg-amber-50/80 text-amber-900',
    bannerIcon: 'bg-amber-100 text-amber-700'
  }
};

export const AdminPage: React.FC<AdminPageProps> = ({ children, width = 'default', className }) => (
  <div className={joinClasses('admin-page', widthClasses[width], className)}>{children}</div>
);

export const AdminPageHeader: React.FC<AdminPageHeaderProps> = ({
  title,
  description,
  icon: Icon,
  tone = 'blue',
  actions,
  className
}) => {
  const styles = toneClasses[tone];

  return (
    <div className={joinClasses('admin-page-header', className)}>
      <div className="admin-page-header__content">
        {Icon && (
          <div className={joinClasses('admin-page-header__icon', styles.icon)}>
            <Icon className="h-5 w-5" />
          </div>
        )}
        <div className="min-w-0">
          <h1 className="admin-page-header__title">{title}</h1>
          {description && <p className="admin-page-header__description">{description}</p>}
        </div>
      </div>
      {actions && <div className="admin-page-header__actions">{actions}</div>}
    </div>
  );
};

export const AdminFormHeader: React.FC<AdminFormHeaderProps> = ({
  onClose,
  closeLabel = 'Close',
  ...props
}) => (
  <AdminPageHeader
    {...props}
    actions={
      <button
        type="button"
        onClick={onClose}
        aria-label={closeLabel}
        title={closeLabel}
        className="admin-icon-button"
      >
        <X className="h-4.5 w-4.5" />
      </button>
    }
  />
);

export const AdminSectionCard: React.FC<AdminSectionCardProps> = ({ children, className }) => (
  <div className={joinClasses('admin-card', className)}>{children}</div>
);

export const AdminInfoBanner: React.FC<AdminInfoBannerProps> = ({
  children,
  icon: Icon,
  title,
  tone = 'blue',
  className
}) => {
  const styles = toneClasses[tone];

  return (
    <div className={joinClasses('admin-info-banner', styles.banner, className)}>
      {Icon && (
        <div className={joinClasses('admin-info-banner__icon', styles.bannerIcon)}>
          <Icon className="h-4 w-4" />
        </div>
      )}
      <div className="min-w-0 text-xs leading-relaxed">
        {title && <p className="mb-1 text-[11px] font-bold uppercase tracking-wide">{title}</p>}
        <div className="font-medium">{children}</div>
      </div>
    </div>
  );
};

