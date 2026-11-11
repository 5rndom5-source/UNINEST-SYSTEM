const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const base=process.env.UNINEST_TEST_BASE;
assert.match(base||'',/^http:\/\/127\.0\.0\.1:\d+\/uninest$/);
let checks=0;
function check(ok,label){assert.ok(ok,label);checks++;}
async function token(context){return (await (await context.request.get(base+'/CsrfTokenServlet')).json()).token;}
async function login(context,role,email){
  const response=await context.request.post(base+'/AccountLoginServlet',{form:{role,email,password:'Testing!2026',_csrf:await token(context)},maxRedirects:0});
  check(response.status()===302,'login redirect');
  return response.headers().location;
}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const anon=await browser.newContext();
  const registrationPage=await anon.newPage();
  for(const width of [390,1280]) {
    await registrationPage.setViewportSize({width,height:900});
    await registrationPage.goto(base+'/createAccount/index.html');
    await registrationPage.locator('[name=fullname]').fill('Browser registration '+width);
    await registrationPage.locator('[name=email]').fill(`browser-${width}@example.invalid`);
    await registrationPage.locator('[name=password]').fill('Testing!2026');
    await registrationPage.locator('[name=confirmPassword]').fill('Testing!2026');
    await registrationPage.locator('[name=role]').selectOption('landlord');
    await Promise.all([registrationPage.waitForURL('**/createAccount/login.html?success=*'),registrationPage.getByRole('button',{name:'Register',exact:true}).click()]);
    check((await registrationPage.locator('#formMessage').innerText()).includes('Awaiting Admin approval'),'registration shows pending approval');
    check(await registrationPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'registration message fits viewport');
  }
  const legacy=await anon.request.post(base+'/RegisterServlet',{multipart:{fullName:'HTTP document upload',email:'http-document@example.invalid',phone:'0712345678',businessName:'Fixture',businessAddress:'Fixture address',idNumber:'HTTP-FIXTURE',password:'Testing!2026',confirmPassword:'Testing!2026',_csrf:await token(anon),idFile:{name:'identity.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4\nHTTP fixture only')}},maxRedirects:0});
  check(legacy.status()===302 && decodeURIComponent(legacy.headers().location).includes('Awaiting+Admin+approval'),'multipart registration succeeds pending approval');
  check((await anon.request.get(base+'/LandlordDocumentServlet?id=1')).status()===401,'anonymous document denied');
  check((await anon.request.get(base+'/uploads/legacy.pdf')).status()===404,'public legacy uploads blocked');
  check((await anon.request.post(base+'/AccountRegisterServlet',{form:{role:'landlord'}})).status()===403,'registration requires CSRF');
  const student=await browser.newContext();await login(student,'student','student@example.invalid');
  check((await student.request.get(base+'/LandlordDocumentServlet?id=1')).status()===403,'student document denied');
  check((await student.request.get(base+'/LandlordApprovalServlet')).status()===403,'student queue denied');
  const pending=await browser.newContext();
  check(decodeURIComponent(await login(pending,'landlord','landlord@example.invalid')).includes('approved'),'pending login blocked');
  const admin=await browser.newContext();await login(admin,'admin','admin@example.invalid');
  const page=await admin.newPage();
  for(const width of [390,1280]) {
    await page.setViewportSize({width,height:900});
    await page.goto(base+'/LandlordApprovalServlet');
    check(await page.getByRole('heading',{name:'Landlord account review',exact:true}).isVisible(),'review page visible');
    check(await page.getByText('No available supporting documents.',{exact:false}).count()>0,'missing evidence warning');
    check(await page.getByRole('link',{name:'Download privately'}).count()>0,'private document link');
    check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no horizontal overflow');
    await page.screenshot({path:path.resolve(__dirname,`../test-runtime/landlord-review-${width}.png`),fullPage:true});
  }
  const href=await page.getByRole('link',{name:'Download privately'}).first().getAttribute('href');
  const download=await admin.request.get(new URL(href,base).href);
  check(download.status()===200,'active admin document download');
  check(download.headers()['content-disposition'].startsWith('attachment;'),'document forced download');
  check(download.headers()['cache-control'].includes('no-store'),'document not cached');
  check((await download.body()).toString().startsWith('%PDF-'),'correct private fixture bytes');
  check((await admin.request.get(base+'/LandlordDocumentServlet?id=999999')).status()===404,'missing document denied');
  check((await admin.request.get(base+'/LandlordDocumentServlet?id=../secret')).status()===400,'invalid document id denied');
  const decision={userId:'10',decision:'approve',reason:'Synthetic independent verification test only'};
  check((await admin.request.post(base+'/LandlordApprovalServlet',{form:decision})).status()===403,'approval missing CSRF denied');
  check((await admin.request.post(base+'/LandlordApprovalServlet',{form:{...decision,_csrf:await token(admin)}})).status()===400,'approval missing independent verification denied');
  await page.goto(base+'/LandlordApprovalServlet');
  const article=page.locator('article').filter({has:page.getByRole('heading',{name:'Landlord',exact:true})});
  await article.locator('[name=independentlyVerified]').check();
  await article.locator('[name=reason]').fill(decision.reason);
  await Promise.all([page.waitForURL('**/LandlordApprovalServlet?result=approved'),article.getByRole('button',{name:'Approve landlord'}).click()]);
  check(await page.getByRole('status').isVisible(),'browser approval succeeded');
  check((await page.locator('table').innerText()).includes('Independent identity and letting-authority checks confirmed'),'decision history records confirmation');
  check((await login(pending,'landlord','landlord@example.invalid')).includes('/Landlord/'),'approved landlord signs in');
  check((await pending.request.get(new URL(href,base).href)).status()===403,'landlord cannot download identity evidence');
  const stale=await admin.request.post(base+'/LandlordApprovalServlet',{form:{...decision,decision:'reject',_csrf:await token(admin)},maxRedirects:0});
  check(stale.headers().location.endsWith('result=changed'),'stale rejection blocked');
  const rejectArticle=page.locator('article').filter({has:page.getByRole('heading',{name:'shared-success',exact:true})});
  await rejectArticle.locator('[name=reason]').fill('Unable to verify fixture');
  await Promise.all([page.waitForURL('**/LandlordApprovalServlet?result=rejected'),rejectArticle.getByRole('button',{name:'Reject landlord'}).click()]);
  check(await page.getByRole('status').isVisible(),'rejection does not require approval checkbox');
  console.log(`PASS: ${checks} real HTTP/MySQL browser checks at 390px and 1280px; no mocked APIs`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
