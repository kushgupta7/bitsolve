// Click-through check of the real UI (run in CI against `docker compose up`).
// For student, technician and admin it opens every menu page, incident details and the action forms, and reports:
//   * dropdowns with no real option (only a placeholder),
//   * error messages shown on screen,
//   * JavaScript errors and failed API calls (HTTP 4xx/5xx other than expected 401 before login).
// Usage: node ui_crawl.mjs [baseUrl]   (exits 1 when anything is found)
import { chromium } from 'playwright';

const BASE = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '');
const PASSWORD = 'BitsolveDemo2026';
const DOMAIN = '@hyderabad.bits-pilani.ac.in';
const findings = [];
const checked = [];
const note = (role, where, problem) => { findings.push({ role, where, problem }); console.log(`  ✗ [${role}] ${where}: ${problem}`); };
const pause = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function settle(page) {
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  await pause(400);
}

/** Every visible single-choice dropdown must offer at least one real (non-empty) option. */
async function checkSelects(page, role, where) {
  const selects = await page.locator('select:visible').evaluateAll(list => list.filter(s => !s.multiple).map(s => ({
    id: s.id, label: (s.labels?.[0]?.innerText || s.getAttribute('aria-label') || s.id || '').trim(),
    real: [...s.options].filter(o => o.value !== '' && !o.disabled).length,
  })));
  for (const s of selects) if (s.real === 0) note(role, where, `dropdown "${s.label}" (#${s.id}) has no options`);
  return selects.length;
}

async function checkErrors(page, role, where) {
  const texts = await page.locator('.report-error:visible, [role="alert"]:visible').allInnerTexts();
  for (const text of texts.map(t => t.trim()).filter(Boolean)) {
    if (/^Please (complete|give|choose)|is required|must be/i.test(text)) continue; // form validation hints, not failures
    note(role, where, `error shown: "${text.slice(0, 160)}"`);
  }
}

async function check(page, role, where) {
  await settle(page);
  const n = await checkSelects(page, role, where);
  await checkErrors(page, role, where);
  console.log(`  ✓ [${role}] ${where} (${n} dropdown${n === 1 ? '' : 's'})`);
  checked.push(`${role}: ${where} (${n})`);
}

async function step(role, where, action) {
  try { await action(); } catch (error) { note(role, where, `could not complete: ${error.message.split('\n')[0].slice(0, 160)}`); }
}

async function login(browser, local, role) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  // Test hook: a local dry run can install a fake backend (CRAWL_SETUP=path/to/module.mjs).
  if (process.env.CRAWL_SETUP) await (await import(process.env.CRAWL_SETUP)).default(context, local);
  const page = await context.newPage();
  page.on('pageerror', error => note(role, 'browser', `JavaScript error: ${error.message.slice(0, 160)}`));
  page.on('response', response => {
    const url = response.url();
    if (!url.includes('/api/') || response.status() < 400) return;
    if (response.status() === 401 && url.endsWith('/api/auth/me')) return; // normal before signing in
    note(role, 'network', `${response.request().method()} ${url.replace(BASE, '')} -> HTTP ${response.status()}`);
  });
  await page.goto(BASE + '/');
  await page.fill('#email', local + DOMAIN);
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  for (let attempt = 0; attempt < 10 && !(await page.locator('aside nav').count()); attempt++) await pause(1000);
  if (!(await page.locator('aside nav').count())) throw new Error(`login as ${local} failed`);
  await settle(page);
  return page;
}

const navButtons = async page => (await page.locator('aside nav button').allInnerTexts()).map(t => t.trim()).filter(Boolean);
async function go(page, label) { await page.locator('aside nav button', { hasText: label }).first().click(); await settle(page); }

/** Open the first incident in the current list (optionally the first whose row contains `rowText`). */
async function openIncident(page, rowText) {
  const rows = page.locator('table tbody tr', rowText ? { hasText: rowText } : {});
  if (!(await rows.count())) return false;
  await rows.first().getByRole('button', { name: /view/i }).first().click();
  await page.locator('dialog[open]').first().waitFor({ timeout: 5000 });
  await settle(page);
  return true;
}
async function closeDialogs(page) {
  for (let i = 0; i < 3 && await page.locator('dialog[open]').count(); i++) {
    const dialog = page.locator('dialog[open]').last();
    const close = dialog.getByRole('button', { name: /^(Close|Cancel)$/ }).first();
    if (await close.count()) await close.click(); else await page.keyboard.press('Escape');
    await pause(300);
  }
}

async function student(browser) {
  const role = 'student';
  const page = await login(browser, 'student01', role);
  for (const label of await navButtons(page)) await step(role, label, async () => { await go(page, label); await check(page, role, label); });
  await step(role, 'Report Incident (submit)', async () => {
    await go(page, 'Report Incident');
    await page.fill('#incident-title', 'UI check: Wi-Fi down in the library reading room');
    await page.fill('#incident-description', 'Wireless keeps disconnecting in the library reading room on the second floor.');
    for (const [id, wanted] of [['#incident-category', 'NETWORK'], ['#incident-affectedService', 'CAMPUS_WIFI']]) {
      const values = await page.locator(id).evaluate(s => [...s.options].map(o => o.value).filter(Boolean));
      if (!values.length) { note(role, 'Report Incident', `${id} has nothing to choose`); return; }
      await page.selectOption(id, values.includes(wanted) ? wanted : values[0]);
    }
    await page.getByRole('button', { name: /Submit Incident/i }).click();
    await page.getByText(/submitted/i).first().waitFor({ timeout: 8000 });
    console.log('  ✓ [student] reported an incident through the form');
  });
  await step(role, 'incident details', async () => {
    await go(page, 'Dashboard');
    if (await openIncident(page)) { await check(page, role, 'incident details'); await closeDialogs(page); }
    else note(role, 'Dashboard', 'no incidents listed');
  });
  await step(role, 'Profile', async () => { await go(page, 'Profile'); await check(page, role, 'Profile (change password)'); });
  await page.context().close();
}

/** A second student starts a similar report: the open one must be suggested, and "me too" must work. */
async function secondStudent(browser) {
  const role = 'student02';
  const page = await login(browser, 'student02', role);
  await step(role, 'Report Incident (me too)', async () => {
    await go(page, 'Report Incident');
    await page.selectOption('#incident-affectedService', 'CAMPUS_WIFI');
    await page.fill('#incident-title', 'wifi keeps disconnecting in the library');
    const panel = page.locator('.similar-issues');
    await panel.waitFor({ timeout: 6000 }).catch(() => {});
    if (!(await panel.count())) { note(role, 'Report Incident', 'the open Wi-Fi report was not suggested while typing'); return; }
    await check(page, role, 'similar open reports panel');
    await panel.getByRole('button', { name: 'This affects me too' }).first().click();
    await panel.getByText(/notify you when it's fixed/i).first().waitFor({ timeout: 6000 });
    console.log('  ✓ [student02] confirmed "me too" through the form');
    checked.push('student02: me too confirmed');
  });
  await page.context().close();
}

async function technician(browser) {
  const role = 'technician';
  // The technician who owns an open demo incident (all four own some).
  const page = await login(browser, 'technician04', role);
  for (const label of await navButtons(page)) await step(role, label, async () => { await go(page, label); await check(page, role, label); });
  await step(role, 'incident actions', async () => {
    await go(page, 'My Incidents');
    const opened = await openIncident(page, 'In Progress') || await openIncident(page, 'Assigned') || await openIncident(page);
    if (!opened) { note(role, 'My Incidents', 'no incidents listed'); return; }
    await check(page, role, 'incident details');
    const dialog = page.locator('dialog[open]').first();
    const start = dialog.getByRole('button', { name: 'Start Work' });
    if (await start.count()) { await start.click(); await settle(page); await check(page, role, 'after Start Work'); }
    for (const action of ['Request Transfer', 'Request Collaborator', 'Request Additional Information', 'Add Investigation Note', 'Provide Resolution']) {
      if (!(await page.locator('dialog[open]').count())) await openIncident(page, 'In Progress') || await openIncident(page);
      const button = page.locator('dialog[open]').first().getByRole('button', { name: action });
      if (!(await button.count())) continue;
      await button.click(); await settle(page);
      if (action === 'Request Collaborator') {
        const skill = page.locator('select[name="skill"]:visible');
        if (await skill.count()) { await skill.selectOption({ index: 1 }); await settle(page); }
      }
      await check(page, role, `${action} form`);
      const cancel = page.locator('dialog[open]').last().getByRole('button', { name: /^(Cancel|Close)$/ }).first();
      if (await cancel.count()) await cancel.click(); await pause(300);
      // Closing a form must leave the incident window intact (not blank).
      if (await page.locator('dialog[open]').count() && (await page.locator('dialog[open]').first().getByRole('button').count()) < 2) {
        note(role, `${action} form`, 'closing the form left the incident window blank');
      }
    }
    await closeDialogs(page);
  });
  await page.context().close();
}

async function admin(browser) {
  const role = 'admin';
  const page = await login(browser, 'admin01', role);
  for (const label of await navButtons(page)) await step(role, label, async () => { await go(page, label); await check(page, role, label); });
  await step(role, 'classify form', async () => {
    await go(page, 'All Incidents');
    if (await openIncident(page, 'New')) { await check(page, role, 'NEW incident (classify form)'); await closeDialogs(page); }
    else note(role, 'All Incidents', 'no NEW incident to classify (the student report should be there)');
  });
  await step(role, 'reassign form', async () => {
    await go(page, 'All Incidents');
    if (await openIncident(page, 'In Progress')) { await check(page, role, 'in-progress incident (reassign form)'); await closeDialogs(page); }
  });
  await step(role, 'user forms', async () => {
    await go(page, 'Users & Technicians');
    await page.getByRole('button', { name: 'Add User' }).click(); await check(page, role, 'Add User form');
    await page.selectOption('#user-role', 'TECHNICIAN'); await check(page, role, 'Add User form (technician)');
    await page.getByRole('button', { name: /skills and capacity/i }).first().click(); await check(page, role, 'Skills & capacity form');
  });
  await step(role, 'service form', async () => {
    await go(page, 'IT Services');
    await page.locator('table tbody tr').first().getByRole('button', { name: /edit/i }).click(); await check(page, role, 'IT service edit form');
  });
  await step(role, 'SLA form', async () => {
    await go(page, 'SLA Policies');
    await page.locator('table tbody tr').first().getByRole('button', { name: /edit/i }).click(); await check(page, role, 'SLA policy edit form');
  });
  await page.context().close();
}

const browser = await chromium.launch();
for (const [name, run] of [['student', student], ['student02', secondStudent], ['technician', technician], ['admin', admin]]) {
  console.log(`\n== ${name}`);
  try { await run(browser); } catch (error) { note(name, 'session', error.message.split('\n')[0]); }
}
await browser.close();
console.log(`::notice title=UI crawl covered ${checked.length} screens::${checked.join(' | ')}`);
console.log(`\n${findings.length ? findings.length + ' problem(s) found' : 'No problems found.'}`);
for (const f of findings) console.log(`::error title=UI ${f.role}: ${f.where}::${f.problem.replace(/\n/g, ' ')}`);
process.exit(findings.length ? 1 : 0);
