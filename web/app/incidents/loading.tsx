import { AppShell } from '@/components/AppShell';

export default function Loading() {
  return (
    <AppShell breadcrumb="Incident queue">
      <div className="animate-pulse" aria-label="Loading incidents" role="status">
        <div className="h-8 w-56 rounded bg-line" />
        <div className="mt-[23px] grid grid-cols-3 gap-3">
          {[0, 1, 2].map((key) => <div key={key} className="h-[87px] rounded-xl bg-line/60" />)}
        </div>
        <div className="mt-[23px] space-y-2">
          {[0, 1, 2, 3].map((key) => <div key={key} className="h-[75px] rounded-xl bg-line/50" />)}
        </div>
      </div>
    </AppShell>
  );
}
