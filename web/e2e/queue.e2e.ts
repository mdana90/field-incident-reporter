import { expect, test } from '@playwright/test';
import { report, unique } from './api';

const search = (page: import('@playwright/test').Page) => page.getByRole('searchbox', { name: 'Search title or location' });

test('a new report appears on the queue without reloading', async ({ page }) => {
  await page.goto('/incidents');
  await expect(page.getByRole('heading', { name: 'Incident queue' })).toBeVisible();
  const title = unique('Auto refresh');
  await report({ title });
  // Picked up by the 15 s auto-refresh, not by navigation.
  await expect(page.getByRole('row', { name: new RegExp(title) })).toBeVisible({ timeout: 20_000 });
});

test('search filters the table and stays in sync with the URL', async ({ page }) => {
  const wiring = unique('Wiring');
  await report({ title: wiring, severity: 'CRITICAL' });
  await report({ title: unique('Pipe') });

  await page.goto('/incidents');
  await search(page).fill(wiring);
  await expect(page).toHaveURL(new RegExp(`q=${encodeURIComponent(wiring).replace(/%20/g, '\\+')}`));
  await expect(page.locator('tbody tr')).toHaveCount(1);

  await search(page).fill('no such incident');
  await page.getByRole('link', { name: 'Clear filters' }).click();
  await expect(page).toHaveURL(/\/incidents$/);
  await expect(search(page)).toHaveValue('');
});

test('changing a filter while a search is pending keeps both', async ({ page }) => {
  await page.goto('/incidents');
  await search(page).fill('road');
  await page.getByRole('combobox', { name: 'Status' }).selectOption('ALL');
  await expect(page).toHaveURL(/q=road/);
  await expect(page).toHaveURL(/status=ALL/);
});

test('Critical only is driven by the severity param', async ({ page }) => {
  const critical = unique('Critical only');
  await report({ title: critical, severity: 'CRITICAL' });
  await report({ title: unique('Not critical'), severity: 'LOW' });

  await page.goto('/incidents');
  await page.getByRole('button', { name: 'Critical only' }).click();
  await expect(page).toHaveURL(/severity=CRITICAL/);
  await expect(page.getByRole('button', { name: 'Critical only' })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('combobox', { name: 'Severity' })).toHaveValue('CRITICAL');
  for (const row of await page.locator('tbody tr').all()) await expect(row).toContainText('Critical');
});

test('invalid URL params fall back to the default view', async ({ page }) => {
  await report({ title: unique('Garbage params') });
  await page.goto('/incidents?status=foo&severity=bar&sort=x');
  await expect(page.getByRole('combobox', { name: 'Status' })).toHaveValue('ACTIVE');
  await expect(page.getByRole('combobox', { name: 'Severity' })).toHaveValue('ALL');
  await expect(page.locator('tbody tr').first()).toBeVisible();
});

test('quick successive filter changes keep every selection', async ({ page }) => {
  await page.goto('/incidents');
  // Slow down the server render for each navigation, as on a slow network.
  await page.route('**/incidents?*', async (route) => {
    if (route.request().headers()['rsc']) await new Promise((resolve) => setTimeout(resolve, 800));
    await route.continue();
  });
  await page.getByRole('combobox', { name: 'Status' }).selectOption('ALL');
  await page.getByRole('combobox', { name: 'Severity' }).selectOption('HIGH');
  await page.getByRole('combobox', { name: 'Sort' }).selectOption('newest');
  await expect(page).toHaveURL(/sort=newest/);
  await expect(page).toHaveURL(/status=ALL/);
  await expect(page).toHaveURL(/severity=HIGH/);
});

test('Critical only stays readable when pressed', async ({ page }) => {
  await page.goto('/incidents');
  const button = page.getByRole('button', { name: 'Critical only' });
  await button.click();
  await expect(button).toHaveAttribute('aria-pressed', 'true');
  await expect(button).toHaveCSS('background-color', 'rgb(168, 54, 46)');
  await expect(button).toHaveCSS('color', 'rgb(255, 255, 255)');
  await button.click();
  await expect(button).toHaveAttribute('aria-pressed', 'false');
  await expect(button).toHaveCSS('background-color', 'rgb(255, 245, 242)');
  await expect(button).toHaveCSS('color', 'rgb(157, 53, 43)');
});
