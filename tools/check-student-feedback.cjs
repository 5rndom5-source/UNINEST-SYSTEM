const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    for (const width of [390, 1280]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      const failures = [];
      page.on('pageerror', error => failures.push(error.message));
      await page.route('https://feedback.test/**', async route => {
        const url = new URL(route.request().url());
        if (url.pathname.endsWith('/StudentFavoriteServlet')) {
          return route.fulfill({ json: { listings: [{ id: '1', title: 'Saved occupied room', availability: 'occupied', tags: [], price: 'R1000', image: '' }] } });
        }
        if (url.pathname.endsWith('/StudentReviewServlet')) {
          assert.equal(route.request().method(), 'POST');
          assert.equal(new URLSearchParams(route.request().postData()).get('rating'), '5');
          return route.fulfill({ json: { status: 'success', message: 'Review saved', ratingAverage: '5.0', ratingCount: '1' } });
        }
        if (url.pathname.endsWith('Servlet')) return route.fulfill({ json: {} });
        return route.fulfill({ contentType: 'text/html', body: '<body data-page="saved"><main id="savedListings"></main><div id="toast"></div></body>' });
      });
      await page.goto('https://feedback.test/UserDashboard/saved.html');
      await page.addScriptTag({ content: fs.readFileSync(path.join(__dirname, '../UserDashboard/script.js'), 'utf8') });
      await page.locator('.listing-card').waitFor();
      assert.equal(await page.getByText('Currently unavailable', { exact: true }).count(), 1);
      assert.equal(await page.getByRole('button', { name: 'Book', exact: true }).isDisabled(), true);
      assert.equal(await page.getByRole('button', { name: 'Request Viewing', exact: true }).isDisabled(), true);
      assert.equal(await page.getByRole('button', { name: 'Remove', exact: true }).isEnabled(), true);
      await page.locator('input[type="radio"][value="5"]').check();
      await page.getByRole('button', { name: 'Submit Review' }).click();
      await page.locator('form [role="status"]').waitFor();
      assert.equal(await page.locator('form [role="status"]').innerText(), 'Review saved');
      assert.match(await page.locator('.listing-rating').innerText(), /5\.0 \(1\)/);
      assert.equal(page.url(), 'https://feedback.test/UserDashboard/saved.html');
      assert.deepEqual(failures, []);
      await page.close();
    }
    console.log('PASS: saved unavailable listing controls and inline review result at mobile and desktop sizes (mocked API)');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
