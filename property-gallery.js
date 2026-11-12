(() => {
  'use strict';
  const base=new URL('.',document.currentScript.src);
  if(!window.uninestMaps&&!document.querySelector('script[data-listing-maps]')){const script=document.createElement('script');script.src=new URL('maps.js',base);script.dataset.listingMaps='true';document.head.append(script);}
  if(!document.querySelector('[data-photo-tour-style]')){const css=document.createElement('link');css.rel='stylesheet';css.href=new URL('property-gallery.css',base);css.dataset.photoTourStyle='true';document.head.append(css);}
  const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text)n.textContent=text;if(cls)n.className=cls;return n;};
  const button=(text,title,fn,cls='')=>{const b=el('button',text,cls);b.type='button';b.title=title;b.setAttribute('aria-label',title);b.addEventListener('click',fn);return b;};
  const demoLabel='DEMO PHOTO \u2014 NOT ACTUAL PROPERTY';
  function picture(photo,label,lazy=true){const img=el('img');img.alt=photo.isDemo?demoLabel:(photo.caption||label);img.loading=lazy?'lazy':'eager';img.decoding='async';img.src=photo.url;img.addEventListener('error',()=>{img.alt='Photo unavailable';img.classList.add('photo-unavailable');});return img;}
  function demoBadge(photo,parent){if(photo.isDemo){parent.classList.add('has-demo-photo');parent.append(el('span',demoLabel,'demo-photo-label'));}}
  function fullscreen(rooms,selected){
    const entries=rooms.flatMap(room=>room.photos.map((photo,i)=>({room,photo,index:i})));
    let current=entries.findIndex(e=>e.photo===selected);if(current<0)return;
    const dialog=el('dialog',null,'photo-fullscreen');dialog.setAttribute('aria-label','Fullscreen photo viewer');
    const top=el('header');const label=el('h2');label.setAttribute('aria-live','polite');top.append(label,button('\u00d7','Close fullscreen viewer',()=>dialog.close()));
    const stage=el('div',null,'photo-stage');const image=el('img');image.draggable=false;
    const previous=button('\u2190','Previous photo',()=>show(current-1),'photo-previous');const next=button('\u2192','Next photo',()=>show(current+1),'photo-next');stage.append(image,previous,next);
    const warning=el('p',demoLabel,'demo-photo-warning');warning.hidden=true;warning.setAttribute('role','status');
    const caption=el('p',null,'viewer-caption');const strip=el('div',null,'photo-strip');strip.setAttribute('aria-label','Photo thumbnails');
    entries.forEach((entry,i)=>{const thumb=button('',`${entry.room.label}, photo ${entry.index+1}`,()=>show(i));thumb.append(picture(entry.photo,entry.room.label));strip.append(thumb);});
    dialog.append(top,warning,stage,caption,strip);document.body.append(dialog);dialog.showModal();
    function show(index){current=Math.max(0,Math.min(entries.length-1,index));const entry=entries[current];image.src=entry.photo.url;image.alt=entry.photo.isDemo?demoLabel:(entry.photo.caption||entry.room.label);warning.hidden=!entry.photo.isDemo;dialog.classList.toggle('showing-demo',!!entry.photo.isDemo);label.textContent=`${entry.room.label} \u00b7 ${entry.index+1} of ${entry.room.photos.length}`;caption.textContent=entry.photo.caption||'';previous.disabled=current===0;next.disabled=current===entries.length-1;Array.from(strip.children).forEach((b,i)=>b.setAttribute('aria-current',String(i===current)));strip.children[current].scrollIntoView({block:'nearest',inline:'center'});}
    dialog.addEventListener('keydown',e=>{if(e.key==='ArrowRight'){e.preventDefault();show(current+1);}if(e.key==='ArrowLeft'){e.preventDefault();show(current-1);}});
    let start=null;stage.addEventListener('touchstart',e=>{start={x:e.touches[0].clientX,y:e.touches[0].clientY};},{passive:true});stage.addEventListener('touchend',e=>{if(!start)return;const dx=e.changedTouches[0].clientX-start.x,dy=e.changedTouches[0].clientY-start.y;if(Math.abs(dx)>45&&Math.abs(dx)>Math.abs(dy))show(current+(dx<0?1:-1));start=null;},{passive:true});
    dialog.addEventListener('close',()=>dialog.remove(),{once:true});show(current);
  }
  window.openPropertyGallery=async id=>{
    if(document.querySelector('.property-tour'))return;
    const dialog=el('dialog',null,'property-tour');dialog.setAttribute('aria-label','Accommodation details and photos');
    const header=el('header',null,'tour-header');const title=el('h2','Accommodation photos');const close=button('\u00d7','Close accommodation photos',()=>dialog.close());header.append(title,close);
    const content=el('div',null,'tour-content');content.append(el('p','Loading photos...','gallery-loading'));dialog.append(header,content);document.body.append(dialog);
    const previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';dialog.showModal();
    const headerSize=new ResizeObserver(()=>dialog.style.setProperty('--tour-header-height',header.offsetHeight+'px'));headerSize.observe(header);
    dialog.addEventListener('close',()=>headerSize.disconnect(),{once:true});
    const controller=new AbortController();dialog.addEventListener('close',()=>{controller.abort();document.body.style.overflow=previousOverflow;dialog.remove();},{once:true});
    try {
      const response=await fetch(new URL('PropertyGalleryServlet?roomId='+encodeURIComponent(id),base),{signal:controller.signal,cache:'no-store'});if(!response.ok)throw new Error('Photos could not be loaded. Please close and try again.');
      const data=await response.json();const listing=data.listing;if(!listing)throw new Error('This listing is unavailable.');
      const rooms=listing.rooms.filter(r=>r.photos?.length).sort((a,b)=>a.order-b.order);rooms.forEach(r=>r.photos.sort((a,b)=>a.order-b.order));
      const photos=rooms.flatMap(r=>r.photos);title.textContent=listing.title||'Accommodation photos';content.replaceChildren();
      if(!photos.length){content.append(el('p','No photos have been added yet.'));return;}
      const cover=photos.find(p=>p.isCover)||photos[0];const ordered=[cover,...photos.filter(p=>p!==cover)];
      const hero=el('div',null,'photo-hero');const grid=el('div',null,'hero-grid');ordered.slice(0,5).forEach((photo,i)=>{const b=button('','Open photo '+(i+1),()=>fullscreen(rooms,photo));b.append(picture(photo,listing.title,false));demoBadge(photo,b);grid.append(b);});
      const tour=el('div',null,'room-tour');tour.hidden=true;
      const chips=el('nav',null,'tour-chips');chips.setAttribute('aria-label','Rooms');const sections=[];
      rooms.forEach((room,i)=>{
        const section=el('section',null,'tour-room');section.id='tour-room-'+i;section.append(el('h3',room.label));
        const roomGrid=el('div',null,'tour-photo-grid');room.photos.forEach(photo=>{const figure=el('figure');const b=button('','View '+room.label+' photo',()=>fullscreen(rooms,photo));b.append(picture(photo,room.label));demoBadge(photo,b);figure.append(b);if(photo.caption)figure.append(el('figcaption',photo.caption));roomGrid.append(figure);});section.append(roomGrid);sections.push(section);
        const chip=button('',`${room.label}, ${room.photos.length} photos`,()=>section.scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'}));chip.append(picture(room.photos[0],room.label),el('span',`${room.label} \u00b7 ${room.photos.length}`));chips.append(chip);
      });
      tour.append(chips,...sections);
      const viewAll=button(`View all ${photos.length} photos`,'View all '+photos.length+' photos',()=>{tour.hidden=false;hero.hidden=true;details.hidden=true;back.hidden=false;dialog.scrollTop=0;chips.querySelector('button')?.focus();activate();},'view-all');hero.append(grid,viewAll);
      const back=button('\u2190','Back to listing overview',()=>{tour.hidden=true;hero.hidden=false;details.hidden=false;back.hidden=true;dialog.scrollTop=0;viewAll.focus();});back.hidden=true;header.prepend(back);
      const details=el('section',null,'gallery-details');details.append(el('p',listing.location||''),el('p',`R ${Number(listing.price).toLocaleString('en-ZA')} / month`));
      const d=listing.details||{};
      const map=document.createElement('div');map.dataset.listingMapAddress=[listing.location,d.suburb,d.city].filter(Boolean).join(', ');details.append(map);
      if(d.deposit!==undefined)details.append(el('p',`Deposit: R ${Number(d.deposit).toLocaleString('en-ZA')}`));
      if(d.bedrooms!==undefined)details.append(el('p',`${d.bedrooms} rooms \u00b7 ${d.bathrooms} bathrooms`));
      if(d.campusDistance)details.append(el('p',`${d.campusDistance} km to campus`));
      details.append(el('p',listing.description||''));
      if(listing.amenities?.length)details.append(el('p',listing.amenities.join(' \u00b7 ')));
      if(d.contactName||d.contactEmail||d.contactPhone){details.append(el('h3','Contact'),el('p',[d.contactName,d.contactEmail,d.contactPhone].filter(Boolean).join(' \u00b7 ')));}
      content.append(hero,details,tour);
      function activate(){if(tour.hidden)return;const top=dialog.getBoundingClientRect().top+header.offsetHeight+chips.offsetHeight+24;let active=0;sections.forEach((s,i)=>{if(s.getBoundingClientRect().top<=top)active=i;});Array.from(chips.children).forEach((chip,i)=>chip.setAttribute('aria-current',String(i===active)));}
      dialog.addEventListener('scroll',activate,{passive:true});
    }catch(e){if(e.name!=='AbortError')content.replaceChildren(el('p',e.message,'gallery-error'));}
  };
  document.addEventListener('click',e=>{const target=e.target.closest('[data-gallery]');if(target){e.preventDefault();window.openPropertyGallery(target.dataset.gallery);}});
})();
