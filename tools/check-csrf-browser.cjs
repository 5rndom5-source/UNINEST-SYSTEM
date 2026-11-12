const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const base = process.env.UNINEST_TEST_URL || 'http://localhost:8091/uninest';
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  let checks = 0;
  const check = (value, label) => { assert.ok(value, label); checks++; };
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base + '/createAccount/login.html');
    check(await page.evaluate(() => !!window.uninestCsrf), 'token helper loaded');
    const token = (await (await context.request.get(base + '/CsrfTokenServlet')).json()).token;
    check((await context.request.post(base + '/LogoutServlet')).status() === 403, 'missing token rejected');
    const other = await browser.newContext();
    check((await other.request.post(base + '/LogoutServlet', { headers: { 'X-CSRF-Token': token } })).status() === 403, 'other session rejected');
    await other.close();
    check((await context.request.post(base + '/LogoutServlet?_csrf=' + token)).status() === 403, 'URL token rejected');
    check((await context.request.get(base + '/LogoutServlet')).status() === 200, 'GET confirmation available');
    check((await (await context.request.get(base + '/CsrfTokenServlet')).json()).token === token, 'GET logout preserves session');
    const multipart = await context.request.post(base + '/ProfileImageServlet', { multipart: { _csrf: token } });
    check(multipart.status() === 401, 'multipart token reaches authentication');
    const fetchStatus = await page.evaluate(async base => {
      const body = new FormData();
      const response = await fetch(base + '/ProfileImageServlet', { method: 'POST', body });
      return response.status;
    }, base);
    check(fetchStatus === 401, 'browser fetch adds token');
    // A synthetic native form avoids account changes while exercising the capture handler.
    await page.evaluate(base => {
      const form = document.createElement('form');
      form.method = 'post';form.action = base + '/ProfileImageServlet';form.enctype = 'multipart/form-data';
      form.id = 'csrf-test-form';form.innerHTML = '<button type="submit">Test</button>';
      document.body.appendChild(form);
    }, base);
    const responsePromise = page.waitForResponse(r => r.url().endsWith('/ProfileImageServlet') && r.request().method() === 'POST');
    await page.locator('#csrf-test-form button').click();
    check((await responsePromise).status() === 401, 'native multipart form adds token');
    await page.waitForURL('**/ProfileImageServlet');
    await page.goto(base + '/createAccount/login.html');
    await page.evaluate(base => {
      const link = document.createElement('a');link.href = base + '/LogoutServlet';link.id = 'logout-test';link.textContent = 'Logout';document.body.appendChild(link);
    }, base);
    await page.locator('#logout-test').click();
    await page.waitForURL('**/createAccount/login.html?signedOut=1');
    check(await page.locator('select[name="role"] option').count() >= 3, 'logout returns role chooser');
    check((await context.request.post(base + '/LogoutServlet', { headers: { 'X-CSRF-Token': token } })).status() === 403, 'old token rejected after logout');
    check(errors.length === 0, 'no browser script errors: ' + errors.join('; '));
    await context.close();
    console.log('PASS: ' + checks + ' live browser and CSRF checks');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
