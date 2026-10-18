import Link from 'next/link';
import { AppShell } from '@/components/AppShell';

export default function IncidentNotFound() {
  return (
    <AppShell breadcrumb="Incident queue / Not found">
      <div className="rounded-xl border border-line bg-white p-6">
        <h1 className="text-lg font-bold">Incident not found. It may have been deleted.</h1>
        <Link href="/incidents" className="mt-4 inline-block text-xs font-extrabold text-teal">Back to queue</Link>
      </div>
    </AppShell>
  );
}
