const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),origin='http://127.0.0.1:8199';
let checks=0;
function check(value,label){assert.ok(value,label);checks++;}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  let fail=false,requests=0;
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());if(url.origin!==origin)return route.abort();
   const rel=decodeURIComponent(url.pathname).replace(/^\/uninest\//,'');
   if(rel==='PublicListingsServlet'){
    requests++;
    if(fail)return route.fulfill({status:503,json:{error:'Unavailable'}});
    const search=url.searchParams.get('search')||'Campus House';
    if(search==='old')await new Promise(resolve=>setTimeout(resolve,600));
    return route.fulfill({json:{page:Number(url.searchParams.get('page')||1),totalPages:10000,total:60000,locations:['Rondebosch'],listings:[{id:1,title:search,area:'Rondebosch',price:'R 5,000 / month',description:'Furnished room near campus',image:'/uninest/HomePage/listing-rondebosch.png',tags:['WiFi']}]}}).catch(()=>{});
   }
   if(rel==='CsrfTokenServlet')return route.fulfill({json:{token:'a'.repeat(43)}});
   if(rel==='DatabaseStatusServlet')return route.fulfill({json:{connected:true}});
   if(rel==='CurrentUserServlet')return route.fulfill({json:{fullName:'Test Admin',email:'test@example.invalid',role:'admin'}});
   if(rel==='portal-identity.js')return route.fulfill({contentType:'text/javascript',body:''});
   if(rel.endsWith('Servlet'))return route.fulfill({status:404,json:{}});
   const file=path.resolve(root,rel);
   if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile())return route.fulfill({status:404,body:''});
   const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.jpg':'image/jpeg'}[path.extname(file)]||'application/octet-stream';
   return route.fulfill({contentType:mime,body:fs.readFileSync(file)});
  });
  await page.goto(origin+'/uninest/HomePage/search.html?search=Campus&minPrice=1000&maxPrice=6000&location=Rondebosch&facility=Wifi&sort=price_asc&page=4');
  await page.locator('.listing-card').waitFor();
  check(await page.locator('#minPriceFilter').inputValue()==='1000','minimum restored');
  check(await page.locator('#locationFilter').inputValue()==='Rondebosch','location restored');
  check(await page.locator('[name=facility][value=Wifi]').isChecked(),'facility restored');
  check(await page.locator('#searchPagination button').count()<=7,'bounded pagination');
  check(await page.locator('[aria-current=page][data-page]').getAttribute('data-page')==='4','page restored');
  check(await page.locator('.results').evaluate(el=>el.lastElementChild.id==='searchPagination'),'pagination follows cards');
  check(await page.locator('.listing-card [data-gallery]').count()===1,'public details open gallery');
  await page.locator('#publicSearchInput').fill('old');await page.locator('#publicSearchButton').click();
  await page.locator('#publicSearchInput').fill('new');await page.locator('#publicSearchButton').click();
  await page.waitForFunction(()=>document.querySelector('.listing-card h2')?.textContent==='new');
  await page.waitForTimeout(800);
  check(await page.locator('.listing-card h2').innerText()==='new','stale response cannot overwrite');
  check(new URL(page.url()).searchParams.get('search')==='new','search persisted in URL');
  await page.locator('#minPriceFilter').fill('9000');const previous=requests;await page.locator('#applyFilters').click();
  check((await page.locator('#publicListingsState').innerText()).includes('valid price range'),'invalid price feedback');
  check(requests===previous,'invalid range does not fetch');
  await page.locator('#minPriceFilter').fill('1000');fail=true;await page.locator('#applyFilters').click();await page.locator('#searchRetry').waitFor();
  check(await page.locator('.results').getAttribute('aria-busy')==='false','failure clears busy state');
  fail=false;await page.locator('#searchRetry').click();await page.locator('.listing-card').waitFor();
  check(await page.locator('#searchRetry').count()===0,'retry recovers');
  for(const width of [320,390,1280]){
   await page.setViewportSize({width,height:900});await page.waitForTimeout(250);
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'search no overflow '+width);
   await page.screenshot({path:`test-runtime/search-polish-${width}.png`,fullPage:true});
  }
  await page.emulateMedia({reducedMotion:'reduce'});await page.locator('#publicSearchButton').click();await page.locator('.listing-card').waitFor();
  check(await page.locator('.listing-card').evaluate(el=>getComputedStyle(el).animationName)==='none','reduced motion');
  await page.goto(origin+'/uninest/Admin/web/jsp/Profile.html');
  const trigger=page.locator('.admin-profile-trigger');await trigger.waitFor();
  await page.waitForFunction(()=>document.querySelector('[data-accessible-menu]'));
  await trigger.focus();await page.keyboard.press('Enter');
  check(await trigger.getAttribute('aria-expanded')==='true','Enter opens menu');
  await page.keyboard.press('Escape');check(await trigger.getAttribute('aria-expanded')==='false','Escape closes menu');
  await page.keyboard.press('ArrowDown');check(await page.evaluate(()=>document.activeElement.closest('.admin-profile-dropdown')!==null),'arrow moves focus to actions');
  await page.keyboard.press('Escape');check(await trigger.evaluate(el=>el===document.activeElement),'focus restored');
  check(await trigger.evaluate(el=>getComputedStyle(el).outlineStyle)!=='none','visible focus ring');
  check(await page.locator('.uninest-skip-link').count()===1,'skip link');
  check(errors.length===0,'no browser errors: '+errors.join('; '));
  console.log(`PASS: ${checks} experience checks`);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
