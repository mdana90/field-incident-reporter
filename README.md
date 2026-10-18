# Field Incident Reporter

An Android project for a field technician to report a physical incident with a photo, then view, edit, or delete the report. The project is based on the senior Android engineer technical assessment in this repository.

## Operator web dashboard (optional bonus)

A Next.js dashboard in `web/` where an operator reviews incidents reported from the Android app, filters the queue, and triages each report (severity, status, resolution note). It reads and writes through the same local API as the app.

Run the API and the dashboard in two terminals (Node 20+):

```sh
cd server && npm install && npm start          # API on http://localhost:3000
cd web && npm install && npm run dev           # dashboard on http://localhost:3001
```

Set `API_BASE_URL` if the API runs elsewhere (default `http://localhost:3000`).

- **Queue:** search, status/severity filters, "Critical only", and sort live in the URL. The queue refreshes every 15 seconds, so new reports from the phone appear without reloading.
- **Detail:** photos, description, location, and a triage form that saves through `PATCH /incidents/:id`. Technician edits on Android keep the operator's status and note.
- **Photos:** HEIC/HEIF uploads can't be shown by most browsers; they appear as a tile with an "Open file" link.

Tests:

```sh
cd server && npm test   # API, including PATCH triage
cd web && npm test      # queue filtering, sorting, and formatting
```
