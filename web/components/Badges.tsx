import { SEVERITY_LABEL, STATUS_LABEL, type Severity, type Status } from '@/lib/incidents';

const base = 'inline-flex items-center gap-1 whitespace-nowrap rounded-md px-2 py-1.5 text-[10px] font-extrabold';

// Colors from the design's .badge.* rules.
const SEVERITY_STYLE: Record<Severity, string> = {
  CRITICAL: 'bg-[#fbe7e3] text-[#a82d25]',
  HIGH: 'bg-[#fff0d8] text-[#99600f]',
  MEDIUM: 'bg-[#eaf0f7] text-[#4d6c90]',
  LOW: 'bg-[#e7f3ec] text-[#377454]',
};

const STATUS_STYLE: Record<Status, string> = {
  OPEN: 'bg-[#fff2dc] text-[#99600f]',
  IN_PROGRESS: 'bg-[#e7f2f9] text-[#2b6988]',
  RESOLVED: 'bg-[#e7f3eb] text-[#317358]',
};

export function SeverityBadge({ severity, suffix = '' }: { severity: Severity; suffix?: string }) {
  return <span className={`${base} ${SEVERITY_STYLE[severity]}`}>{SEVERITY_LABEL[severity]}{suffix}</span>;
}

export function StatusBadge({ status }: { status: Status }) {
  return <span className={`${base} ${STATUS_STYLE[status]}`}>{STATUS_LABEL[status]}</span>;
}
