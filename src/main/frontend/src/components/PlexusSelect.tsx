import React from 'react';
import Select, { FormatOptionLabelMeta, Props as SelectProps, StylesConfig, GroupBase } from 'react-select';

export interface PlexusOption {
  value: string;
  label: string;
  code?: string;
  metaKey?: string;
  secondaryLabel?: string;
}

interface PlexusSelectProps extends Omit<SelectProps<PlexusOption, false, GroupBase<PlexusOption>>, 'styles'> {
  showSearch?: boolean;
  compact?: boolean;
  optionMetaLayout?: 'inline' | 'stacked';
  singleValueMetaLayout?: 'inline' | 'stacked';
}

const PlexusSelect: React.FC<PlexusSelectProps> = ({
  showSearch,
  compact = false,
  optionMetaLayout = 'inline',
  singleValueMetaLayout = 'inline',
  options = [],
  formatOptionLabel,
  ...props
}) => {
  // Determine if we should show search based on item count if not explicitly set
  const isSearchable = showSearch !== undefined ? showSearch : options.length > 8;
  const portalTarget = typeof window !== 'undefined' ? document.body : undefined;

  const controlHeight = compact ? '36px' : '42px';
  const valueFontSize = compact ? '0.6875rem' : '0.75rem';

  const getSecondaryLabel = (option: PlexusOption) => option.secondaryLabel || option.code || option.metaKey || '';

  const renderOptionLabel = (option: PlexusOption, layout: 'inline' | 'stacked') => {
    const secondaryLabel = getSecondaryLabel(option);

    if (!secondaryLabel) {
      return <span>{option.label}</span>;
    }

    if (layout === 'stacked') {
      return (
        <span className="flex flex-col items-start leading-tight gap-1 max-w-full">
          <span className="block max-w-full break-words">{option.label}</span>
          <span className="font-normal text-[10px] tracking-normal text-gray-400 whitespace-nowrap">({secondaryLabel})</span>
        </span>
      );
    }

    return (
      <span className="inline-flex items-baseline gap-1 max-w-full">
        <span className="min-w-0 break-words">{option.label}</span>
        <span className="font-normal text-[10px] tracking-normal text-gray-400 whitespace-nowrap">({secondaryLabel})</span>
      </span>
    );
  };

  const defaultFormatOptionLabel = (option: PlexusOption, meta: FormatOptionLabelMeta<PlexusOption>) => {
    const layout = meta.context === 'menu' ? optionMetaLayout : singleValueMetaLayout;
    return renderOptionLabel(option, layout);
  };

  const customStyles: StylesConfig<PlexusOption, false, GroupBase<PlexusOption>> = {
    control: (base, state) => ({
      ...base,
      backgroundColor: 'white',
      borderWidth: '1px',
      borderColor: state.isFocused ? '#3b82f6' : '#e5e7eb', // blue-500 or gray-200
      borderRadius: '0.5rem', // rounded-lg
      padding: '0',
      boxShadow: state.isFocused ? '0 0 0 4px rgba(59, 130, 246, 0.1)' : '0 1px 2px 0 rgba(0, 0, 0, 0.05)', // focus:ring-4 focus:ring-blue-500/10 shadow-sm
      '&:hover': {
        borderColor: state.isFocused ? '#3b82f6' : '#d1d5db', // gray-300 on hover
      },
      minHeight: controlHeight,
      height: controlHeight,
      transition: 'all 0.2s',
    }),
    valueContainer: (base) => ({
      ...base,
      padding: '0 0.75rem',
      overflow: 'hidden',
    }),
    placeholder: (base) => ({
      ...base,
      color: '#9ca3af', // gray-400
      fontWeight: 900, // font-black
      fontSize: valueFontSize,
      textTransform: 'uppercase',
      letterSpacing: '0.1em', // tracking-widest
    }),
    singleValue: (base) => ({
      ...base,
      color: '#111827', // gray-900
      fontWeight: 900, // font-black
      fontSize: valueFontSize,
      textTransform: 'uppercase',
      letterSpacing: '0.1em', // tracking-widest
      maxWidth: '100%',
      overflow: 'hidden',
      whiteSpace: 'normal',
    }),
    input: (base) => ({
      ...base,
      fontWeight: 900,
      fontSize: valueFontSize,
      textTransform: 'uppercase',
      letterSpacing: '0.1em',
    }),
    menu: (base) => ({
      ...base,
      borderRadius: '1.25rem', // rounded-[1.25rem]
      border: '1px solid #f3f4f6',
      boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)', // shadow-xl
      overflow: 'hidden',
      padding: '0.5rem',
      marginTop: '0.5rem',
      zIndex: 9999,
    }),
    menuPortal: (base) => ({
      ...base,
      zIndex: 9999,
    }),
    menuList: (base) => ({
      ...base,
      padding: 0,
      maxHeight: '280px',
      overflowY: 'auto',
    }),
    option: (base, state) => ({
      ...base,
      backgroundColor: state.isSelected
        ? '#eff6ff'
        : state.isFocused
          ? '#eff6ff'
          : 'transparent',
      color: state.isSelected
        ? (state.data.value === 'CREATE_NEW' ? '#2563eb' : '#374151')
        : state.data.value === 'CREATE_NEW'
          ? '#2563eb' // blue-600
          : '#374151',
      fontWeight: 900,
      fontSize: valueFontSize,
      textTransform: 'uppercase',
      letterSpacing: '0.1em',
      padding: '0.75rem 1rem',
      borderRadius: '0.75rem',
      cursor: 'pointer',
      whiteSpace: 'normal',
      '&:active': {
        backgroundColor: '#dbeafe',
      },
    }),
    indicatorSeparator: () => ({
      display: 'none',
    }),
    dropdownIndicator: (base) => ({
      ...base,
      color: '#9ca3af',
      padding: '0 1rem',
      '&:hover': {
        color: '#6b7280',
      },
    }),
  };

  return (
    <Select
      {...props}
      options={options}
      formatOptionLabel={formatOptionLabel ?? defaultFormatOptionLabel}
      isSearchable={isSearchable}
      styles={customStyles}
      menuPortalTarget={portalTarget}
      menuPosition="fixed"
      components={{
        IndicatorSeparator: null,
      }}
    />
  );
};

export default PlexusSelect;
