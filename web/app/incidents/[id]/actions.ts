'use server';

import { revalidatePath } from 'next/cache';
import { ApiError, triageIncident } from '@/lib/api';
import type { Severity, Status } from '@/lib/incidents';

export type TriageValues = { severity: Severity; status: Status; resolutionNote: string };
export type TriageState = { result: 'idle' | 'saved' | 'error'; message?: string; fields?: Record<string, string> };

export async function saveTriage(id: string, _prev: TriageState, formData: FormData): Promise<TriageState> {
  const changes = {
    severity: String(formData.get('severity')) as Severity,
    status: String(formData.get('status')) as Status,
    resolutionNote: String(formData.get('resolutionNote') ?? ''),
  };
  try {
    await triageIncident(id, changes);
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 404) return { result: 'error', message: 'This incident no longer exists.' };
    if (failure instanceof ApiError && failure.status === 413) return { result: 'error', message: 'Resolution note is too long. Shorten it and try again.' };
    if (failure instanceof ApiError && failure.status === 400) return { result: 'error', message: 'Please check the highlighted fields.', fields: failure.fields };
    return { result: 'error', message: "Couldn't save. Check the API is running and try again." };
  }
  revalidatePath('/incidents');
  revalidatePath(`/incidents/${id}`);
  return { result: 'saved', message: 'Changes saved.' };
}
