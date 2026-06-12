"use client";

import { useAuthStore } from "@/stores/auth-store";
import { useRouter, usePathname } from "next/navigation";
import { useEffect, useState } from "react";

export function useAuthGuard() {
  const { isAuthenticated } = useAuthStore();
  const [hydrated, setHydrated] = useState(false);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (useAuthStore.persist.hasHydrated()) {
      setHydrated(true);
    }
    const unsub = useAuthStore.persist.onFinishHydration(() => {
      setHydrated(true);
    });
    return () => unsub();
  }, []);

  useEffect(() => {
    if (!hydrated) return;

    const publicRoutes = ["/login", "/register"];
    if (!isAuthenticated && !publicRoutes.includes(pathname)) {
      router.replace("/login");
    }
    if (isAuthenticated && publicRoutes.includes(pathname)) {
      router.replace("/dashboard");
    }
  }, [hydrated, isAuthenticated, pathname, router]);
}

/** Client component wrapper for use in Server Component layouts */
export function AuthGuard() {
  useAuthGuard();
  return null;
}
