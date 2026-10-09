import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, refreshSession, setAccessToken, setSessionExpiredHandler } from "./api";
import type { AuthResponse, UserSummary } from "./types";

type Status = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: Status;
  user: UserSummary | null;
  login: (email: string, password: string) => Promise<UserSummary>;
  register: (name: string, email: string, password: string) => Promise<UserSummary>;
  logout: () => Promise<void>;
  /** Applies a new token pair after a password change, or updates the cached user. */
  applyAuth: (auth: AuthResponse) => void;
  setUser: (user: UserSummary) => void;
  clearSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("loading");
  const [user, setUser] = useState<UserSummary | null>(null);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus("anonymous");
    queryClient.clear();
  }, [queryClient]);

  const applyAuth = useCallback((auth: AuthResponse) => {
    setAccessToken(auth.accessToken);
    setUser(auth.user);
    setStatus("authenticated");
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(clearSession);
    let cancelled = false;
    refreshSession().then((auth) => {
      if (cancelled) return;
      if (auth) applyAuth(auth);
      else setStatus("anonymous");
    });
    return () => {
      cancelled = true;
      setSessionExpiredHandler(null);
    };
  }, [applyAuth, clearSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const auth = await api<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: { email, password },
        noRetry: true,
      });
      queryClient.clear();
      applyAuth(auth);
      return auth.user;
    },
    [applyAuth, queryClient],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      const auth = await api<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: { name, email, password },
        noRetry: true,
      });
      queryClient.clear();
      applyAuth(auth);
      return auth.user;
    },
    [applyAuth, queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await api<void>("/api/auth/logout", { method: "POST", noRetry: true });
    } catch {
      // The local session is cleared either way.
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout, applyAuth, setUser, clearSession }),
    [status, user, login, register, logout, applyAuth, clearSession],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
