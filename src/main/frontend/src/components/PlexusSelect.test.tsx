import React from 'react';
import { render, screen, within } from '@testing-library/react';
import PlexusSelect from './PlexusSelect';

jest.mock('react-select', () => ({
  __esModule: true,
  default: ({ options = [], value, formatOptionLabel }: any) => (
    <div>
      <div data-testid="selected-value">
        {value ? formatOptionLabel(value, { context: 'value', inputValue: '', selectValue: value ? [value] : [] }) : null}
      </div>
      <div data-testid="menu-options">
        {options.map((option: any) => (
          <div key={option.value} data-testid={`option-${option.value}`}>
            {formatOptionLabel(option, { context: 'menu', inputValue: '', selectValue: value ? [value] : [] })}
          </div>
        ))}
      </div>
    </div>
  )
}));

describe('PlexusSelect shared option rendering', () => {
  test('renders plain labels unchanged when no code or key metadata exists', () => {
    render(
      <PlexusSelect
        options={[{ value: 'ONE', label: 'Just Label' }]}
        value={{ value: 'ONE', label: 'Just Label' }}
      />
    );

    expect(screen.getByTestId('selected-value')).toHaveTextContent('Just Label');
    expect(screen.getByTestId('selected-value')).not.toHaveTextContent('(');
  });

  test('renders code in gray for inline select values and menu options', () => {
    render(
      <PlexusSelect
        options={[{ value: 'ADV_BASE_FEE', label: 'Advanced Base Fee', code: 'ADV_BASE_FEE' }]}
        value={{ value: 'ADV_BASE_FEE', label: 'Advanced Base Fee', code: 'ADV_BASE_FEE' }}
      />
    );

    const selected = screen.getByTestId('selected-value');
    expect(selected).toHaveTextContent('Advanced Base Fee');
    expect(selected).toHaveTextContent('(ADV_BASE_FEE)');
    expect(within(selected).getByText('(ADV_BASE_FEE)')).toHaveClass('text-gray-400');

    const option = screen.getByTestId('option-ADV_BASE_FEE');
    expect(within(option).getByText('(ADV_BASE_FEE)')).toHaveClass('text-gray-400');
  });

  test('renders stacked menu metadata when optionMetaLayout is stacked', () => {
    render(
      <PlexusSelect
        optionMetaLayout="stacked"
        options={[
          {
            value: 'HIGH_VALUE_TX_SURCHARGE',
            label: 'High Value Transaction Surcharge',
            code: 'HIGH_VALUE_TX_SURCHARGE'
          }
        ]}
        value={{
          value: 'HIGH_VALUE_TX_SURCHARGE',
          label: 'High Value Transaction Surcharge',
          code: 'HIGH_VALUE_TX_SURCHARGE'
        }}
      />
    );

    const menuOption = screen.getByTestId('option-HIGH_VALUE_TX_SURCHARGE').firstChild as HTMLElement;
    expect(menuOption).toHaveClass('flex-col');
    expect(within(screen.getByTestId('option-HIGH_VALUE_TX_SURCHARGE')).getByText('(HIGH_VALUE_TX_SURCHARGE)')).toHaveClass('text-gray-400');

    const selected = screen.getByTestId('selected-value').firstChild as HTMLElement;
    expect(selected).toHaveClass('inline-flex');
  });
});

