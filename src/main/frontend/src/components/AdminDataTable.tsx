import React from 'react';
import { Archive, CheckCircle2, Clock, Copy, Edit2, History, Trash2, XCircle, type LucideIcon } from 'lucide-react';
export { AuditTimestampCell } from './AuditTimestampCell';

type Align = 'left' | 'center' | 'right';
type ActionButtonTone = 'neutral' | 'primary' | 'success' | 'danger';
type ActionButtonSize = 'icon' | 'compact';

interface AdminDataTableProps extends React.TableHTMLAttributes<HTMLTableElement> {
  children: React.ReactNode;
  containerClassName?: string;
}

interface AdminDataTableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  children: React.ReactNode;
  interactive?: boolean;
  hoverable?: boolean;
}

interface AdminDataTableHeaderCellProps extends Omit<React.ThHTMLAttributes<HTMLTableCellElement>, 'align'> {
  align?: Align;
}

interface AdminDataTableCellProps extends Omit<React.TdHTMLAttributes<HTMLTableCellElement>, 'align'> {
  align?: Align;
}

interface AdminDataTableEmptyRowProps {
  colSpan: number;
  children: React.ReactNode;
}

interface AdminDataTableActionButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: ActionButtonTone;
  size?: ActionButtonSize;
}

type AdminActionKind = 'activate' | 'approve' | 'reactivate' | 'deactivate' | 'edit' | 'delete' | 'archive' | 'copy' | 'version' | 'reject';

interface AdminDataTableActionContentProps {
  action: AdminActionKind;
  label?: string;
  iconClassName?: string;
}

const joinClasses = (...classes: Array<string | false | null | undefined>) => classes.filter(Boolean).join(' ');

const alignmentClassNames: Record<Align, string> = {
  left: '',
  center: 'text-center',
  right: 'text-right'
};

const buttonToneClassNames: Record<ActionButtonTone, string> = {
  neutral: 'admin-table__action-btn--neutral',
  primary: 'admin-table__action-btn--primary',
  success: 'admin-table__action-btn--success',
  danger: 'admin-table__action-btn--danger'
};

const buttonSizeClassNames: Record<ActionButtonSize, string> = {
  icon: 'admin-table__action-btn--icon',
  compact: 'admin-table__action-btn--compact'
};

const actionIconMap: Record<AdminActionKind, LucideIcon> = {
  activate: CheckCircle2,
  approve: CheckCircle2,
  reactivate: CheckCircle2,
  deactivate: Clock,
  edit: Edit2,
  delete: Trash2,
  archive: Archive,
  copy: Copy,
  version: History,
  reject: XCircle
};

const actionDefaultLabels: Record<AdminActionKind, string> = {
  activate: 'Activate',
  approve: 'Approve',
  reactivate: 'Re-activate',
  deactivate: 'Deactivate',
  edit: 'Edit',
  delete: 'Delete',
  archive: 'Archive',
  copy: 'Copy',
  version: 'Version',
  reject: 'Reject'
};

export const AdminDataTable: React.FC<AdminDataTableProps> = ({
  children,
  className,
  containerClassName,
  ...props
}) => (
  <div className={joinClasses('admin-table-card', containerClassName)}>
    <table className={joinClasses('admin-table', className)} {...props}>
      {children}
    </table>
  </div>
);

export const AdminDataTableRow: React.FC<AdminDataTableRowProps> = ({
  children,
  className,
  interactive = false,
  hoverable = true,
  ...props
}) => (
  <tr
    className={joinClasses(
      'admin-table__row',
      hoverable && 'admin-table__row--hoverable',
      interactive && 'admin-table__row--interactive',
      className
    )}
    {...props}
  >
    {children}
  </tr>
);

export const AdminDataTableHeaderCell: React.FC<AdminDataTableHeaderCellProps> = ({
  children,
  className,
  align = 'left',
  ...props
}) => (
  <th className={joinClasses(alignmentClassNames[align], className)} {...props}>
    {children}
  </th>
);

export const AdminDataTableActionsHeader: React.FC<Omit<AdminDataTableHeaderCellProps, 'align'>> = ({
  className,
  ...props
}) => <AdminDataTableHeaderCell align="right" className={joinClasses('admin-table__actions-header', className)} {...props} />;

export const AdminDataTableCell: React.FC<AdminDataTableCellProps> = ({
  children,
  className,
  align = 'left',
  ...props
}) => (
  <td className={joinClasses(alignmentClassNames[align], className)} {...props}>
    {children}
  </td>
);

export const AdminDataTableActionCell: React.FC<Omit<AdminDataTableCellProps, 'align'>> = ({
  children,
  className,
  ...props
}) => (
  <AdminDataTableCell align="right" className={joinClasses('admin-table__actions-cell', className)} {...props}>
    <div className="admin-table__actions">{children}</div>
  </AdminDataTableCell>
);

export const AdminDataTableEmptyRow: React.FC<AdminDataTableEmptyRowProps> = ({ colSpan, children }) => (
  <tr>
    <td colSpan={colSpan} className="admin-empty-state">
      {children}
    </td>
  </tr>
);

export const AdminDataTableActionButton: React.FC<AdminDataTableActionButtonProps> = ({
  children,
  className,
  tone = 'neutral',
  size = 'icon',
  type,
  ...props
}) => (
  <button
    type={type ?? 'button'}
    className={joinClasses(
      'admin-table__action-btn',
      buttonToneClassNames[tone],
      buttonSizeClassNames[size],
      className
    )}
    {...props}
  >
    {children}
  </button>
);

export const AdminDataTableActionContent: React.FC<AdminDataTableActionContentProps> = ({
  action,
  label,
  iconClassName
}) => {
  const Icon = actionIconMap[action];

  return (
    <>
      <Icon className={joinClasses('h-3.5 w-3.5', iconClassName)} />
      {label ?? actionDefaultLabels[action]}
    </>
  );
};

