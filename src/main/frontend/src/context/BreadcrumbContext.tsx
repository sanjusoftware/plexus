import React, { createContext, useContext, useState, ReactNode } from 'react';

interface BreadcrumbContextType {
  entityName: string | null;
  setEntityName: (name: string | null) => void;
}

const BreadcrumbContext = createContext<BreadcrumbContextType | undefined>(undefined);

export const BreadcrumbProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [entityName, setEntityName] = useState<string | null>(null);

  return (
    <BreadcrumbContext.Provider value={{ entityName, setEntityName }}>
      {children}
    </BreadcrumbContext.Provider>
  );
};

export const useBreadcrumb = () => {
  const context = useContext(BreadcrumbContext);
  if (context === undefined) {
    throw new Error('useBreadcrumb must be used within a BreadcrumbProvider');
  }
  return context;
};
