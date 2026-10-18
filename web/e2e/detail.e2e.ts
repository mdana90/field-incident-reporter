import { expect, test, type Page } from '@playwright/test';
import { activeCount, editOnAndroid, fetchIncident, remove, report, unique } from './api';

const status = (page: Page) => page.getByRole('combobox', { name: 'Status' });
const severity = (page: Page) => page.getByRole('combobox', { name: 'Severity' });
const note = (page: Page) => page.getByRole('textbox', { name: /Resolution note/ });
const save = (page: Page) => page.getByRole('button', { name: 'Save changes' });

async function openFromQueue(page: Page, title: string) {
  await page.getByRole('searchbox', { name: 'Search title or location' }).fill(title);
  await page.getByRole('row', { name: new RegExp(title) }).getByRole('link', { name: 'Review →' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

test('triage saves severity, status, and note', async ({ page }) => {
  const incident = await report({ title: unique('Triage'), photos: 2 });
  await page.goto('/incidents');
  await openFromQueue(page, incident.title);

  await expect(page.getByText('1 / 2 photos')).toBeVisible();
  await page.getByRole('button', { name: 'Show photo 2' }).click();
  await expect(page.getByText('2 / 2 photos')).toBeVisible();

  await severity(page).selectOption('CRITICAL');
  await status(page).selectOption('IN_PROGRESS');
  await note(page).fill('Electrician booked');
  await save(page).click();
  await expect(page.getByText('Changes saved.')).toBeVisible();
  await expect(page.getByText('Critical severity')).toBeVisible();
  expect(await fetchIncident(incident.id)).toMatchObject({ severity: 'CRITICAL', status: 'IN_PROGRESS', resolutionNote: 'Electrician booked' });

  await note(page).fill('');
  await save(page).click();
  await expect(page.getByText('Changes saved.')).toBeVisible();
  await page.reload();
  await expect(note(page)).toHaveValue('');
});

test('sidebar count is fresh after going back to the queue', async ({ page }) => {
  const incident = await report({ title: unique('Count') });
  const other = await report({ title: unique('Deleted elsewhere') });
  await page.goto(`/incidents/${incident.id}`);
  await remove(other.id);
  await page.getByRole('link', { name: '← Back to queue' }).click();
  await expect(page.getByRole('link', { name: /Incident queue/ }).filter({ visible: true }).locator('span').last()).toHaveText(String(await activeCount()));
});

test('form shows a technician edit made after the operator saved', async ({ page }) => {
  const incident = await report({ title: unique('Stale form'), severity: 'HIGH' });
  await page.goto('/incidents');
  await openFromQueue(page, incident.title);
  await status(page).selectOption('IN_PROGRESS');
  await save(page).click();
  await expect(page.getByText('Changes saved.')).toBeVisible();

  await page.getByRole('link', { name: '← Back to queue' }).click();
  await editOnAndroid(incident, { severity: 'CRITICAL' });
  // Wait for the queue's auto-refresh instead of reloading, so the preserved detail page is reused.
  await expect(page.getByRole('row', { name: new RegExp(incident.title) })).toContainText('Critical', { timeout: 20_000 });
  await page.getByRole('row', { name: new RegExp(incident.title) }).getByRole('link', { name: 'Review →' }).click();

  await expect(page.getByText('Critical severity')).toBeVisible();
  await expect(severity(page)).toHaveValue('CRITICAL');
  await expect(page.getByText('Changes saved.')).toBeHidden();
});

test('saving an incident deleted elsewhere explains what happened', async ({ page }) => {
  const incident = await report({ title: unique('Deleted while open') });
  await page.goto(`/incidents/${incident.id}`);
  await remove(incident.id);
  await status(page).selectOption('RESOLVED');
  await save(page).click();
  await expect(page.getByText('This incident no longer exists.')).toBeVisible();
  await expect(status(page)).toHaveValue('RESOLVED');

  await page.reload();
  await expect(page.getByText('Incident not found. It may have been deleted.')).toBeVisible();
});

test('an oversized note gets an actionable message and keeps the text', async ({ page }) => {
  const incident = await report({ title: unique('Long note') });
  await page.goto(`/incidents/${incident.id}`);
  await note(page).fill('x'.repeat(17_000));
  await save(page).click();
  await expect(page.getByText('Resolution note is too long. Shorten it and try again.')).toBeVisible();
  await expect(note(page)).toHaveValue('x'.repeat(17_000));
});

test('unknown incident shows not found', async ({ page }) => {
  await page.goto('/incidents/does-not-exist');
  await expect(page.getByText('Incident not found. It may have been deleted.')).toBeVisible();
});

test('HEIC photos fall back to an Open file link', async ({ page }) => {
  const incident = await report({ title: unique('HEIC'), heic: true });
  await page.goto(`/incidents/${incident.id}`);
  await expect(page.getByText('Preview not available')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Open file' })).toHaveAttribute('href', /\.heic$/);
});

test('the form is locked while a save is in flight', async ({ page }) => {
  const incident = await report({ title: unique('Pending save') });
  await page.goto(`/incidents/${incident.id}`);
  // Hold the Server Action request so the save stays pending.
  await page.route(`**/incidents/${incident.id}`, async (route) => {
    if (route.request().method() === 'POST') await new Promise((resolve) => setTimeout(resolve, 1500));
    await route.continue();
  });
  await status(page).selectOption('IN_PROGRESS');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('button', { name: 'Saving…' })).toBeVisible();
  await expect(note(page)).toBeDisabled();
  await expect(severity(page)).toBeDisabled();
  await expect(page.getByText('Changes saved.')).toBeVisible();
  await expect(status(page)).toHaveValue('IN_PROGRESS');
  await expect(note(page)).toBeEnabled();
});

test('gallery stays consistent when photos are removed elsewhere', async ({ page }) => {
  const incident = await report({ title: unique('Shrinking gallery'), photos: 3 });
  await page.goto(`/incidents/${incident.id}`);
  await page.getByRole('button', { name: 'Show photo 3' }).click();
  await expect(page.getByText('3 / 3 photos')).toBeVisible();
  await editOnAndroid(incident, { keepPhotos: 1 });
  await save(page).click(); // the save re-renders the page with the new photo list
  await expect(page.getByText('Changes saved.')).toBeVisible();
  await expect(page.getByText('1 / 1 photo')).toBeVisible();
  await expect(page.getByRole('img', { name: 'Incident photo 1' })).toBeVisible();
});
