import React from 'react';
import PlexusSelect, { PlexusOption } from './PlexusSelect';

interface StyledSelectProps {
  className?: string;
  containerClassName?: string;
  value?: string;
  onChange?: (e: { target: { value: string } }) => void;
  children: React.ReactNode;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

/**
 * @deprecated Use PlexusSelect directly for better type safety and features.
 * This wrapper maintains backward compatibility with existing <select> style onChange events.
 */
const StyledSelect: React.FC<StyledSelectProps> = ({
  value,
  onChange,
  children,
  placeholder,
  containerClassName,
  ...props
}) => {
  // Convert React children (options) to react-select options format
  const options: PlexusOption[] = React.Children.map(children, (child) => {
    if (React.isValidElement(child) && child.type === 'option') {
      const props = child.props as any;
      return {
        value: props.value,
        label: props.children?.toString() || props.value,
      };
    }
    return null;
  })?.filter((opt): opt is PlexusOption => opt !== null) || [];

  const selectedOption = options.find(opt => opt.value === value) || null;

  const handleChange = (option: PlexusOption | null) => {
    if (onChange) {
      onChange({
        target: {
          value: option ? option.value : '',
        },
      } as React.ChangeEvent<HTMLSelectElement>);
    }
  };

  return (
    <div className={containerClassName || 'w-full'}>
      <PlexusSelect
        {...props}
        options={options}
        value={selectedOption}
        onChange={handleChange}
        placeholder={placeholder || 'Select...'}
      />
    </div>
  );
};

export default StyledSelect;
