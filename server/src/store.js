import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export function createStore(dataDir) {
  fs.mkdirSync(dataDir, { recursive: true });
  const uploadDir = path.join(dataDir, 'uploads');
  fs.mkdirSync(uploadDir, { recursive: true });
  const file = path.join(dataDir, 'incidents.json');
  if (!fs.existsSync(file)) {
    const seed = path.join(dataDir, 'seed.json');
    fs.copyFileSync(fs.existsSync(seed) ? seed : new URL('../data/seed.json', import.meta.url), file);
  }

  function read() {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  }

  function write(incidents) {
    const temporary = `${file}.${randomUUID()}.tmp`;
    fs.writeFileSync(temporary, JSON.stringify(incidents, null, 2));
    fs.renameSync(temporary, file);
  }

  function removeFiles(photos) {
    for (const photo of photos) fs.rmSync(path.join(uploadDir, path.basename(photo.url)), { force: true });
  }

  return {
    uploadDir,
    list: () => read().sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    get: (id) => read().find((item) => item.id === id),
    findRequest: (clientRequestId) => read().find((item) => item.clientRequestId === clientRequestId),
    create(incident) {
      // In this single-process server, synchronous lookup and write keep retries from creating duplicate reports.
      const incidents = read();
      const existing = incidents.find((item) => item.clientRequestId === incident.clientRequestId);
      if (existing) return { incident: existing, created: false };
      incidents.push(incident);
      write(incidents);
      return { incident, created: true };
    },
    update(id, incident) {
      const incidents = read();
      const index = incidents.findIndex((item) => item.id === id);
      if (index < 0) return false;
      const oldPhotos = incidents[index].photos;
      incidents[index] = incident;
      write(incidents);
      removeFiles(oldPhotos.filter((photo) => !incident.photos.some((kept) => kept.id === photo.id)));
      return true;
    },
    remove(id) {
      const incidents = read();
      const index = incidents.findIndex((item) => item.id === id);
      if (index < 0) return false;
      const [incident] = incidents.splice(index, 1);
      write(incidents);
      removeFiles(incident.photos);
      return true;
    },
  };
}
