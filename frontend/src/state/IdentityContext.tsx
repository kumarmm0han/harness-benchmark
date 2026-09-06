import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { Identity } from '../api/types';

interface IdentityContextValue {
  identity: Identity;
  setIdentity: (id: Identity) => void;
}

const Ctx = createContext<IdentityContextValue | null>(null);

export function IdentityProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<Identity>('demo-author');
  const value = useMemo(() => ({ identity, setIdentity }), [identity]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useIdentity(): IdentityContextValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useIdentity must be used inside <IdentityProvider>');
  return v;
}
