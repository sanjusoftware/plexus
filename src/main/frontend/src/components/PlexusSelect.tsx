import React from 'react';
import Select, { Props as SelectProps, StylesConfig, GroupBase } from 'react-select';

export interface PlexusOption {
  value: string;
  label: string;
}

interface PlexusSelectProps extends Omit<SelectProps<PlexusOption, false, GroupBase<PlexusOption>>, 'styles'> {
  showSearch?: boolean;
  compact?: boolean;
}

const PlexusSelect: React.FC<PlexusSelectProps> = ({
  showSearch,
  compact = false,
  options = [],
  ...props
}) => {
  // Determine if we should show search based on item count if not explicitly set
  const isSearchable = showSearch !== undefined ? showSearch : options.length > 10;

  const controlHeight = compact ? '36px' : '42px';
  const valueFontSize = compact ? '0.6875rem' : '0.75rem';

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
      zIndex: 50,
    }),
    menuList: (base) => ({
      ...base,
      padding: 0,
    }),
    option: (base, state) => ({
      ...base,
      backgroundColor: state.isSelected
        ? '#3b82f6'
        : state.isFocused
          ? '#eff6ff'
          : 'transparent',
      color: state.isSelected
        ? 'white'
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
      isSearchable={isSearchable}
      styles={customStyles}
      components={{
        IndicatorSeparator: null,
      }}
    />
  );
};

export default PlexusSelect;
