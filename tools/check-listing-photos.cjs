const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const root=path.resolve(__dirname,'..');
const origin='http://127.0.0.1:8199';
let checks=0;
function check(value,label){assert.ok(value,label);checks++;}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  const context=await browser.newContext();const page=await context.newPage();
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  let uploads=0,saves=0,failNext=false,savedBody;
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
  const fixture={id:1,title:'Campus House',description:'A furnished student home.',location:'Cape Town',price:5000,details:{deposit:'2500',suburb:'Rondebosch',city:'Cape Town',bedrooms:'2',bathrooms:'1'},amenities:['WiFi'],rooms:[{id:'11111111-1111-4111-8111-111111111111',type:'Bedroom',label:'Bedroom 1',order:0,photos:[1,2,3].map((id,i)=>({id,url:origin+'/photo.png',caption:i===0?'Window view':'',order:i,isCover:i===0}))},{id:'22222222-2222-4222-8222-222222222222',type:'Kitchen',label:'Kitchen 1',order:1,photos:[4,5].map((id,i)=>({id,url:origin+'/photo.png',caption:'',order:i,isCover:false}))},{id:'33333333-3333-4333-8333-333333333333',type:'Other',label:'Empty room',order:2,photos:[]}]};
  await page.route('**/*',async route=>{
   const req=route.request(),url=new URL(req.url());const pathname=decodeURIComponent(url.pathname);
   if(url.origin!==origin)return route.abort();
   if(pathname.endsWith('/CsrfTokenServlet'))return route.fulfill({json:{token:'a'.repeat(43)}});
   if(pathname.endsWith('/MapsConfigServlet'))return route.fulfill({json:{enabled:true,key:'test-browser-key-not-real-12345'}});
   if(pathname.endsWith('/ListingPhotoUploadServlet')){
    uploads++;check(req.headers()['x-csrf-token']==='a'.repeat(43),'CSRF header on upload');
    if(failNext){failNext=false;return route.fulfill({status:503,json:{message:'Test upload interruption'}});}
    const body=req.postDataBuffer().toString('latin1');const match=body.match(/name="token"\r\n\r\n([^\r]+)/);
    return route.fulfill({json:{token:match?.[1]}});
   }
   if(pathname.endsWith('/LandlordAddPropertyServlet')){saves++;savedBody=new URLSearchParams(req.postData());return route.fulfill({status:503,json:{message:'Test save boundary reached'}});}
   if(pathname.endsWith('/PropertyGalleryServlet'))return route.fulfill({json:{listing:fixture}});
   if(pathname.endsWith('/LandlordListingsServlet'))return route.fulfill({json:{listings:[],page:1,totalPages:0}});
   if(pathname==='/photo.png')return route.fulfill({contentType:'image/png',body:fs.readFileSync(path.join(root,'HomePage/listing-rondebosch.png'))});
   if(pathname.endsWith('/portal-identity.js')||pathname.endsWith('/system-status.js'))return route.fulfill({contentType:'text/javascript',body:''});
   const rel=pathname.replace(/^\/uninest\//,'');const file=path.resolve(root,rel);
   if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile())return route.fulfill({status:404,body:''});
   const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png'}[path.extname(file)]||'application/octet-stream';
   return route.fulfill({contentType:mime,body:fs.readFileSync(file)});
  });
  await page.goto(origin+'/uninest/Landlord/listings.html');
  await page.locator('#propertyTitle').fill('Campus House');await page.locator('#propertyDescription').fill('Student home');await page.locator('#propertyLocation').fill('Cape Town');await page.locator('#propertyPrice').fill('5000');
  check(new URL(await page.locator('#listingAddressMap a').first().getAttribute('href')).searchParams.get('query')==='Cape Town','entered address linked to Maps');
  await page.locator('#listingAddressMap button').click();
  check(new URL(await page.locator('#listingAddressMap iframe').getAttribute('src')).searchParams.get('q')==='Cape Town','Embed API address query');
  await page.locator('[name=suburb]').fill('Rondebosch');
  await page.waitForFunction(()=>document.querySelector('#listingAddressMap iframe').src.includes('Rondebosch'));
  check(new URL(await page.locator('#listingAddressMap iframe').getAttribute('src')).searchParams.get('q')==='Cape Town, Rondebosch','map updates with address fields');
  await page.getByRole('button',{name:'Add Bedroom',exact:true}).click();await page.getByRole('button',{name:'Add Bedroom',exact:true}).click();
  check(await page.getByRole('heading',{name:'Bedroom 2',exact:false}).count()===1,'numbered rooms');
  const fileInput=page.locator('.photo-room input[type=file]').first();
  await fileInput.setInputFiles({name:'invalid.txt',mimeType:'text/plain',buffer:Buffer.from('wrong')});
  check(await page.locator('.photo-error').textContent()==='Choose JPG, PNG or WebP.','wrong type immediate');
  await page.getByRole('button',{name:'Remove photo',exact:true}).click();
  await fileInput.setInputFiles({name:'large.jpg',mimeType:'image/jpeg',buffer:Buffer.alloc(10*1024*1024+1)});
  check((await page.locator('.photo-error').textContent()).includes('10 MB'),'oversize immediate');await page.getByRole('button',{name:'Remove photo',exact:true}).click();
  const imageData=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=2200;c.height=1100;const x=c.getContext('2d');x.fillStyle='#55a07b';x.fillRect(0,0,c.width,c.height);return c.toDataURL('image/png').split(',')[1];});
  const files=Array.from({length:5},(_,i)=>({name:`room-${i}.png`,mimeType:'image/png',buffer:Buffer.from(imageData,'base64')}));
  failNext=true;await fileInput.setInputFiles(files);
  await page.waitForFunction(()=>document.querySelector('.photo-count').textContent.startsWith('4 photos'));
  check(await page.getByRole('button',{name:'Retry upload'}).count()===1,'failed upload retry available');
  await page.getByRole('button',{name:'Retry upload'}).click();await page.waitForFunction(()=>document.querySelector('.photo-count').textContent.startsWith('5 photos'));
  check(uploads===6,'other photos uploaded after failure');
  await page.getByRole('button',{name:'Save listing',exact:true}).click();await page.waitForTimeout(150);
  check(saves===0,'cover required');
  await page.getByRole('button',{name:'Set as listing cover'}).first().click();
  await page.locator('.photo-item input[type=text]').first().fill('Sunny bedroom');
  await page.getByRole('button',{name:'Move photo later'}).first().click();
  await page.locator('.photo-item select').first().selectOption({label:'Bedroom 2'});
  check(await page.locator('.photo-room').nth(1).locator('.photo-item').count()===1,'move between rooms');
  for(const width of [320,390,1280]){
   await page.setViewportSize({width,height:900});
   const metrics=await page.evaluate(()=>({width:document.querySelector('#propertyForm').getBoundingClientRect().width,overflow:document.documentElement.scrollWidth>innerWidth,heights:Array.from(document.querySelectorAll('#propertyForm input:not([type=hidden]):not([type=file]):not([type=checkbox]),#propertyForm select')).map(e=>e.getBoundingClientRect().height),columns:getComputedStyle(document.querySelector('.photo-thumbs')).gridTemplateColumns.split(' ').length,pair:getComputedStyle(document.querySelector('.field-pair')).gridTemplateColumns.split(' ').length}));
   check(metrics.width<=640,'form max width '+width);check(!metrics.overflow,'no overflow '+width);check(metrics.heights.every(h=>h===44),'44px controls '+width);check(metrics.columns===(width<600?3:4),'thumbnail columns '+width);check(metrics.pair===(width<600?1:2),'paired fields '+width);
   await page.screenshot({path:path.join(root,`test-runtime/listing-editor-${width}.png`),fullPage:true});
  }
  await page.getByRole('button',{name:'Save listing',exact:true}).click();await page.waitForFunction(()=>document.getElementById('listingStatus').textContent==='Test save boundary reached');
  check(saves===1&&savedBody.get('photoCount')==='5','save manifest');check(savedBody.get('photoCaption0')==='Sunny bedroom','caption follows reordered photo');
  await page.evaluate(()=>document.getElementById('propertyForm').dispatchEvent(new CustomEvent('listing:edit',{detail:{id:1}})));
  await page.waitForFunction(()=>document.getElementById('listingStatus').textContent==='Editing listing');
  check(await page.locator('[name=deposit]').inputValue()==='2500','edit details restored');check(await page.locator('.photo-item').count()===5,'existing photos restored');
  await page.addScriptTag({url:origin+'/uninest/property-gallery.js'});await page.evaluate(()=>window.openPropertyGallery(1));await page.getByRole('button',{name:'View all 5 photos',exact:true}).waitFor();
  check(await page.locator('.hero-grid img').count()===5,'cover plus four hero');
  await page.getByRole('button',{name:'View all 5 photos',exact:true}).click();check(await page.locator('.tour-chips button').count()===2,'empty rooms hidden');
  await page.getByRole('button',{name:'View Bedroom 1 photo'}).last().click();await page.keyboard.press('ArrowRight');
  check(await page.locator('.photo-fullscreen h2').textContent()==='Kitchen 1 \u00b7 1 of 2','keyboard crosses room boundary');check(await page.locator('.photo-strip button').count()===5,'thumbnail strip');
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(root,'test-runtime/photo-viewer-mobile.png')});
  await page.keyboard.press('Escape');await page.screenshot({path:path.join(root,'test-runtime/photo-tour-mobile.png')});
  check(errors.length===0,errors.join('\n'));console.log(`PASS: ${checks} listing photo browser checks (mock API)`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
