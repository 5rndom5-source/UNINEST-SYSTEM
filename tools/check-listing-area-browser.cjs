const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const base=process.env.UNINEST_TEST_BASE;
assert.match(base||'',/^http:\/\/127\.0\.0\.1:\d+\/uninest$/);
let checks=0;
function check(ok,label){assert.ok(ok,label);checks++;}
async function token(context){return (await (await context.request.get(base+'/CsrfTokenServlet')).json()).token;}
async function login(context,role){
 const res=await context.request.post(base+'/AccountLoginServlet',{form:{email:role+'@example.invalid',role,password:'Testing!2026',_csrf:await token(context)},maxRedirects:0});
 check(res.status()===302&&!res.headers().location.includes('error='),'fixture login');
}
async function listings(context,query){const r=await context.request.get(base+'/PublicListingsServlet?'+query);check(r.status()===200,'listing API success');return r.json();}
async function edit(page,id){
 await page.goto(base+'/Landlord/listings.html');
 await page.locator('.classic-property-card').first().waitFor();
 const button=page.locator(`.classic-property-card[data-id="${id}"] .edit-btn`);
 while(!await button.count()) {
  const next=page.getByRole('button',{name:'Next',exact:true});
  assert.ok(await next.isEnabled(),'listing must exist in pagination');
  const firstId=await page.locator('.classic-property-card').first().getAttribute('data-id');
  await Promise.all([page.waitForResponse(r=>r.url().includes('/LandlordListingsServlet?')),next.click()]);
  await page.waitForFunction(id=>document.querySelector('.classic-property-card')?.dataset.id!==id,firstId);
 }
 await button.click();await page.locator('#saveListingArea').waitFor({state:'visible'});
}
async function saved(page,button){await Promise.all([page.waitForURL('**/Landlord/listings.html?status=pending'),button.click()]);}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  const anon=await browser.newContext();const student=await browser.newContext();await login(student,'student');
  for(const context of [anon,student]) {
   const data=await listings(context,'search=Cape%20Town');
   assert.deepEqual(data.listings.map(x=>Number(x.id)).sort(),[1,3]);checks++;
   const filtered=await listings(context,'location=Cape%20Town%20CBD&search=cape%20town&size=1&page=2');
   check(filtered.total===2&&filtered.totalPages===2&&filtered.listings.length===1,'city plus area pagination/count agree');
   check(filtered.locations.includes('Cape Town CBD')&&!filtered.locations.includes('12 Main Road'),'area options exclude street addresses');
   check((await listings(context,'search=Stellenbosch')).total===1,'other city search');
   check((await listings(context,'location=Claremont')).total===0,'unassigned area not guessed');
   check((await listings(context,"location=%27%20OR%201%3D1")).total===0,'filter SQL injection treated as text');
  }
  for(const width of [390,1280]) {
   const publicPage=await anon.newPage();await publicPage.setViewportSize({width,height:900});
   await publicPage.goto(base+'/HomePage/search.html?search=Cape%20Town');
   await publicPage.getByText('Modern House',{exact:true}).waitFor();
   if(!await publicPage.locator('#locationFilter').isVisible())await publicPage.locator('.search-filter-panel summary').click();
   await publicPage.locator('#locationFilter').selectOption('Cape Town CBD');
   await publicPage.locator('#applyFilters').click();
   await publicPage.getByText('Legacy CBD',{exact:true}).waitFor();
   check(await publicPage.getByText('Modern House',{exact:true}).isVisible(),'public city/area UI result');
   check(await publicPage.getByText('Other City',{exact:true}).count()===0,'public excludes other city');
   const studentPage=await student.newPage();await studentPage.setViewportSize({width,height:900});
   await studentPage.goto(base+'/UserDashboard/listings.html');
   await Promise.all([studentPage.waitForResponse(r=>r.url().includes('/PublicListingsServlet?')&&new URL(r.url()).searchParams.get('search')==='Cape Town'),studentPage.locator('#globalSearch').fill('Cape Town')]);
   await studentPage.getByText('Other City',{exact:true}).waitFor({state:'hidden'});
   await studentPage.getByText('Modern House',{exact:true}).waitFor();
   await studentPage.locator('#areaFilter').selectOption('Cape Town CBD');
   await studentPage.getByText('Legacy CBD',{exact:true}).waitFor();
   check(await studentPage.getByText('Modern House',{exact:true}).isVisible(),'student city/area UI result');
   await publicPage.screenshot({path:`test-runtime/area-public-${width}.png`,fullPage:true});
   await studentPage.screenshot({path:`test-runtime/area-student-${width}.png`,fullPage:true});
   await publicPage.close();await studentPage.close();
  }
  const landlord=await browser.newContext();await login(landlord,'landlord');const page=await landlord.newPage();
  const map=await landlord.request.get(base+'/ListingMapServlet?roomId=1',{maxRedirects:0});
  check(new URL(map.headers().location).searchParams.get('query')==='12 Main Road, Observatory, Cape Town','map redirect uses street address, not area');
  check((await landlord.request.post(base+'/LandlordAddPropertyServlet',{form:{editorVersion:'2',areaOnly:'1',roomId:'2',area:'Rondebosch'}})).status()===403,'area edit requires CSRF');
  check((await landlord.request.post(base+'/LandlordAddPropertyServlet',{form:{editorVersion:'2',areaOnly:'1',roomId:'2',area:'Invalid',_csrf:await token(landlord)}})).status()===400,'invalid area rejected');
  check((await student.request.post(base+'/LandlordAddPropertyServlet',{form:{editorVersion:'2',areaOnly:'1',roomId:'2',area:'Rondebosch',_csrf:await token(student)}})).status()===401,'student cannot edit area');
  for(const width of [390,1280]) {
   await page.setViewportSize({width,height:900});await edit(page,2);
   check(await page.locator('#propertyLocation').inputValue()==='5 Quiet Lane','legacy address loads unchanged');
   check(await page.locator('[name=city]').inputValue()==='','missing city remains blank');
   check(await page.locator('[name=bedrooms]').inputValue()===''&&await page.locator('[name=deposit]').inputValue()==='','missing room count and deposit are not invented');
   check(await page.locator('#propertyArea').inputValue()===(width===390?'':'Rondebosch'),'area reloads only explicit selection');
   assert.deepEqual(await page.locator('#propertyArea option').allTextContents(),['Not specified','Cape Town CBD','Rondebosch','Observatory','Bellville','Claremont','Stellenbosch']);checks++;
   await page.locator('#propertyArea').selectOption(width===390?'Rondebosch':'Bellville');
   check(new URL(await page.locator('#listingAddressMap a').first().getAttribute('href')).searchParams.get('query')==='5 Quiet Lane','area selection does not replace map address');
   check(await page.evaluate(()=>document.querySelector('#propertyArea').getBoundingClientRect().right<=innerWidth),'area dropdown fits viewport');
   await page.locator('#locationHeading').scrollIntoViewIfNeeded();
   await page.screenshot({path:`test-runtime/area-editor-${width}.png`});
   await saved(page,page.locator('#saveListingArea'));
   const gallery=await (await landlord.request.get(base+'/PropertyGalleryServlet?roomId=2')).json();
   check(gallery.listing.rooms.flatMap(x=>x.photos).length===1,'one-photo legacy listing preserved');
  }
  await edit(page,1);await page.locator('#propertyArea').selectOption('Claremont');
  await saved(page,page.getByRole('button',{name:'Save listing',exact:true}));
  let gallery=await (await landlord.request.get(base+'/PropertyGalleryServlet?roomId=1')).json();
  check(gallery.listing.details.area==='Claremont'&&gallery.listing.rooms.flatMap(x=>x.photos).length===6,'full edit retains six photos and area');
  await page.setViewportSize({width:390,height:900});await page.goto(base+'/Landlord/listings.html');
  check(await page.locator('#propertyArea').inputValue()==='','new listing starts without inferred area');
  check(await page.locator('#saveListingArea').isHidden(),'area-only action hidden for new listing');
  await page.locator('#propertyTitle').fill('New area listing');await page.locator('#propertyDescription').fill('Test listing only');
  await page.locator('#propertyLocation').fill('22 Test Street');await page.locator('[name=city]').fill('Stellenbosch');
  await page.locator('#propertyArea').selectOption('Stellenbosch');await page.locator('#propertyPrice').fill('1500');
  await page.getByRole('button',{name:'Add Bedroom',exact:true}).click();
  await page.locator('.photo-room input[type=file]').first().setInputFiles(Array(5).fill(path.resolve(__dirname,'../HomePage/listing-rondebosch.png')));
  await page.waitForFunction(()=>document.querySelector('.photo-count').textContent.startsWith('5 photos'),{},{timeout:120000});
  await page.getByRole('button',{name:'Set as listing cover'}).first().click();
  await saved(page,page.getByRole('button',{name:'Save listing',exact:true}));
  await page.getByText('New area listing',{exact:true}).waitFor();
  check(await page.getByText('New area listing',{exact:true}).isVisible(),'new listing form saved');
  console.log(`PASS: ${checks} real MySQL/HTTP area, search, editing and map checks at 390px/1280px; no mocked application APIs`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
