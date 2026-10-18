// Starts the real Express API on a temporary data directory for the e2e suite.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createApp } from '../../server/src/app.js';

const port = Number(process.argv[2] ?? 3100);
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'incident-e2e-'));
const server = createApp({ dataDir }).listen(port, '127.0.0.1', () => console.log(`e2e API on ${port} (${dataDir})`));

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close();
    fs.rmSync(dataDir, { recursive: true, force: true });
    process.exit(0);
  });
}
