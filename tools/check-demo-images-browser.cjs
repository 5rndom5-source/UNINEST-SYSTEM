const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const base=process.env.UNINEST_TEST_BASE;
const mode=process.env.UNINEST_DEMO_TEST_MODE;
assert.match(base||'',/^http:\/\/127\.0\.0\.1:\d+\/uninest$/);
let checks=0;
function check(ok,label){assert.ok(ok,label);checks++;}
async function token(c){return (await (await c.request.get(base+'/CsrfTokenServlet')).json()).token;}
async function login(c,email,role){const r=await c.request.post(base+'/AccountLoginServlet',{form:{email:email+'@example.invalid',role,password:'Testing!2026',_csrf:await token(c)},maxRedirects:0});check(r.status()===302&&!r.headers().location.includes('error='),'fixture login');}
async function photo(c,name){return c.request.get(base+'/PropertyImageServlet/'+name);}
async function gallery(c,id=1){const r=await c.request.get(base+'/PropertyGalleryServlet?roomId='+id);check(r.status()===200,'authorized gallery');return (await r.json()).listing;}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  const anon=await browser.newContext();
  if(mode==='database-failure') {
   const r=await photo(anon,'missing.png');check(r.status()===503&&!r.headers()['x-uninest-demo-photo'],'database failure never becomes demo image');
   console.log(`PASS: ${checks} database-failure image check (real unavailable local database)`);return;
  }
  const original=await photo(anon,'existing.png');
  check(original.status()===200&&!original.headers()['x-uninest-demo-photo'],'existing image served normally');
  check((await original.body()).equals(fs.readFileSync(path.join(process.env.UNINEST_UPLOAD_DIR,'property-images/existing.png'))),'original image bytes unchanged');
  for(const name of ['orphan.png','deleted.png','private.png'])check((await photo(anon,name)).status()===404,'orphan/deleted/private photo denied');
  for(const name of ['nested/missing.png','%2e%2e%5cmissing.png'])check((await photo(anon,name)).status()===400,'invalid path never falls back');
  const missing=await photo(anon,'missing.png');
  const data=await gallery(anon);const photos=data.rooms.flatMap(r=>r.photos);
  check(photos.find(p=>p.id===1).isDemo===false,'existing image not labelled demo');
  if(mode==='disabled') {
   check(missing.status()===404&&!missing.headers()['x-uninest-demo-photo'],'disabled missing image remains 404');
   check(photos.every(p=>p.isDemo===false),'disabled gallery has no demo flags');
   console.log(`PASS: ${checks} fallback-disabled real HTTP/MySQL checks`);return;
  }
  check(missing.status()===200&&missing.headers()['x-uninest-demo-photo']==='true','enabled authorized missing image uses demo');
  check(missing.headers()['content-type']==='image/jpeg'&&missing.headers()['cache-control']==='private, no-store','fallback content type and private cache policy');
  check((await missing.body()).equals(await (await photo(anon,'missing.png')).body()),'deterministic fallback bytes');
  check(photos.filter(p=>p.isDemo).length===4,'only missing gallery images labelled');
  const owner=await browser.newContext();await login(owner,'landlord','landlord');
  const other=await browser.newContext();await login(other,'other','landlord');
  const student=await browser.newContext();await login(student,'student','student');
  const admin=await browser.newContext();await login(admin,'admin','admin');
  check((await photo(owner,'private.png')).status()===200,'owner allowed private fallback');
  check((await photo(admin,'private.png')).status()===200,'admin allowed existing private access');
  check((await photo(other,'private.png')).status()===404,'other landlord denied private fallback');
  check((await photo(student,'private.png')).status()===404,'student denied private fallback');
  const label='DEMO PHOTO \u2014 NOT ACTUAL PROPERTY';
  for(const width of [390,1280]) {
   const page=await student.newPage();await page.setViewportSize({width,height:900});
   await page.goto(base+'/UserDashboard/listings.html');
   await page.waitForFunction(()=>typeof window.openPropertyGallery==='function');
   await page.evaluate(()=>window.openPropertyGallery(1));
   await page.locator('.hero-grid .demo-photo-label').first().waitFor();
   check(await page.locator('.hero-grid .demo-photo-label').first().textContent()===label,'student hero demo disclosure');
   await page.getByRole('button',{name:'Open photo 1',exact:true}).click();
   await page.locator('.photo-stage > img').evaluate(img=>img.decode());
   check(await page.locator('.demo-photo-warning').isVisible(),'fullscreen warning visible');
   check(await page.locator('.demo-photo-warning').textContent()===label,'fullscreen exact disclosure');
   check(await page.locator('.photo-stage > img').getAttribute('alt')===label,'accessible demo label');
   check(await page.evaluate(()=>{const image=document.querySelector('.photo-stage').getBoundingClientRect(),label=document.querySelector('.demo-photo-warning').getBoundingClientRect();return image.height>150&&image.top>=label.bottom&&image.right<=innerWidth;}),'fullscreen label and image fit without overlap');
   await page.screenshot({path:`test-runtime/demo-fullscreen-${width}.png`});
   await page.keyboard.press('ArrowRight');
   check(await page.locator('.demo-photo-warning').isHidden(),'real photo does not inherit demo label');
   await page.keyboard.press('ArrowLeft');check(await page.locator('.demo-photo-warning').isVisible(),'demo label restored on navigation');
   await page.getByRole('button',{name:'Close fullscreen viewer'}).click();
   await page.getByRole('button',{name:'View all 5 photos'}).click();
   check(await page.locator('.tour-photo-grid .demo-photo-label').count()===4,'room tour labels all substitutes');
   await page.screenshot({path:`test-runtime/demo-tour-${width}.png`,fullPage:true});await page.close();
  }
  // Keep existing image references while editing room groups/order/captions, including missing files.
  const fields={editorVersion:'2',roomId:'1',propertyTitle:'Demo fixture',description:'Edited in isolated test',location:'12 Test Street',price:'1000',groupCount:'1',photoCount:'5',coverIndex:'2',groupId0:crypto.randomUUID(),groupType0:'Kitchen',groupLabel0:'Kitchen revised',deposit:'0',bedrooms:'1',bathrooms:'1',campusDistance:'',_csrf:await token(owner)};
  photos.slice().reverse().forEach((p,i)=>Object.assign(fields,{['photoRef'+i]:String(p.id),['photoGroup'+i]:'0',['photoCaption'+i]:'Caption '+i}));
  const edited=await owner.request.post(base+'/LandlordAddPropertyServlet',{form:fields});check(edited.status()===200&&(await edited.json()).saved,'existing listing edit with missing images succeeds');
  const after=await gallery(owner);assert.deepEqual(after.rooms[0].photos.map(p=>p.id),photos.map(p=>p.id).reverse());checks++;
  check(after.rooms[0].label==='Kitchen revised'&&after.rooms[0].photos[2].isCover,'grouping/ordering/cover retained');
  check((await photo(anon,'missing.png')).status()===404,'listing returned to pending remains private after edit');
  const page=await owner.newPage();await page.goto(base+'/Landlord/listings.html');
  await page.locator('#propertyTitle').fill('Fresh upload');await page.locator('#propertyDescription').fill('Isolated upload check');await page.locator('#propertyLocation').fill('20 Upload Street');await page.locator('#propertyPrice').fill('1500');
  await page.getByRole('button',{name:'Add Bedroom',exact:true}).click();
  await page.locator('.photo-room input[type=file]').first().setInputFiles(Array(5).fill(path.resolve(__dirname,'../HomePage/listing-rondebosch.png')));
  await page.waitForFunction(()=>document.querySelector('.photo-count').textContent.startsWith('5 photos'),{},{timeout:120000});
  await page.getByRole('button',{name:'Set as listing cover'}).first().click();
  await Promise.all([page.waitForURL('**/Landlord/listings.html?status=pending'),page.getByRole('button',{name:'Save listing',exact:true}).click()]);
  const fresh=await gallery(owner,3);check(fresh.rooms[0].photos.length===5,'real landlord browser uploads saved');
  for(const p of fresh.rooms[0].photos){const r=await owner.request.get(base+p.url.replace(/^\/uninest/,''));check(r.status()===200&&!r.headers()['x-uninest-demo-photo']&&!p.isDemo,'fresh uploads use original image files');}
  console.log(`PASS: ${checks} enabled real HTTP/MySQL/browser checks at 390px and 1280px; no mocked application APIs`);
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
