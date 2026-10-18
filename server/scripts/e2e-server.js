// Test-only API fixture for SavedDraftE2eTest.
// Run with E2E_DATA_DIR=$(mktemp -d) node scripts/e2e-server.js, then adb reverse tcp:3000 tcp:3000.
import express from 'express';
import { createApp } from '../src/app.js';

const dataDir = process.env.E2E_DATA_DIR;
if (!dataDir) throw new Error('Set E2E_DATA_DIR to a disposable directory');

const modes = new Set(['normal', 'list_503', 'create_503', 'create_400', 'create_lost_reply']);
let mode = 'normal';
const app = express();

app.get('/__e2e/control', (req, res) => {
  if (!modes.has(req.query.mode)) return res.status(400).json({ error: 'Unknown test mode' });
  mode = req.query.mode;
  return res.json({ mode });
});

app.use((req, res, next) => {
  if (req.method === 'GET' && req.path === '/incidents' && mode === 'list_503') {
    return res.status(503).json({ error: { code: 'SERVER_ERROR' } });
  }
  if (req.method === 'POST' && req.path === '/incidents') {
    if (mode === 'create_503') return res.status(503).json({ error: { code: 'SERVER_ERROR' } });
    if (mode === 'create_400') return res.status(400).json({ error: { code: 'VALIDATION_FAILED' } });
    if (mode === 'create_lost_reply') {
      const originalJson = res.json.bind(res);
      res.json = (body) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          res.status(503);
          return originalJson({ error: { code: 'SERVER_ERROR' } });
        }
        return originalJson(body);
      };
    }
  }
  next();
});

app.use(createApp({ dataDir }));
app.listen(3000, '127.0.0.1', () => console.log(`E2E API listening on 127.0.0.1:3000 with ${dataDir}`));
