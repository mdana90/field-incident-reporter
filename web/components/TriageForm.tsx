'use client';

import { startTransition, useActionState, useLayoutEffect, useState } from 'react';
import { saveTriage, type TriageState, type TriageValues } from '@/app/incidents/[id]/actions';
import { SEVERITIES, SEVERITY_LABEL, STATUS_LABEL, STATUSES } from '@/lib/incidents';

const control = 'w-full min-h-[41px] rounded-lg border border-[#cddcd9] bg-white px-[11px] text-xs text-[#304b51]';
const label = 'mb-[7px] block text-[11px] font-extrabold text-[#425b61]';

export function TriageForm({ incidentId, initial }: { incidentId: string; initial: TriageValues }) {
  const [state, action, pending] = useActionState<TriageState, FormData>(saveTriage.bind(null, incidentId), { result: 'idle' });
  // Controlled inputs submitted via onSubmit, not <form action>: React resets a form after an action, which
  // reverts a controlled <select> to its first-rendered option and would drop the operator's values on a failed save.
  const [values, setValues] = useState(initial);
  // Next keeps this route mounted (Activity) after navigating away, so state outlives the visit.
  // When the server values change (e.g. a technician edit), show them instead of the stale ones.
  const [synced, setSynced] = useState(initial);
  if (initial.severity !== synced.severity || initial.status !== synced.status || initial.resolutionNote !== synced.resolutionNote) {
    setSynced(initial);
    setValues(initial);
  }
  // Hide the last save message once the page is hidden, so it doesn't reappear on the next visit.
  const [messageHidden, setMessageHidden] = useState(false);
  useLayoutEffect(() => () => setMessageHidden(true), []);
  const set = <K extends keyof TriageValues>(key: K, value: TriageValues[K]) => setValues((current) => ({ ...current, [key]: value }));
  const fieldError = (name: keyof TriageValues) => state.fields?.[name] && <p className="mt-1.5 text-[10px] text-red">{state.fields[name]}</p>;

  return (
    <form
      className="rounded-xl border border-line bg-white p-[21px]"
      onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setMessageHidden(false);
        startTransition(() => action(data));
      }}
    >
      <h2 className="text-[17px] font-bold">Operator triage</h2>
      <p className="mb-[22px] mt-1.5 text-[11px] leading-snug text-[#819397]">Review the report and update the incident for the maintenance team.</p>

      <label className="mt-4 block">
        <span className={label}>Severity</span>
        <select name="severity" className={control} value={values.severity} onChange={(event) => set('severity', event.target.value as TriageValues['severity'])}>
          {SEVERITIES.map((severity) => <option key={severity} value={severity}>{SEVERITY_LABEL[severity]}</option>)}
        </select>
      </label>
      <p className="mt-1.5 text-[10px] leading-snug text-[#839598]">Reported by the technician. Adjust after reviewing the evidence.</p>
      {fieldError('severity')}

      <label className="mt-4 block">
        <span className={label}>Status</span>
        <select name="status" className={control} value={values.status} onChange={(event) => set('status', event.target.value as TriageValues['status'])}>
          {STATUSES.map((status) => <option key={status} value={status}>{STATUS_LABEL[status]}</option>)}
        </select>
      </label>
      {fieldError('status')}

      <label className="mt-4 block">
        <span className={label}>Resolution note <small className="font-normal">(optional)</small></span>
        <textarea
          name="resolutionNote"
          className={`${control} min-h-[85px] resize-none py-2.5 leading-snug`}
          placeholder="What was done to resolve this incident?"
          value={values.resolutionNote}
          onChange={(event) => set('resolutionNote', event.target.value)}
        />
      </label>
      {fieldError('resolutionNote')}

      <button type="submit" disabled={pending} className="mt-5 min-h-[42px] w-full rounded-lg bg-teal text-xs font-extrabold text-white disabled:opacity-70">
        {pending ? 'Saving…' : 'Save changes'}
      </button>
      <p role="status" className={`mt-3 min-h-4 text-[11px] font-bold ${state.result === 'error' ? 'text-red' : 'text-teal'}`}>
        {pending || messageHidden ? '' : state.message}
      </p>
    </form>
  );
}
