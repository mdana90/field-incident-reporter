import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import multer from 'multer';
import { createStore } from './store.js';

const severities = new Set(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);
const statuses = new Set(['OPEN', 'IN_PROGRESS', 'RESOLVED']);
const imageTypes = new Set(['image/jpeg']);

function error(res, status, code, fields = {}) {
  return res.status(status).json({ error: { code, fields } });
}

function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function validate(body, count) {
  const fields = {};
  for (const name of ['title', 'description', 'location']) {
    if (!text(body[name])) fields[name] = 'Required';
  }
  if (!severities.has(body.severity)) fields.severity = 'Choose a valid severity';
  if (count < 1 || count > 3) fields.photos = 'Choose 1 to 3 photos';
  return fields;
}

function validateTriage(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return { fields: { body: 'Expected a JSON object' } };
  const fields = {};
  const changes = {};
  if ('severity' in body) {
    if (severities.has(body.severity)) changes.severity = body.severity;
    else fields.severity = 'Choose a valid severity';
  }
  if ('status' in body) {
    if (statuses.has(body.status)) changes.status = body.status;
    else fields.status = 'Choose a valid status';
  }
  if ('resolutionNote' in body) {
    if (typeof body.resolutionNote === 'string') changes.resolutionNote = body.resolutionNote.trim();
    else fields.resolutionNote = 'Must be text';
  }
  if (!Object.keys(fields).length && !Object.keys(changes).length) fields.body = 'Nothing to update';
  return { fields, changes };
}

export function createApp({ dataDir = path.resolve('data') } = {}) {
  const store = createStore(dataDir);
  const app = express();
  const upload = multer({
    storage: multer.diskStorage({
      destination: store.uploadDir,
      filename: (_req, file, callback) => {
        callback(null, `${randomUUID()}.jpg`);
      },
    }),
    fileFilter: (_req, file, callback) => callback(imageTypes.has(file.mimetype) ? null : new Error('UNSUPPORTED_IMAGE'), true),
    limits: { fileSize: 10 * 1024 * 1024, files: 3 },
  }).array('photos', 3);

  function multipart(req, res, next) {
    upload(req, res, (failure) => {
      if (failure) {
        cleanup(req.files);
        return error(res, failure.code === 'LIMIT_FILE_SIZE' ? 413 : 400, failure.code ?? failure.message ?? 'UPLOAD_FAILED');
      }
      next();
    });
  }

  function cleanup(files = []) {
    for (const file of files) fs.rmSync(file.path, { force: true });
  }

  function photo(file) {
    return { id: randomUUID(), url: `/uploads/${file.filename}` };
  }

  function isJpeg(file) {
    const bytes = Buffer.alloc(2);
    const descriptor = fs.openSync(file.path, 'r');
    try {
      return fs.readSync(descriptor, bytes, 0, 2, 0) === 2 && bytes[0] === 0xff && bytes[1] === 0xd8;
    } finally {
      fs.closeSync(descriptor);
    }
  }

  app.use('/uploads', express.static(store.uploadDir));

  app.get('/incidents', (_req, res) => res.json(store.list().map(publicIncident)));
  app.get('/incidents/:id', (req, res) => {
    const incident = store.get(req.params.id);
    return incident ? res.json(publicIncident(incident)) : error(res, 404, 'NOT_FOUND');
  });

  app.post('/incidents', multipart, (req, res) => {
    const files = req.files ?? [];
    if (files.some((file) => !isJpeg(file))) {
      cleanup(files);
      return error(res, 400, 'UNSUPPORTED_IMAGE');
    }
    const body = req.body ?? {};
    const clientRequestId = text(body.clientRequestId);
    if (!clientRequestId) {
      cleanup(files);
      return error(res, 400, 'VALIDATION_FAILED', { clientRequestId: 'Required' });
    }
    const existing = store.findRequest(clientRequestId);
    if (existing) {
      cleanup(files);
      return res.json(publicIncident(existing));
    }
    const fields = validate(body, files.length);
    if (Object.keys(fields).length) {
      cleanup(files);
      return error(res, 400, 'VALIDATION_FAILED', fields);
    }
    const now = new Date().toISOString();
    const incident = {
      id: randomUUID(), clientRequestId,
      title: text(body.title), description: text(body.description),
      severity: body.severity, location: text(body.location), status: 'OPEN',
      photos: files.map(photo), createdAt: now, updatedAt: now,
    };
    try {
      const result = store.create(incident);
      if (!result.created) cleanup(files);
      return res.status(result.created ? 201 : 200).json(publicIncident(result.incident));
    } catch (failure) {
      cleanup(files);
      throw failure;
    }
  });

  app.put('/incidents/:id', multipart, (req, res) => {
    const files = req.files ?? [];
    if (files.some((file) => !isJpeg(file))) {
      cleanup(files);
      return error(res, 400, 'UNSUPPORTED_IMAGE');
    }
    const body = req.body ?? {};
    const previous = store.get(req.params.id);
    if (!previous) {
      cleanup(files);
      return error(res, 404, 'NOT_FOUND');
    }
    let keepIds;
    try {
      keepIds = body.keepPhotoIds ? JSON.parse(body.keepPhotoIds) : [];
      if (!Array.isArray(keepIds) || !keepIds.every((id) => typeof id === 'string')) throw new Error('Invalid keepPhotoIds');
    } catch {
      cleanup(files);
      return error(res, 400, 'VALIDATION_FAILED', { keepPhotoIds: 'Invalid photo selection' });
    }
    const retained = previous.photos.filter((item) => keepIds.includes(item.id));
    const fields = validate(body, retained.length + files.length);
    if (retained.length !== new Set(keepIds).size) fields.keepPhotoIds = 'Unknown photo ID';
    if (Object.keys(fields).length) {
      cleanup(files);
      return error(res, 400, 'VALIDATION_FAILED', fields);
    }
    const incident = {
      ...previous,
      title: text(body.title), description: text(body.description),
      severity: body.severity, location: text(body.location),
      photos: [...retained, ...files.map(photo)], updatedAt: new Date().toISOString(),
    };
    try {
      store.update(previous.id, incident);
      return res.json(publicIncident(incident));
    } catch (failure) {
      cleanup(files);
      throw failure;
    }
  });

  app.patch('/incidents/:id', express.json({ limit: '16kb' }), (req, res) => {
    const { fields, changes } = validateTriage(req.body);
    if (Object.keys(fields).length) return error(res, 400, 'VALIDATION_FAILED', fields);
    const previous = store.get(req.params.id);
    if (!previous) return error(res, 404, 'NOT_FOUND');
    const incident = { ...previous, ...changes, updatedAt: new Date().toISOString() };
    store.update(previous.id, incident);
    return res.json(publicIncident(incident));
  });

  app.delete('/incidents/:id', (req, res) => store.remove(req.params.id)
    ? res.status(204).end() : error(res, 404, 'NOT_FOUND'));

  app.use((failure, req, res, _next) => {
    cleanup(req.files);
    if (failure.type === 'entity.parse.failed') return error(res, 400, 'INVALID_JSON');
    if (failure.type === 'entity.too.large') return error(res, 413, 'PAYLOAD_TOO_LARGE');
    console.error(failure);
    return error(res, 500, 'INTERNAL_ERROR');
  });

  return app;
}

function publicIncident({ clientRequestId, ...incident }) {
  return incident;
}
