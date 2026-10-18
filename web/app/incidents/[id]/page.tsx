import Link from 'next/link';
import { notFound } from 'next/navigation';
import { AppShell } from '@/components/AppShell';
import { SeverityBadge, StatusBadge } from '@/components/Badges';
import { PhotoGallery } from '@/components/PhotoGallery';
import { TriageForm } from '@/components/TriageForm';
import { getIncident, listIncidents, photoUrl } from '@/lib/api';
import { formatReported, isPreviewable, SEVERITIES, SEVERITY_GUIDE, SEVERITY_LABEL, shortId, summarize } from '@/lib/incidents';

export default async function IncidentPage({ params }: PageProps<'/incidents/[id]'>) {
  const { id } = await params;
  const [incident, list] = await Promise.all([getIncident(id), listIncidents()]);
  if (!incident) notFound();
  const reported = formatReported(incident.createdAt);

  return (
    <AppShell
      breadcrumb={<>Incident queue / <strong className="font-bold text-[#52686c]">Incident #{shortId(incident.id)}</strong></>}
      activeCount={summarize(list).active}
    >
      <div className="mt-1">
        <Link href="/incidents" className="mb-3 inline-flex items-center gap-1.5 text-[11px] font-extrabold text-teal">← Back to queue</Link>
        <h1 className="text-[21px] font-bold tracking-tight md:text-[25px]">{incident.title}</h1>
        <p className="mt-[7px] text-[11px] text-[#819195]">
          {`Incident #${shortId(incident.id)} · Reported ${reported.charAt(0).toLowerCase()}${reported.slice(1)}`}
        </p>
        <div className="mt-3 flex items-center gap-[7px]">
          <SeverityBadge severity={incident.severity} suffix=" severity" />
          <StatusBadge status={incident.status} />
        </div>
      </div>

      <div className="mt-6 grid gap-[19px] lg:grid-cols-[minmax(0,1.5fr)_minmax(280px,.8fr)]">
        <section className="overflow-hidden rounded-xl border border-line bg-white">
          <PhotoGallery photos={incident.photos.map((photo) => ({ id: photo.id, src: photoUrl(photo.url), previewable: isPreviewable(photo.url) }))} />
          <div className="p-[22px]">
            <ReportBlock title="Technician's description">{incident.description}</ReportBlock>
            <ReportBlock title="Location">{incident.location}</ReportBlock>
          </div>
        </section>
        <div className="self-start">
          <TriageForm
            incidentId={incident.id}
            initial={{ severity: incident.severity, status: incident.status, resolutionNote: incident.resolutionNote ?? '' }}
          />
          <div className="mt-4 rounded-[10px] border border-[#dce8e5] bg-[#f5faf8] px-[17px] py-[15px]">
            <strong className="mb-[7px] block text-[11px] text-[#416360]">Severity guide</strong>
            {[...SEVERITIES].reverse().map((severity) => (
              <p key={severity} className="my-1 text-[10px] leading-snug text-[#72898a]">{`${SEVERITY_LABEL[severity]} · ${SEVERITY_GUIDE[severity]}`}</p>
            ))}
          </div>
        </div>
      </div>
    </AppShell>
  );
}

function ReportBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="[&+&]:mt-[22px] [&+&]:border-t [&+&]:border-line [&+&]:pt-[18px]">
      <h2 className="mb-[7px] text-[10px] font-extrabold uppercase tracking-[.09em] text-[#7c8e91]">{title}</h2>
      <p className="max-w-[640px] whitespace-pre-line text-xs leading-relaxed text-[#405960]">{children}</p>
    </div>
  );
}
