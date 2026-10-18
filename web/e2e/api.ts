// Talks to the e2e API directly, standing in for the Android app.
import { API_URL } from './env';
import type { Incident, Severity } from '../lib/incidents';

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

type Report = { title: string; severity?: Severity; location?: string; photos?: number; heic?: boolean };

export async function report({ title, severity = 'HIGH', location = 'Building A', photos = 1, heic = false }: Report): Promise<Incident> {
  const form = new FormData();
  const fields = { clientRequestId: crypto.randomUUID(), title, description: `${title} description`, location, severity };
  for (const [key, value] of Object.entries(fields)) form.append(key, value);
  for (let index = 0; index < photos; index++) {
    form.append('photos', new Blob([JPEG], { type: heic ? 'image/heic' : 'image/jpeg' }), `photo-${index}.${heic ? 'heic' : 'jpg'}`);
  }
  const response = await fetch(`${API_URL}/incidents`, { method: 'POST', body: form });
  if (!response.ok) throw new Error(`report failed: ${response.status}`);
  return response.json();
}

// Same request the Android edit screen sends.
export async function editOnAndroid(incident: Incident, { severity = incident.severity, keepPhotos = incident.photos.length }: { severity?: Severity; keepPhotos?: number }) {
  const form = new FormData();
  const keepPhotoIds = JSON.stringify(incident.photos.slice(0, keepPhotos).map((photo) => photo.id));
  const fields = { title: incident.title, description: incident.description, location: incident.location, severity, keepPhotoIds };
  for (const [key, value] of Object.entries(fields)) form.append(key, value);
  await fetch(`${API_URL}/incidents/${incident.id}`, { method: 'PUT', body: form });
}

export async function remove(id: string) {
  await fetch(`${API_URL}/incidents/${id}`, { method: 'DELETE' });
}

export async function fetchIncident(id: string): Promise<Incident> {
  return (await fetch(`${API_URL}/incidents/${id}`)).json();
}

export async function activeCount(): Promise<number> {
  const incidents: Incident[] = await (await fetch(`${API_URL}/incidents`)).json();
  return incidents.filter((incident) => incident.status !== 'RESOLVED').length;
}

// Unique per test so tests can isolate their rows with the search box.
export const unique = (label: string) => `${label} ${crypto.randomUUID().slice(0, 6)}`;
