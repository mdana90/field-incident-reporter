# Local incident API

Base URL: `http://localhost:3000` on the development computer. Run `adb reverse tcp:3000 tcp:3000` before opening the Android app; its debug URL is `http://127.0.0.1:3000/`. Images use relative URLs such as `/uploads/<file>.jpg`.

## Incident

```json
{
  "id": "server-uuid",
  "title": "Leaking water pipe",
  "description": "Water is leaking above the sink.",
  "severity": "HIGH",
  "location": "Building A, Floor 2",
  "status": "OPEN",
  "photos": [{ "id": "photo-uuid", "url": "/uploads/example.jpg" }],
  "createdAt": "2026-10-18T08:00:00.000Z",
  "updatedAt": "2026-10-18T08:00:00.000Z"
}
```

`severity` is one of `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`. `status` is server-owned and begins `OPEN`. IDs and timestamps are server-owned.

## Routes

| Route | Request | Success |
| --- | --- | --- |
| `GET /incidents` | None | `200` array, newest first |
| `GET /incidents/:id` | None | `200` incident |
| `POST /incidents` | Multipart form below | `201` incident; `200` original incident if `clientRequestId` is replayed |
| `PUT /incidents/:id` | Multipart form below | `200` updated incident |
| `DELETE /incidents/:id` | None | `204` empty body |

Create fields: `clientRequestId` (stable UUID for every manual retry of the same saved draft), `title`, `description`, `severity`, `location`, and one to three `photos` files. Edit fields: `title`, `description`, `severity`, `location`, `keepPhotoIds` (JSON array of old photo IDs to retain), and zero to three new `photos` files. Missing `keepPhotoIds` means `[]`, so all old photos are removed. The final edit result must have one to three photos. Only JPEG is accepted, up to 10 MiB per file.

Example create request:

```sh
curl -F 'clientRequestId=915cd1bc-57c3-4cd3-9047-63c81c7610a3' \
  -F 'title=Leaking water pipe' -F 'description=Water above the sink' \
  -F 'severity=HIGH' -F 'location=Building A, Floor 2' \
  -F 'photos=@photo.jpg;type=image/jpeg' http://localhost:3000/incidents
```

Example edit that replaces all old photos:

```sh
curl -X PUT -F 'title=Pipe repaired' -F 'description=Repair completed' \
  -F 'severity=LOW' -F 'location=Building A, Floor 2' \
  -F 'keepPhotoIds=[]' -F 'photos=@replacement.jpg;type=image/jpeg' \
  http://localhost:3000/incidents/INCIDENT_ID
```

Errors use `{"error":{"code":"VALIDATION_FAILED","fields":{"title":"Required"}}}`. Invalid fields or unsupported image types return `400`, missing incidents return `404`, oversized images return `413`, and unexpected failures return `500`. An invalid or replayed multipart request removes newly uploaded files. A technician edit ignores client-supplied server-owned fields.

The Android app saves the exact create payload locally before Send. A lost reply leaves an `UNCONFIRMED` draft, which is retried manually with unchanged fields, photos, and `clientRequestId`. The API does not upload drafts by itself.
