import { useEffect, useRef } from 'react';

/**
 * Hook to handle the Escape key.
 * Only the top-most active listener (the one most recently registered) will be triggered.
 */
const listeners: (() => void)[] = [];

const handleGlobalKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && listeners.length > 0) {
    const topListener = listeners[listeners.length - 1];
    topListener();
  }
};

if (typeof document !== 'undefined') {
  document.addEventListener('keydown', handleGlobalKeyDown);
}

export const useEscapeKey = (onEscape: () => void, enabled: boolean = true) => {
  const onEscapeRef = useRef(onEscape);
  onEscapeRef.current = onEscape;

  useEffect(() => {
    if (!enabled) return;

    const listener = () => onEscapeRef.current();
    listeners.push(listener);

    return () => {
      const index = listeners.indexOf(listener);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    };
  }, [enabled]);
};
