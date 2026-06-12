"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { Store } from "@/types";

const AUTH_KEY = "gh_auth";
const LEGACY_TOKEN_KEY = "gh_token";
const LEGACY_USER_KEY = "gh_user";

const DEFAULT_STATE = {
  token: null as string | null,
  store: null as Store | null,
  user: null as { name: string; phone: string; email?: string } | null,
  isAuthenticated: false,
};

export interface AuthState {
  token: string | null;
  store: Store | null;
  user: { name: string; phone: string; email?: string } | null;
  isAuthenticated: boolean;
  setAuth: (token: string, store: Store, user: { name: string; phone: string; email?: string }) => void;
  logout: () => void;
}

/** Migrate legacy auth formats from index.html login to zustand persist format */
function migrateLegacyAuth() {
  // Migration 1: old zustand format in gh_user → move to gh_auth
  try {
    const oldRaw = localStorage.getItem(LEGACY_USER_KEY);
    if (oldRaw) {
      const old = JSON.parse(oldRaw);
      if (old && old.state && old.version !== undefined) {
        localStorage.setItem(AUTH_KEY, oldRaw);
        localStorage.removeItem(LEGACY_USER_KEY);
      }
    }
  } catch { /* ignore */ }

  // Migration 2: landing page flat format (gh_token + gh_user)
  let legacyToken = localStorage.getItem(LEGACY_TOKEN_KEY);

  try {
    const raw = localStorage.getItem(LEGACY_USER_KEY);
    if (!raw) return;
    const legacy = JSON.parse(raw);
    if (!legacy || !legacy.loggedIn) return;

    // Fallback: extract token from inside gh_user if gh_token missing
    if (!legacyToken && legacy.token) {
      legacyToken = legacy.token;
    }
    if (!legacyToken) return;

    // Reject corrupted tokens from previous bugs
    if (legacyToken === "undefined" || legacyToken === "null") {
      localStorage.removeItem(LEGACY_TOKEN_KEY);
      localStorage.removeItem(LEGACY_USER_KEY);
      return;
    }

    const store: Store = {
      id: legacy.store_id || "",
      store_name: legacy.store || legacy.name || "My Store",
      owner_name: legacy.name || "",
      phone: legacy.phone || "",
      email: legacy.email || "",
      business_type: "retail",
      plan: "basic",
      created_at: new Date().toISOString(),
      status: "active",
    };

    const newState = {
      state: {
        token: legacyToken,
        store,
        user: {
          name: legacy.name || "Store Owner",
          phone: legacy.phone || "",
          email: legacy.email || "",
        },
        isAuthenticated: true,
      },
      version: 0,
    };
    localStorage.setItem(AUTH_KEY, JSON.stringify(newState));

    // Clean up legacy keys
    localStorage.removeItem(LEGACY_TOKEN_KEY);
    localStorage.removeItem(LEGACY_USER_KEY);
  } catch { /* ignore */ }
}

function loadInitialState(): AuthState {
  if (typeof window === "undefined") {
    return { ...DEFAULT_STATE };
  }

  // Run migration first so gh_auth is populated
  migrateLegacyAuth();

  // Read gh_auth directly (synchronous) instead of waiting for persist rehydration
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    if (!raw) return { ...DEFAULT_STATE };
    const parsed = JSON.parse(raw);
    const s = parsed?.state;
    if (!s?.isAuthenticated || !s?.token) return { ...DEFAULT_STATE };

    // Reject corrupted tokens
    if (s.token === "undefined" || s.token === "null") {
      localStorage.removeItem(AUTH_KEY);
      return { ...DEFAULT_STATE };
    }

    return {
      token: s.token,
      store: s.store || null,
      user: s.user || null,
      isAuthenticated: true,
    };
  } catch {
    return { ...DEFAULT_STATE };
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      ...loadInitialState(),

      setAuth: (token, store, user) =>
        set({ token, store, user, isAuthenticated: true }),

      logout: () => {
        if (typeof window !== "undefined") {
          localStorage.removeItem(LEGACY_TOKEN_KEY);
          localStorage.removeItem(AUTH_KEY);
        }
        set({ token: null, store: null, user: null, isAuthenticated: false });
      },
    }),
    {
      name: AUTH_KEY,
      storage: createJSONStorage(() => localStorage),
    }
  )
);
