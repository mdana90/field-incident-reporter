'use client';

import { AppShell } from '@/components/AppShell';

export default function QueueError({ retry }: { error: unknown; retry: () => void }) {
  return (
    <AppShell breadcrumb="Incident queue">
      <div className="rounded-xl border border-[#efc8c1] bg-[#fff8f6] p-6" role="alert">
        <h1 className="text-lg font-bold text-red">Couldn&apos;t load incidents.</h1>
        <p className="mt-1.5 text-xs text-[#75898c]">Make sure the API server is running.</p>
        <button type="button" onClick={() => retry()} className="mt-4 min-h-[39px] rounded-lg bg-teal px-4 text-xs font-extrabold text-white">
          Retry
        </button>
      </div>
    </AppShell>
  );
}
