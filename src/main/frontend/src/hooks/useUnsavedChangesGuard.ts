import { useCallback, useMemo, useState } from 'react';

const UNSAVED_CHANGES_MESSAGE = 'You will lose unsaved changes. Are you sure?';

const stableStringify = (value: unknown): string => {
  if (value === null || value === undefined) {
    return String(value);
  }

  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`;
  }

  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const keys = Object.keys(record).sort();
    return `{${keys.map(key => `${JSON.stringify(key)}:${stableStringify(record[key])}`).join(',')}}`;
  }

  return JSON.stringify(value);
};

export const useUnsavedChangesGuard = <T,>(currentValue: T) => {
  const [initialSnapshot, setInitialSnapshot] = useState(() => stableStringify(currentValue));

  const isDirty = useMemo(() => stableStringify(currentValue) !== initialSnapshot, [currentValue, initialSnapshot]);

  const resetDirtyBaseline = useCallback((nextValue: T) => {
    setInitialSnapshot(stableStringify(nextValue));
  }, []);

  const confirmDiscardChanges = useCallback(() => {
    if (!isDirty) {
      return true;
    }

    return window.confirm(UNSAVED_CHANGES_MESSAGE);
  }, [isDirty]);

  return { isDirty, resetDirtyBaseline, confirmDiscardChanges };
};

