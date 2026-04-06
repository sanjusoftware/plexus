import { useEffect, useRef } from 'react';

/**
 * Custom hook to provide an AbortSignal that is automatically aborted
 * when the component unmounts. Useful for cancelling pending API requests
 * during double-mounting in development (React StrictMode).
 */
export const useAbortSignal = () => {
  const controllerRef = useRef<AbortController | null>(null);

  if (!controllerRef.current) {
    controllerRef.current = new AbortController();
  }

  useEffect(() => {
    return () => {
      if (controllerRef.current) {
        controllerRef.current.abort();
      }
    };
  }, []);

  return controllerRef.current.signal;
};
