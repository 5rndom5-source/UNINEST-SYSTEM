const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const base = 'http://localhost:8092/uninest';
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    async function login(role, email, password) {
      const context = await browser.newContext();
      const token = (await (await context.request.get(base + '/CsrfTokenServlet')).json()).token;
      await context.request.post(base + '/AccountLoginServlet', { form: { email, password, role }, headers: { 'X-CSRF-Token': token } });
      const page = await context.newPage();
      await page.goto(base + '/PaymentSimulationServlet');
      assert.equal(await page.locator('h1').innerText(), 'Demo payments');
      return page;
    }
    const landlord = await login('landlord', 'landlord@example.invalid', 'Testing!2026');
    const student = await login('student', 'student@example.invalid', 'Changed!2026');
    async function submit(page, booking, action, fields) {
      const form = page.locator(`form:has(input[name="bookingId"][value="${booking}"]):has(input[name="action"][value="${action}"])`);
      for (const [key, value] of Object.entries(fields)) {
        const field = form.locator(`[name="${key}"]`);
        if (key === 'consent') await field.check();
        else if (await field.evaluate(el => el.tagName === 'SELECT')) await field.selectOption(value);
        else await field.fill(value);
      }
      await Promise.all([page.waitForNavigation(), form.locator('button').click()]);
      assert.equal(await page.locator('[role="alert"]').count(), 0, await page.locator('body').innerText());
    }
    await submit(landlord, 7, 'configure', { deposit: '500' });
    await submit(landlord, 8, 'configure', { deposit: '500' });
    await student.reload();
    await submit(student, 7, 'route', { source: 'self' });
    await submit(student, 7, 'accept', { consent: 'yes' });
    await submit(student, 7, 'pay', { outcome: 'failed' });
    assert.match(await student.locator('section').filter({ hasText: 'Booking #7' }).innerText(), /failed/);
    await submit(student, 7, 'pay', { outcome: 'completed' });
    assert.match(await student.locator('section').filter({ hasText: 'Booking #7' }).innerText(), /DEMO-V2-7-STUDENT \| R1500.00/);
    await submit(student, 8, 'route', { source: 'nsfas' });
    assert.equal(await student.locator('form:has(input[name="bookingId"][value="8"]):has(input[name="action"][value="pay"])').count(), 0);
    await landlord.reload();
    await submit(landlord, 8, 'verify', { decision: 'verified', coveredDeposit: '0', coveredRent: '1000', reference: 'DEMO-AWARD-8' });
    await student.reload();
    await submit(student, 8, 'accept', { consent: 'yes' });
    await submit(student, 8, 'pay', { outcome: 'completed' });
    await landlord.reload();
    await submit(landlord, 8, 'external', { reference: 'DEMO-RECEIPT-8' });
    await student.reload();
    const funded = await student.locator('section').filter({ hasText: 'Booking #8' }).innerText();
    assert.match(funded, /DEMO-V2-8-STUDENT \| R500.00/);
    assert.match(funded, /DEMO-V2-8-PROVIDER \| R1000.00/);
    for (const width of [1280, 390]) {
      await student.setViewportSize({ width, height: 900 });
      assert.ok(await student.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await student.screenshot({ path: `test-runtime/funding-${width}.png`, fullPage: true });
    }
    console.log('PASS: two-account self-funded decline/retry and NSFAS split-payment browser journeys; desktop/mobile layout');
  } finally { await browser.close(); }
})().catch(error => { console.error(error);process.exitCode = 1; });
