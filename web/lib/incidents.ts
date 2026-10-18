// Incident types and queue logic shared by server and client components.

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Status = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export type Photo = { id: string; url: string };
export type Incident = {
  id: string;
  title: string;
  description: string;
  location: string;
  severity: Severity;
  status: Status;
  photos: Photo[];
  resolutionNote?: string;
  createdAt: string;
  updatedAt: string;
};

export const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const STATUSES: Status[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED'];

export const SEVERITY_LABEL: Record<Severity, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };
export const STATUS_LABEL: Record<Status, string> = { OPEN: 'Open', IN_PROGRESS: 'In Progress', RESOLVED: 'Resolved' };
export const SEVERITY_GUIDE: Record<Severity, string> = {
  CRITICAL: 'immediate danger',
  HIGH: 'serious hazard or disruption',
  MEDIUM: 'functional issue without immediate danger',
  LOW: 'minor or cosmetic issue',
};

export type StatusFilter = 'ACTIVE' | 'ALL' | Status;
export type QueueQuery = { q: string; status: StatusFilter; severity: Severity | 'ALL'; sort: 'severity' | 'newest' };

const DEFAULT_QUERY: QueueQuery = { q: '', status: 'ACTIVE', severity: 'ALL', sort: 'severity' };

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(value: string | undefined, allowed: readonly T[], fallback: T): T {
  return allowed.includes(value as T) ? (value as T) : fallback;
}

export function parseQueueQuery(params: Record<string, string | string[] | undefined>): QueueQuery {
  return {
    q: (first(params.q) ?? '').trim(),
    status: oneOf<StatusFilter>(first(params.status), ['ACTIVE', 'ALL', ...STATUSES], DEFAULT_QUERY.status),
    severity: oneOf<Severity | 'ALL'>(first(params.severity), ['ALL', ...SEVERITIES], DEFAULT_QUERY.severity),
    sort: oneOf<QueueQuery['sort']>(first(params.sort), ['severity', 'newest'], DEFAULT_QUERY.sort),
  };
}

export function queueHref(query: QueueQuery, patch: Partial<QueueQuery>): string {
  const next = { ...query, ...patch };
  const params = new URLSearchParams();
  for (const key of ['q', 'status', 'severity', 'sort'] as const) {
    if (next[key] !== DEFAULT_QUERY[key]) params.set(key, next[key]);
  }
  const search = params.toString();
  return search ? `/incidents?${search}` : '/incidents';
}

const isActive = (incident: Incident) => incident.status !== 'RESOLVED';
const newestFirst = (a: Incident, b: Incident) => b.createdAt.localeCompare(a.createdAt);

export function filterIncidents(incidents: Incident[], query: QueueQuery): Incident[] {
  const needle = query.q.toLowerCase();
  return incidents
    .filter((incident) => (query.status === 'ALL' || (query.status === 'ACTIVE' ? isActive(incident) : incident.status === query.status)))
    .filter((incident) => query.severity === 'ALL' || incident.severity === query.severity)
    .filter((incident) => !needle || `${incident.title}\n${incident.location}`.toLowerCase().includes(needle))
    .sort((a, b) => (query.sort === 'severity' ? SEVERITIES.indexOf(b.severity) - SEVERITIES.indexOf(a.severity) : 0) || newestFirst(a, b));
}

export function summarize(incidents: Incident[]) {
  return {
    critical: incidents.filter((incident) => isActive(incident) && incident.severity === 'CRITICAL').length,
    open: incidents.filter((incident) => incident.status === 'OPEN').length,
    inProgress: incidents.filter((incident) => incident.status === 'IN_PROGRESS').length,
    active: incidents.filter(isActive).length,
  };
}

export function formatReported(iso: string, now = new Date()): string {
  const date = new Date(iso);
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000);
  // Recent ICU puts a narrow no-break space before AM/PM.
  const time = date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }).replace(/ /g, ' ');
  if (days === 0) return `Today, ${time}`;
  if (days === 1) return `Yesterday, ${time}`;
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export function isPreviewable(photoUrl: string): boolean {
  return !/\.(heic|heif)$/i.test(photoUrl);
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}
