import Link from 'next/link';
import type { ReactNode } from 'react';

function ListIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-[18px] fill-none stroke-current stroke-[1.8]" aria-hidden>
      <path d="M8 6h12M8 12h12M8 18h12M4 6h.1M4 12h.1M4 18h.1" strokeLinecap="round" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-[18px] fill-none stroke-current stroke-[1.8]" aria-hidden>
      <path d="m4 12 5 5L20 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const activeNav = 'bg-teal-soft text-teal-dark';
const navItem = 'my-[3px] flex min-h-[42px] w-full items-center gap-2.5 rounded-[9px] px-[11px] text-xs font-bold text-[#60777a] hover:bg-teal-soft/60';

// Sidebar + topbar from the design. Rendered by each page (not the layout) so the count stays fresh.
export function AppShell({ children, breadcrumb, activeCount, nav = 'queue' }: { children: ReactNode; breadcrumb: ReactNode; activeCount?: number; nav?: 'queue' | 'resolved' }) {
  return (
    <div className="min-h-screen bg-white md:grid md:grid-cols-[170px_minmax(0,1fr)] lg:grid-cols-[218px_minmax(0,1fr)]">
      <aside className="hidden border-r border-line bg-[#fbfdfc] px-2.5 py-6 md:block lg:px-4 lg:py-[27px]">
        <Link href="/incidents" className="mx-2.5 mb-[35px] mt-px flex items-center gap-2.5 text-[13px] font-extrabold tracking-tight text-teal-dark lg:text-base">
          <span className="grid size-[29px] place-items-center rounded-[9px] bg-teal text-lg leading-none text-white">+</span>
          Field Incident
        </Link>
        <p className="mx-2.5 mb-[9px] text-[10px] font-extrabold uppercase tracking-widest text-[#9bacad]">Workspace</p>
        <Link href="/incidents" className={`${navItem} ${nav === 'queue' ? activeNav : ''}`}>
          <ListIcon />
          Incident queue
          {activeCount !== undefined && <span className="ml-auto rounded-[5px] bg-[#d5eae5] px-1.5 py-[3px] text-[10px]">{activeCount}</span>}
        </Link>
        <Link href="/incidents?status=RESOLVED" className={`${navItem} ${nav === 'resolved' ? activeNav : ''}`}>
          <CheckIcon />
          Resolved
        </Link>
        <div className="mx-[9px] mt-[55px] rounded-[10px] border border-[#deebe8] bg-[#f3faf8] p-[13px]">
          <strong className="block text-[11px] text-teal-dark">Keep work moving</strong>
          <p className="mt-1.5 text-[10px] leading-snug text-[#718789]">Review serious incidents first and update status as work progresses.</p>
        </div>
      </aside>
      <div className="min-w-0">
        <header className="flex h-[54px] items-center justify-between gap-3 border-b border-line px-3.5 md:h-16 md:px-5 lg:px-[31px]">
          <span className="text-[11px] text-[#859799]">Workspace / {breadcrumb}</span>
          <span className="flex items-center gap-[9px] text-[11px] font-bold text-[#65797c]">
            <span className="grid size-[30px] place-items-center rounded-full bg-[#dceeea] text-[10px] font-extrabold text-teal-dark">OP</span>
            Operator
          </span>
        </header>
        <main className="px-3.5 pb-[22px] pt-[19px] md:px-5 md:py-6 lg:px-[31px] lg:pb-8 lg:pt-7">{children}</main>
      </div>
    </div>
  );
}
