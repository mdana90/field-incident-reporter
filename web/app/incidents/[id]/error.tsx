'use client';

import Link from 'next/link';
import { AppShell } from '@/components/AppShell';

export default function IncidentError({ retry }: { error: unknown; retry: () => void }) {
  return (
    <AppShell breadcrumb="Incident queue / Incident">
      <div className="rounded-xl border border-[#efc8c1] bg-[#fff8f6] p-6" role="alert">
        <h1 className="text-lg font-bold text-red">Couldn&apos;t load this incident.</h1>
        <p className="mt-1.5 text-xs text-[#75898c]">Make sure the API server is running.</p>
        <div className="mt-4 flex items-center gap-4">
          <button type="button" onClick={() => retry()} className="min-h-[39px] rounded-lg bg-teal px-4 text-xs font-extrabold text-white">
            Retry
          </button>
          <Link href="/incidents" className="text-xs font-extrabold text-teal">Back to queue</Link>
        </div>
      </div>
    </AppShell>
  );
}
