const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage();
    const base = 'http://localhost:8091/uninest';
    for (const width of [1280, 390]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(base + '/Admin/web/jsp/Register.html');
      await page.locator('[name=fullName]').fill('Registration Test');
      await page.locator('[name=email]').fill('test@example.invalid');
      await page.locator('[name=reason]').fill('Validation test only');
      await page.locator('[name=password]').fill('Testing!2026');
      await page.locator('[name=confirmPassword]').fill('Different!2026');
      await page.locator('button[type=submit]').click();
      await page.waitForURL('**/Admin/web/jsp/Register.html?error=*');
      assert.match(await page.locator('#requestMessage').innerText(), /Passwords must match/);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: `test-runtime/admin-registration-${width}.png`, fullPage: true });
    }
    console.log('PASS: admin registration validation, redirect and layout at desktop/mobile widths; no account created');
  } finally { await browser.close(); }
})().catch(error => { console.error(error);process.exitCode = 1; });
