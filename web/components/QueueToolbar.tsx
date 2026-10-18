'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { queueHref, SEVERITIES, SEVERITY_LABEL, STATUS_LABEL, STATUSES, type QueueQuery } from '@/lib/incidents';

const control = 'min-h-[39px] rounded-lg border border-[#d7e1df] bg-white px-[11px] text-[11px] font-bold text-[#496167]';

export function QueueToolbar({ query }: { query: QueueQuery }) {
  const router = useRouter();
  const [draft, setDraft] = useState(query.q);
  const lastPushed = useRef(query.q);
  const go = (patch: Partial<QueueQuery>) => router.replace(queueHref(query, patch), { scroll: false });

  // Push the search after typing pauses. Cleanup cancels a pending push when the draft or other filters change.
  useEffect(() => {
    const q = draft.trim();
    if (q === query.q) return;
    const timer = setTimeout(() => {
      lastPushed.current = q;
      router.replace(queueHref(query, { q }), { scroll: false });
    }, 300);
    return () => clearTimeout(timer);
  }, [draft, query, router]);

  // The URL changed from outside (Clear filters, Back/Forward): show its search. Polling leaves q unchanged.
  useEffect(() => {
    if (query.q !== lastPushed.current) {
      lastPushed.current = query.q;
      setDraft(query.q);
    }
  }, [query.q]);

  const criticalOnly = query.severity === 'CRITICAL';

  return (
    <div className="mt-[23px] flex flex-wrap items-center gap-[9px]">
      <label className="flex min-h-[39px] w-full items-center gap-2 rounded-lg border border-[#d7e1df] bg-white px-[11px] text-[11px] text-[#93a2a4] sm:w-[295px]">
        <svg viewBox="0 0 24 24" className="size-[18px] flex-none fill-none stroke-current stroke-[1.8]" aria-hidden>
          <circle cx="11" cy="11" r="7" />
          <path d="m16 16 5 5" strokeLinecap="round" />
        </svg>
        <input
          type="search"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Search title or location"
          aria-label="Search title or location"
          className="w-full bg-transparent text-[#304b51] outline-none placeholder:text-[#93a2a4]"
        />
      </label>
      <select aria-label="Status" className={control} value={query.status} onChange={(event) => go({ status: event.target.value as QueueQuery['status'] })}>
        <option value="ACTIVE">Status: Active</option>
        {STATUSES.map((status) => <option key={status} value={status}>Status: {STATUS_LABEL[status]}</option>)}
        <option value="ALL">Status: All</option>
      </select>
      <select aria-label="Severity" className={control} value={query.severity} onChange={(event) => go({ severity: event.target.value as QueueQuery['severity'] })}>
        <option value="ALL">Severity: All</option>
        {[...SEVERITIES].reverse().map((severity) => <option key={severity} value={severity}>Severity: {SEVERITY_LABEL[severity]}</option>)}
      </select>
      <button
        type="button"
        aria-pressed={criticalOnly}
        onClick={() => go({ severity: criticalOnly ? 'ALL' : 'CRITICAL' })}
        className={`${control} ${criticalOnly ? 'border-[#a8362e] bg-[#a8362e] text-white' : 'border-[#e8bdb6] bg-[#fff5f2] text-[#9d352b]'}`}
      >
        Critical only
      </button>
      <select aria-label="Sort" className={`${control} border-transparent bg-transparent font-normal text-[#718589] sm:ml-auto`} value={query.sort} onChange={(event) => go({ sort: event.target.value as QueueQuery['sort'] })}>
        <option value="severity">Sort: Highest severity first</option>
        <option value="newest">Sort: Newest first</option>
      </select>
    </div>
  );
}
