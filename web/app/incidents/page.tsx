import Link from 'next/link';
import { AppShell } from '@/components/AppShell';
import { AutoRefresh } from '@/components/AutoRefresh';
import { SeverityBadge, StatusBadge } from '@/components/Badges';
import { QueueToolbar } from '@/components/QueueToolbar';
import { listIncidents, photoUrl } from '@/lib/api';
import { filterIncidents, formatReported, isPreviewable, parseQueueQuery, summarize, type Incident } from '@/lib/incidents';

export default async function QueuePage({ searchParams }: PageProps<'/incidents'>) {
  const query = parseQueueQuery(await searchParams);
  const all = await listIncidents();
  const counts = summarize(all);
  const incidents = filterIncidents(all, query);

  return (
    <AppShell breadcrumb={<strong className="font-bold text-[#52686c]">Incident queue</strong>} activeCount={counts.active} nav={query.status === 'RESOLVED' ? 'resolved' : 'queue'}>
      <AutoRefresh />
      <div className="flex items-start justify-between gap-[15px]">
        <div>
          <h1 className="text-[26px] font-bold tracking-tight">Incident queue</h1>
          <p className="mt-1.5 text-xs text-[#75898c]">Review reports submitted by field technicians.</p>
        </div>
        <span className="pt-1.5 text-[11px] text-[#87999b]">Updated just now</span>
      </div>

      <div className="mt-[23px] grid grid-cols-3 gap-[7px] md:gap-3">
        <Summary label="Critical incidents" value={counts.critical} urgent />
        <Summary label="Open" value={counts.open} />
        <Summary label="In progress" value={counts.inProgress} />
      </div>

      <QueueToolbar query={query} />

      <div className="mt-[13px] overflow-x-auto rounded-xl border border-line bg-white">
        {all.length === 0 ? (
          <Empty>No incidents yet. Reports from the Android app will appear here.</Empty>
        ) : incidents.length === 0 ? (
          <Empty>
            No incidents match these filters.{' '}
            <Link href="/incidents" className="font-extrabold text-teal">Clear filters</Link>
          </Empty>
        ) : (
          <table className="w-full min-w-[740px] table-fixed border-collapse">
            <thead>
              <tr className="bg-[#f9fbfa] text-left text-[10px] uppercase tracking-[.07em] text-[#87999a]">
                <th className="w-[42%] border-b border-line px-[15px] py-[13px]">Incident</th>
                <th className="w-[15%] border-b border-line px-[15px] py-[13px]">Severity</th>
                <th className="w-[16%] border-b border-line px-[15px] py-[13px]">Status</th>
                <th className="w-[16%] border-b border-line px-[15px] py-[13px]">Reported</th>
                <th className="w-[11%] border-b border-line px-[15px] py-[13px]"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {incidents.map((incident) => <Row key={incident.id} incident={incident} />)}
            </tbody>
          </table>
        )}
        {all.length > 0 && (
          <div className="flex justify-between border-t border-line px-4 py-[13px] text-[10px] text-[#839397]">
            <span>{`Showing ${incidents.length} ${incidents.length === 1 ? 'incident' : 'incidents'}`}</span>
            <span>{all.length} total</span>
          </div>
        )}
      </div>
    </AppShell>
  );
}

function Summary({ label, value, urgent = false }: { label: string; value: number; urgent?: boolean }) {
  return (
    <div className={`min-h-[76px] rounded-xl border px-2.5 py-3 md:min-h-[87px] md:px-[18px] md:py-4 ${urgent ? 'border-[#efc8c1] bg-[#fff8f6]' : 'border-line bg-white'}`}>
      <span className="block text-[11px] font-bold text-[#70858a]">{label}</span>
      <strong className={`mt-1.5 block text-xl tracking-tight md:text-2xl ${urgent ? 'text-red' : ''}`}>{value}</strong>
    </div>
  );
}

function Row({ incident }: { incident: Incident }) {
  const cover = incident.photos[0];
  const focus = incident.severity === 'CRITICAL' && incident.status !== 'RESOLVED';
  const cell = 'border-b border-[#edf2f1] px-[15px] py-3.5 align-middle text-[11px] text-[#5e7378]';
  return (
    <tr className={focus ? 'bg-[#fffbf9]' : ''}>
      <td className={cell}>
        <div className="flex items-center gap-[11px]">
          {cover && isPreviewable(cover.url) ? (
            <img src={photoUrl(cover.url)} alt="" className="size-[47px] flex-none rounded-lg object-cover" />
          ) : (
            <span className="size-[47px] flex-none rounded-lg bg-[#dfe7e5]" aria-hidden />
          )}
          <span className="min-w-0">
            <strong className="block truncate text-xs font-bold leading-snug text-[#21373e]">{incident.title}</strong>
            <small className="mt-1 block truncate text-[10px] text-[#8b9a9d]">{incident.location}</small>
          </span>
        </div>
      </td>
      <td className={cell}><SeverityBadge severity={incident.severity} /></td>
      <td className={cell}><StatusBadge status={incident.status} /></td>
      <td className={cell}>{formatReported(incident.createdAt)}</td>
      <td className={cell}>
        <Link href={`/incidents/${incident.id}`} className="whitespace-nowrap font-extrabold text-teal">Review →</Link>
      </td>
    </tr>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="px-4 py-12 text-center text-xs text-[#75898c]">{children}</p>;
}
