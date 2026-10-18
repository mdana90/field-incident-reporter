'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

// Re-renders the current route on an interval so new reports appear without a reload.
export function AutoRefresh({ intervalMs = 15000 }: { intervalMs?: number }) {
  const router = useRouter();
  useEffect(() => {
    const timer = setInterval(() => router.refresh(), intervalMs);
    return () => clearInterval(timer);
  }, [router, intervalMs]);
  return null;
}
