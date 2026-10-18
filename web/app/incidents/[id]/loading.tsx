import { AppShell } from '@/components/AppShell';

export default function Loading() {
  return (
    <AppShell breadcrumb="Incident queue / Incident">
      <div className="animate-pulse" aria-label="Loading incident" role="status">
        <div className="h-4 w-28 rounded bg-line" />
        <div className="mt-3 h-8 w-80 max-w-full rounded bg-line" />
        <div className="mt-6 grid gap-[19px] lg:grid-cols-[minmax(0,1.5fr)_minmax(280px,.8fr)]">
          <div className="h-[420px] rounded-xl bg-line/60" />
          <div className="h-[380px] rounded-xl bg-line/50" />
        </div>
      </div>
    </AppShell>
  );
}
