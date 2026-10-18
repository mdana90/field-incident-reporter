import { describe, expect, test } from 'vitest';
import { filterIncidents, formatReported, isPreviewable, parseQueueQuery, queueHref, summarize, type Incident } from './incidents';

const make = (over: Partial<Incident>): Incident => ({
  id: 'id', title: 'T', description: 'D', location: 'L', severity: 'LOW', status: 'OPEN',
  photos: [], createdAt: '2026-10-18T08:00:00.000Z', updatedAt: '2026-10-18T08:00:00.000Z', ...over,
});
const wiring = make({ id: 'a', title: 'Exposed wiring', location: 'Building B', severity: 'CRITICAL', createdAt: '2026-10-18T01:00:00.000Z' });
const pipe = make({ id: 'b', title: 'Leaking pipe', location: 'Building A, Room 204', severity: 'HIGH', status: 'IN_PROGRESS', createdAt: '2026-10-18T02:00:00.000Z' });
const stain = make({ id: 'c', title: 'Ceiling stain', location: 'Building C', severity: 'LOW', createdAt: '2026-10-18T03:00:00.000Z' });
const fixed = make({ id: 'd', title: 'Old crack', location: 'Gate 3', severity: 'CRITICAL', status: 'RESOLVED', createdAt: '2026-10-18T04:00:00.000Z' });
const all = [stain, fixed, pipe, wiring];
const ids = (list: Incident[]) => list.map((i) => i.id);

describe('parseQueueQuery', () => {
  test('defaults', () => expect(parseQueueQuery({})).toEqual({ q: '', status: 'ACTIVE', severity: 'ALL', sort: 'severity' }));
  test('valid values', () => expect(parseQueueQuery({ q: ' pipe ', status: 'RESOLVED', severity: 'CRITICAL', sort: 'newest' }))
    .toEqual({ q: 'pipe', status: 'RESOLVED', severity: 'CRITICAL', sort: 'newest' }));
  test('garbage falls back to defaults', () => expect(parseQueueQuery({ status: 'foo', severity: 'bar', sort: 'x' }))
    .toEqual({ q: '', status: 'ACTIVE', severity: 'ALL', sort: 'severity' }));
  test('repeated params use the first value', () => expect(parseQueueQuery({ status: ['OPEN', 'RESOLVED'] }).status).toBe('OPEN'));
});

describe('filterIncidents', () => {
  const base = parseQueueQuery({});
  test('default: active only, highest severity first, ties newest first', () =>
    expect(ids(filterIncidents([...all, make({ id: 'e', severity: 'HIGH', createdAt: '2026-10-18T05:00:00.000Z' })], base))).toEqual(['a', 'e', 'b', 'c']));
  test('status ALL and RESOLVED', () => {
    expect(ids(filterIncidents(all, { ...base, status: 'ALL' }))).toEqual(['d', 'a', 'b', 'c']);
    expect(ids(filterIncidents(all, { ...base, status: 'RESOLVED' }))).toEqual(['d']);
  });
  test('severity filter', () => expect(ids(filterIncidents(all, { ...base, severity: 'CRITICAL' }))).toEqual(['a']));
  test('search is case-insensitive over title and location', () => {
    expect(ids(filterIncidents(all, { ...base, q: 'PIPE' }))).toEqual(['b']);
    expect(ids(filterIncidents(all, { ...base, q: 'room 204' }))).toEqual(['b']);
  });
  test('newest sort', () => expect(ids(filterIncidents(all, { ...base, sort: 'newest' }))).toEqual(['c', 'b', 'a']));
});

test('summarize counts from the full list', () =>
  expect(summarize(all)).toEqual({ critical: 1, open: 2, inProgress: 1, active: 3 }));

test('queueHref omits defaults', () => {
  const base = parseQueueQuery({});
  expect(queueHref(base, { severity: 'CRITICAL' })).toBe('/incidents?severity=CRITICAL');
  expect(queueHref({ ...base, severity: 'CRITICAL' }, { severity: 'ALL' })).toBe('/incidents');
});

test('formatReported', () => {
  const now = new Date(2026, 9, 18, 12, 0);
  expect(formatReported(new Date(2026, 9, 18, 8, 42).toISOString(), now)).toBe('Today, 8:42 AM');
  expect(formatReported(new Date(2026, 9, 17, 23, 59).toISOString(), now)).toBe('Yesterday, 11:59 PM');
  expect(formatReported(new Date(2026, 9, 3, 9, 0).toISOString(), now)).toBe('Oct 3, 2026');
});

test('isPreviewable', () => {
  expect(isPreviewable('/uploads/a.jpg')).toBe(true);
  expect(isPreviewable('/uploads/a.HEIC')).toBe(false);
  expect(isPreviewable('/uploads/a.heif')).toBe(false);
});
