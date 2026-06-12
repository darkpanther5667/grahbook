"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { Store } from "@/types";

const LEGACY_TOKEN_KEY = "gh_token";

export interface AuthState {
  token: string | null;
  store: Store | null;
  user: { name: string; phone: string; email?: string } | null;
  isAuthenticated: boolean;
  setAuth: (token: string, store: Store, user: { name: string; phone: string; email?: string }) => void;
  logout: () => void;
}

function migrateLegacyAuth() {
  // Migration 1: Check if there's old zustand format in gh_user (from previous sessions)
  // and move it to gh_auth
  try {
    const oldRaw = localStorage.getItem("gh_user");
    if (oldRaw) {
      const old = JSON.parse(oldRaw);
      if (old && old.state && old.version !== undefined) {
        // This is old zustand persist format - migrate to gh_auth
        localStorage.setItem("gh_auth", oldRaw);
        localStorage.removeItem("gh_user");
      }
    }
  } catch { /* ignore */ }

  // Migration 2: Check if landing page (index.html) stored a token
  // First try gh_token, then fallback to token inside gh_user object
  let legacyToken = localStorage.getItem(LEGACY_TOKEN_KEY);

  // Parse the legacy user from gh_user (flat format from index.html)
  // Note: gh_user might have been deleted by Migration 1 if it was zustand format
  try {
    const raw = localStorage.getItem("gh_user");
    if (!raw) return;
    const legacy = JSON.parse(raw);
    if (!legacy || !legacy.loggedIn) return;

    // Fallback: if gh_token wasn't set, extract token from inside gh_user
    if (!legacyToken && legacy.token) {
      legacyToken = legacy.token;
    }
    if (!legacyToken) return;

    // Build a Store object from the legacy format
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

    // Save in zustand-compatible format under gh_auth key
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
    localStorage.setItem("gh_auth", JSON.stringify(newState));

    // Clean up legacy keys so this only runs once
    localStorage.removeItem(LEGACY_TOKEN_KEY);
    localStorage.removeItem("gh_user");
  } catch {
    // If parsing fails, ignore
  }
}

// Run migration immediately so it's in place before zustand hydrates
if (typeof window !== "undefined") {
  migrateLegacyAuth();
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      store: null,
      user: null,
      isAuthenticated: false,
      setAuth: (token, store, user) =>
        set({ token, store, user, isAuthenticated: true }),
      logout: () => {
        // Also clear the landing page's gh_token so Google login from index.html doesn't cause re-auth
        if (typeof window !== "undefined") {
          localStorage.removeItem(LEGACY_TOKEN_KEY);
        }
        set({ token: null, store: null, user: null, isAuthenticated: false });
      },
    }),
    {
      name: "gh_auth",
      storage: createJSONStorage(() => localStorage),
    }
  )
);
