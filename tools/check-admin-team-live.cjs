const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const base='http://localhost:8093/uninest'; // Disposable database only.
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  const owner=await browser.newContext();
  async function post(context,url,form){
   const token=(await (await context.request.get(base+'/CsrfTokenServlet')).json()).token;
   return context.request.post(base+url,{form,headers:{'X-CSRF-Token':token}});
  }
  await post(owner,'/AccountLoginServlet',{email:'owner@example.invalid',password:'Testing!2026',role:'admin'});
  const member=await browser.newContext(); const page=await member.newPage();
  const email='member-'+Date.now()+'@example.invalid';
  await page.goto(base+'/Admin/web/jsp/Register.html');
  await page.locator('[name=fullName]').fill('Team Member');await page.locator('[name=email]').fill(email);
  await page.locator('[name=reason]').fill('Isolated team registration test');
  await page.locator('[name=password]').fill('Testing!2026');await page.locator('[name=confirmPassword]').fill('Testing!2026');
  await Promise.all([page.waitForURL('**/Register.html?success=*'),page.getByRole('button',{name:'Submit request'}).click()]);
  await post(member,'/AccountLoginServlet',{email,password:'Testing!2026',role:'admin'});
  assert.notEqual((await member.request.get(base+'/CurrentUserServlet')).status(),200,'pending account must not sign in');
  assert.equal((await member.request.get(base+'/AdminAccessServlet')).status(),401);
  const review=await owner.newPage();await review.goto(base+'/AdminAccessServlet');
  const item=review.locator('article').filter({hasText:email});await item.waitFor();
  await Promise.all([review.waitForURL('**/AdminAccessServlet?result=saved'),item.getByRole('button',{name:'Approve',exact:true}).click()]);
  await post(member,'/AccountLoginServlet',{email,password:'Testing!2026',role:'admin'});
  assert.equal((await member.request.get(base+'/CurrentUserServlet')).status(),200);
  await page.goto(base+'/Admin/web/jsp/Profile.html');
  await page.locator('[data-admin-access]').waitFor();
  await page.locator('#adminProfilePhoto').setInputFiles(path.resolve(__dirname,'../HomePage/listing-rondebosch.png'));
  const upload=page.waitForResponse(r=>r.url().includes('/ProfileImageServlet')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Upload Picture',exact:true}).click();
  assert.equal((await upload).status(),200);
  await page.waitForFunction(()=>document.querySelector('.avatar img[data-profile-image]')?.naturalWidth>0);
  const response=await member.request.get(base+'/ProfileImageServlet');
  assert.equal(response.headers()['content-type'],'image/png');
  const before=await response.body();
  await page.reload();await page.waitForFunction(()=>document.querySelector('.avatar img[data-profile-image]')?.naturalWidth>0);
  await page.locator('.admin-avatar img[data-profile-image]').waitFor({state:'visible'});
  assert.ok(await page.locator('.admin-avatar img').evaluate(img=>img.naturalWidth>0&&img.getBoundingClientRect().width>0),'header avatar must render, not merely load');
  assert.deepEqual(await (await member.request.get(base+'/ProfileImageServlet')).body(),before);
  assert.equal((await owner.request.get(base+'/ProfileImageServlet')).status(),404,'another account cannot see member avatar');
  await page.locator('#adminProfilePhoto').setInputFiles({name:'fake.txt',mimeType:'text/plain',buffer:Buffer.from('not an image')});
  await page.getByRole('button',{name:'Upload Picture',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('[data-profile-image-message]').textContent.includes('JPG or PNG'));
  assert.deepEqual(await (await member.request.get(base+'/ProfileImageServlet')).body(),before,'invalid file preserves saved avatar');
  for(const width of [1280,390]){
   await page.setViewportSize({width,height:900});
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await page.screenshot({path:`test-runtime/admin-profile-${width}.png`,fullPage:true});
  }
  console.log('PASS: separate-session admin registration, pending login denial, approval, login, navigation, real photo upload, MIME, reload persistence, isolation, invalid file and responsive layout');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
