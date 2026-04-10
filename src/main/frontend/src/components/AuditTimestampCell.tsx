import React from 'react';
import { formatAuditTimestamp } from '../utils/auditTimestamp';

interface AuditTimestampCellProps extends React.TdHTMLAttributes<HTMLTableCellElement> {
  value?: string;
  variant?: 'default' | 'expanded';
}

/**
 * DRY component for rendering audit timestamps across admin tables.
 * Provides consistent styling and formatting.
 *
 * @param value - The timestamp string to format
 * @param variant - 'default' for standard table cells, 'expanded' for expanded row details
 * @param title - Optional title attribute for tooltip
 * @param rest - Other table cell props
 */
export const AuditTimestampCell = React.forwardRef<
  HTMLTableCellElement,
  AuditTimestampCellProps
>(({ value, variant = 'default', title, ...rest }, ref) => {
  const formattedValue = formatAuditTimestamp(value);
  const displayTitle = title || value || '--';

  const classNames = {
    default: 'whitespace-nowrap text-xs text-gray-600',
    expanded: 'text-[11px] font-bold text-gray-600 leading-tight'
  };

  if (variant === 'expanded') {
    return (
      <div className={classNames[variant]} title={displayTitle}>
        {formattedValue}
      </div>
    );
  }

  return (
    <td
      ref={ref}
      className={classNames[variant]}
      title={displayTitle}
      {...rest}
    >
      {formattedValue}
    </td>
  );
});

AuditTimestampCell.displayName = 'AuditTimestampCell';

