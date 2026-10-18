import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { createApp } from '../src/app.js';

let server;
let dataDir;
let baseUrl;

beforeEach(async () => {
  dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'incident-api-'));
  server = createApp({ dataDir }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

afterEach(async () => {
  await new Promise((resolve) => server.close(resolve));
  fs.rmSync(dataDir, { recursive: true, force: true });
});

function report({ requestId = randomUUID(), title = 'Leaking pipe', photos = 1, keepPhotoIds, ...overrides } = {}) {
  const form = new FormData();
  const fields = {
    clientRequestId: requestId,
    title,
    description: 'Water is leaking above the sink.',
    severity: 'HIGH',
    location: 'Building A, Floor 2',
    ...overrides,
  };
  for (const [key, value] of Object.entries(fields)) form.append(key, value);
  if (keepPhotoIds !== undefined) form.append('keepPhotoIds', JSON.stringify(keepPhotoIds));
  for (let index = 0; index < photos; index++) {
    form.append('photos', new Blob([Buffer.from([0xff, 0xd8, 0xff, 0xd9])], { type: 'image/jpeg' }), `photo-${index}.jpg`);
  }
  return form;
}

async function create(options) {
  return fetch(`${baseUrl}/incidents`, { method: 'POST', body: report(options) });
}

function uploadedFiles() {
  return fs.readdirSync(path.join(dataDir, 'uploads'));
}

test('create, list, view, edit all photos, and delete', async () => {
  const created = await create();
  assert.equal(created.status, 201);
  const incident = await created.json();
  assert.equal(incident.status, 'OPEN');
  assert.equal(incident.photos.length, 1);
  assert.equal('clientRequestId' in incident, false);
  assert.equal((await fetch(`${baseUrl}${incident.photos[0].url}`)).status, 200);

  const listed = await (await fetch(`${baseUrl}/incidents`)).json();
  assert.equal(listed[0].id, incident.id);
  assert.equal((await (await fetch(`${baseUrl}/incidents/${incident.id}`)).json()).title, 'Leaking pipe');

  const edited = await fetch(`${baseUrl}/incidents/${incident.id}`, {
    method: 'PUT',
    body: report({ title: 'Fixed pipe', status: 'RESOLVED', id: 'forged', photos: 1 }),
  });
  assert.equal(edited.status, 200);
  const updated = await edited.json();
  assert.equal(updated.title, 'Fixed pipe');
  assert.equal(updated.status, 'OPEN');
  assert.equal(updated.id, incident.id);
  assert.notEqual(updated.photos[0].id, incident.photos[0].id);
  assert.equal(uploadedFiles().length, 1);

  assert.equal((await fetch(`${baseUrl}/incidents/${incident.id}`, { method: 'DELETE' })).status, 204);
  assert.equal((await fetch(`${baseUrl}/incidents/${incident.id}`)).status, 404);
  assert.equal(uploadedFiles().length, 0);
});

test('invalid fields and missing incident clean newly uploaded files', async () => {
  const invalid = await create({ title: '' });
  assert.equal(invalid.status, 400);
  assert.equal((await invalid.json()).error.fields.title, 'Required');
  assert.equal(uploadedFiles().length, 0);

  const missing = await fetch(`${baseUrl}/incidents/missing`, { method: 'PUT', body: report() });
  assert.equal(missing.status, 404);
  assert.equal(uploadedFiles().length, 0);
});

test('replaying a request ID returns the original and removes replay photos', async () => {
  const requestId = randomUUID();
  const first = await create({ requestId });
  const original = await first.json();
  const replay = await create({ requestId });
  assert.equal(replay.status, 200);
  assert.equal((await replay.json()).id, original.id);
  assert.equal(uploadedFiles().length, 1);
  assert.equal((await (await fetch(`${baseUrl}/incidents`)).json()).length, 1);
});

test('retaining unknown photos and removing every photo are rejected without orphans', async () => {
  const original = await (await create()).json();
  const invalid = await fetch(`${baseUrl}/incidents/${original.id}`, {
    method: 'PUT', body: report({ keepPhotoIds: ['unknown'] }),
  });
  assert.equal(invalid.status, 400);
  assert.equal(uploadedFiles().length, 1);

  const empty = await fetch(`${baseUrl}/incidents/${original.id}`, {
    method: 'PUT', body: report({ photos: 0, keepPhotoIds: [] }),
  });
  assert.equal(empty.status, 400);
  assert.equal(uploadedFiles().length, 1);
});

test('unsupported images are rejected', async () => {
  const form = report({ photos: 0 });
  form.append('photos', new Blob(['text'], { type: 'text/plain' }), 'note.txt');
  const response = await fetch(`${baseUrl}/incidents`, { method: 'POST', body: form });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.code, 'UNSUPPORTED_IMAGE');
  assert.equal(uploadedFiles().length, 0);
});
