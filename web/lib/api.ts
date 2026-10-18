// Server-side client for the Express incident API.
import type { Incident, Severity, Status } from './incidents';

const API_BASE_URL = (process.env.API_BASE_URL ?? 'http://localhost:3000').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly fields: Record<string, string> = {},
  ) {
    super(`API ${status} ${code}`);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, cache: 'no-store' });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.error?.code ?? 'UNKNOWN', body?.error?.fields ?? {});
  }
  return response.json() as Promise<T>;
}

export function listIncidents(): Promise<Incident[]> {
  return request('/incidents');
}

export async function getIncident(id: string): Promise<Incident | null> {
  try {
    return await request(`/incidents/${encodeURIComponent(id)}`);
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 404) return null;
    throw failure;
  }
}

export function triageIncident(
  id: string,
  changes: { severity: Severity; status: Status; resolutionNote: string },
): Promise<Incident> {
  return request(`/incidents/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(changes),
  });
}

export function photoUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}
