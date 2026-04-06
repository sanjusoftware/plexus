import { useEffect, useState } from 'react';

/**
 * Custom hook to provide an AbortSignal that is automatically aborted
 * when the component unmounts. Useful for cancelling pending API requests
 * during double-mounting in development (React StrictMode).
 */
export const useAbortSignal = () => {
  const [controller, setController] = useState(() => new AbortController());

  useEffect(() => {
    // In React 18 Strict Mode (development), components are mounted, unmounted,
    // and remounted. The cleanup from the first mount will abort the controller.
    // If we detect the controller is aborted, we trigger a re-render with a new one
    // so the second mount can successfully perform its side effects.
    if (controller.signal.aborted) {
      setController(new AbortController());
    }

    return () => {
      controller.abort();
    };
  }, [controller]);

  return controller.signal;
};
