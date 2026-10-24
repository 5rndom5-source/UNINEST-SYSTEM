(() => {
  'use strict';
  const form = document.getElementById('propertyForm');
  const original = document.getElementById('propertyPhotos');
  if (!form || !original) return;
  const host = original.parentElement;
  host.replaceChildren();
  const types = ['Bedroom','Bathroom','Kitchen','Lounge','Study area','Laundry','Shared space','Exterior','Other'];
  const listing = { rooms: [] };
  let generation = 0, saving = false, loading = false, loadFailed = false, dragged = null, dirty = false;
  const status = document.getElementById('listingStatus');
  const submit = document.getElementById('propertySubmitButton');
  const requests = new Set();
  const all = () => listing.rooms.flatMap(room => room.photos);
  const node = (tag, text, cls) => { const e = document.createElement(tag); if (text) e.textContent = text; if (cls) e.className = cls; return e; };
  const button = (text, title, action, cls = 'photo-tool') => { const e = node('button',text,cls); e.type='button'; e.title=title; e.setAttribute('aria-label',title); e.addEventListener('click',action); return e; };
  const uuid = () => crypto.randomUUID();
  const chips = node('div',null,'room-add'); chips.setAttribute('role','group');chips.setAttribute('aria-label','Add room');
  const count = node('p',null,'photo-count');count.setAttribute('aria-live','polite');
  const error = node('p',null,'field-error');error.setAttribute('role','alert');error.tabIndex=-1;
  const roomsHost = node('div');
  host.append(chips,count,error,roomsHost);
  types.forEach(type => chips.append(button('+ '+type,'Add '+type,() => {
    if(listing.rooms.length>=30) { error.textContent='Use at most 30 room groups.'; return; }
    let n=1;while(listing.rooms.some(r=>r.label===`${type} ${n}`))n++;
    listing.rooms.push({id:uuid(),type,label:`${type} ${n}`,order:listing.rooms.length,photos:[]});dirty=true;render();
  },'')));
  function dispose(photo) { photo.removed=true;photo.xhr?.abort();if(photo.url?.startsWith('blob:'))URL.revokeObjectURL(photo.url); }
  function clear() { generation++;requests.forEach(x=>x.abort());requests.clear();all().forEach(dispose);listing.rooms=[];loadFailed=false;loading=false;submit.disabled=false;error.textContent='';render(); }
  function move(room,photo,offset) { const i=room.photos.indexOf(photo),j=i+offset;if(j<0||j>=room.photos.length)return;[room.photos[i],room.photos[j]]=[room.photos[j],room.photos[i]];dirty=true;render(); }
  function render() {
    const focus=document.activeElement?.dataset.focus;const start=document.activeElement?.selectionStart;
    roomsHost.replaceChildren();
    count.textContent=`${all().filter(p=>p.state==='ready').length} photos added, 10+ recommended`;
    listing.rooms.forEach(room => {
      const card=node('section',null,'photo-room');card.dataset.room=room.id;
      const header=node('header');header.append(node('h3',`${room.label} \u00b7 ${room.photos.filter(p=>p.state==='ready').length} photos`));
      header.append(button('\u270e','Rename '+room.label,()=>{const value=prompt('Room name',room.label);if(value===null)return;const label=value.trim();if(!label||label.length>80){error.textContent='Room names must contain 1 to 80 characters.';return;}room.label=label;dirty=true;render();}));
      header.append(button('\u00d7','Delete '+room.label,()=>{if(room.photos.length&&!confirm(`Delete ${room.label} and its ${room.photos.length} photos?`))return;room.photos.forEach(dispose);listing.rooms=listing.rooms.filter(r=>r!==room);dirty=true;render();}));
      card.append(header);
      const zone=node('div',null,'photo-drop');
      const input=node('input');input.type='file';input.accept='.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp';input.multiple=true;input.hidden=true;input.setAttribute('aria-label','Photos for '+room.label);
      input.addEventListener('change',()=>{addFiles(room,Array.from(input.files));input.value='';});
      zone.append(input,button('Add photos','Add photos to '+room.label,()=>input.click(),''));
      const camera=node('input');camera.type='file';camera.accept='image/jpeg,image/png,image/webp';camera.setAttribute('capture','environment');camera.hidden=true;
      camera.addEventListener('change',()=>{addFiles(room,Array.from(camera.files));camera.value='';});
      const cameraButton=button('Camera','Take a photo for '+room.label,()=>camera.click(),'camera-button');
      zone.append(camera,cameraButton);
      zone.addEventListener('dragover',e=>{if(e.dataTransfer.types.includes('Files')){e.preventDefault();zone.classList.add('drag-over');}});
      zone.addEventListener('dragleave',()=>zone.classList.remove('drag-over'));
      zone.addEventListener('drop',e=>{e.preventDefault();zone.classList.remove('drag-over');if(e.dataTransfer.files.length)addFiles(room,Array.from(e.dataTransfer.files));});
      card.append(zone);
      if(!room.photos.some(p=>p.state==='ready'))card.append(node('p','Add at least 1 photo','photo-warning'));
      const grid=node('div',null,'photo-thumbs');
      room.photos.forEach(photo=>{
        const item=node('div',null,'photo-item');item.dataset.photo=photo.id;
        const square=node('div',null,'photo-square');
        if(photo.url){const img=node('img');img.src=photo.url;img.alt=photo.caption||room.label;img.loading='lazy';square.append(img);}
        square.append(button('\u00d7','Remove photo',()=>{dispose(photo);room.photos=room.photos.filter(p=>p!==photo);dirty=true;render();},'photo-tool remove'));
        const cover=button('\u2605','Set as listing cover',()=>{all().forEach(p=>p.isCover=p===photo);error.textContent='';dirty=true;render();},'photo-tool cover');cover.setAttribute('aria-pressed',String(!!photo.isCover));cover.disabled=photo.state!=='ready';square.append(cover);item.append(square);
        if(photo.state==='uploading'||photo.state==='queued') { const progress=node('progress');progress.max=100;progress.value=photo.progress||0;progress.setAttribute('aria-label','Uploading photo');photo.progressElement=progress;item.append(progress); }
        if(photo.error) { item.append(node('p',photo.error,'photo-error'));if(photo.file&&!photo.invalid)item.append(button('Retry','Retry upload',()=>{photo.error='';photo.state='queued';render();runUpload(photo,generation);},'')); }
        const caption=node('label','Caption');const field=node('input');field.type='text';field.maxLength=80;field.value=photo.caption||'';field.dataset.focus='caption-'+photo.id;field.addEventListener('input',()=>{photo.caption=field.value;dirty=true;});caption.append(field);item.append(caption);
        const moveLabel=node('label','Move to...');const select=node('select');select.dataset.focus='move-'+photo.id;
        listing.rooms.forEach(target=>{const option=node('option',target.label);option.value=target.id;option.selected=target===room;select.append(option);});
        select.addEventListener('change',()=>{const target=listing.rooms.find(r=>r.id===select.value);if(!target||target===room)return;room.photos=room.photos.filter(p=>p!==photo);target.photos.push(photo);dirty=true;render();});moveLabel.append(select);item.append(moveLabel);
        const order=node('div',null,'photo-order');const handle=button('\u283f','Drag to reorder photo',()=>{});handle.draggable=true;
        handle.addEventListener('dragstart',e=>{dragged={room,photo};e.dataTransfer.setData('text/plain',photo.id);e.dataTransfer.effectAllowed='move';});handle.addEventListener('dragend',()=>{dragged=null;});
        item.addEventListener('dragover',e=>{if(dragged?.room===room)e.preventDefault();});
        item.addEventListener('drop',e=>{if(dragged?.room!==room)return;e.preventDefault();const from=room.photos.indexOf(dragged.photo),to=room.photos.indexOf(photo);room.photos.splice(from,1);room.photos.splice(to,0,dragged.photo);dragged=null;dirty=true;render();});
        const earlier=button('\u2190','Move photo earlier',()=>move(room,photo,-1));earlier.disabled=room.photos.indexOf(photo)===0;
        const later=button('\u2192','Move photo later',()=>move(room,photo,1));later.disabled=room.photos.indexOf(photo)===room.photos.length-1;
        order.append(handle,earlier,later);item.append(order);grid.append(item);
      });card.append(grid);roomsHost.append(card);
    });
    if(focus){const el=Array.from(roomsHost.querySelectorAll('[data-focus]')).find(e=>e.dataset.focus===focus);el?.focus({preventScroll:true});if(el?.type==='text'&&start!=null)el.setSelectionRange(start,start);}
  }
  async function compress(file) {
    const image=await createImageBitmap(file);try {
      const ratio=Math.min(1,1600/Math.max(image.width,image.height));
      const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(image.width*ratio));canvas.height=Math.max(1,Math.round(image.height*ratio));
      const ctx=canvas.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(image,0,0,canvas.width,canvas.height);
      return await new Promise((resolve,reject)=>canvas.toBlob(blob=>blob?resolve(blob):reject(new Error('Cannot resize this photo.')),'image/jpeg',0.85));
    } finally {image.close();}
  }
  function addFiles(room,files) {
    const epoch=generation;const added=[];
    for(const file of files){
      if(all().length>=60){error.textContent='Use at most 60 photos per listing.';break;}
      const validType=['image/jpeg','image/png','image/webp'].includes(file.type);
      const problem=!validType?'Choose JPG, PNG or WebP.':file.size>10*1024*1024?'Photo exceeds 10 MB.':file.size===0?'This file is empty.':'';
      const photo={id:uuid(),token:uuid(),file,url:validType&&!problem?URL.createObjectURL(file):'',caption:'',isCover:false,state:problem?'failed':'queued',invalid:!!problem,error:problem};
      room.photos.push(photo);if(!problem)added.push(photo);
    }
    dirty=true;render();
    // Sequential compression avoids decoding a large selection simultaneously on mobile.
    (async()=>{for(const photo of added){if(epoch!==generation)break;if(photo.removed)continue;await runUpload(photo,epoch);}})();
  }
  async function runUpload(photo,epoch) {
    photo.state='uploading';photo.progress=0;photo.error='';render();
    try {
      const blob=photo.blob||await compress(photo.file);photo.blob=blob;
      const csrf=await window.uninestCsrf.token();if(epoch!==generation||photo.removed)return;
      await new Promise((resolve,reject)=>{
        const xhr=new XMLHttpRequest();photo.xhr=xhr;requests.add(xhr);xhr.open('POST','../ListingPhotoUploadServlet');xhr.setRequestHeader('X-CSRF-Token',csrf);xhr.timeout=120000;
        xhr.upload.onprogress=e=>{photo.progress=e.lengthComputable?Math.round(e.loaded/e.total*100):0;if(photo.progressElement)photo.progressElement.value=photo.progress;};
        xhr.onload=()=>{requests.delete(xhr);let data;try{data=JSON.parse(xhr.responseText);}catch{}if(xhr.status>=200&&xhr.status<300&&data?.token===photo.token)resolve();else reject(new Error(data?.message||'Upload failed. Sign in again if your session expired, then retry.'));};
        xhr.onerror=xhr.ontimeout=()=>{requests.delete(xhr);reject(new Error('Upload interrupted. Retry this photo.'));};xhr.onabort=()=>{requests.delete(xhr);reject(new Error('Upload cancelled.'));};
        const body=new FormData();body.append('token',photo.token);body.append('photo',blob,'photo.jpg');xhr.send(body);
      });
      photo.state='ready';photo.ref=photo.token;
    } catch(e){photo.state='failed';photo.error=e.message||'Photo could not be processed.';}
    if(epoch===generation&&!photo.removed)render();
  }
  form.addEventListener('input',()=>{dirty=true;});
  form.addEventListener('invalid',e=>{
    const field=e.target;if(!field.closest('label'))return;let msg=field.closest('label').querySelector('.field-error');
    if(!msg){msg=node('span',null,'field-error');msg.id='error-'+(field.id||field.name);field.closest('label').append(msg);field.setAttribute('aria-describedby',msg.id);}
    msg.textContent=field.validationMessage;field.setAttribute('aria-invalid','true');
  },true);
  form.addEventListener('input',e=>{if(e.target.validity?.valid){e.target.removeAttribute('aria-invalid');const msg=e.target.closest('label')?.querySelector('.field-error');if(msg)msg.textContent='';}});
  form.addEventListener('reset',e=>{if(saving){e.preventDefault();return;}clear();dirty=false;form.querySelectorAll('.field-error').forEach(e=>e.textContent='');});
  form.addEventListener('listing:edit',async e=>{
    clear();const epoch=generation;loading=true;submit.disabled=true;status.textContent='Loading listing...';
    try {
      const res=await fetch('../PropertyGalleryServlet?roomId='+encodeURIComponent(e.detail.id),{cache:'no-store'});
      if(!res.ok)throw new Error('Could not load this listing. Cancel and try again.');
      const data=await res.json();if(epoch!==generation)return;if(!data.listing)throw new Error('Listing is unavailable.');
      listing.rooms=data.listing.rooms.map(r=>({...r,photos:r.photos.map(p=>({...p,ref:String(p.id),state:'ready'}))}));
      const defaults={suburb:'',city:'',deposit:'0',bedrooms:'1',bathrooms:'1',campusDistance:'',contactName:'',contactEmail:'',contactPhone:''};
      Object.entries({...defaults,...data.listing.details}).forEach(([k,v])=>{if(form.elements[k])form.elements[k].value=v??'';});
      form.querySelectorAll('[name=amenities]').forEach(input=>input.checked=data.listing.amenities.includes(input.value));
      dirty=false;status.textContent='Editing listing';render();form.dispatchEvent(new CustomEvent('listing:loaded'));
    }catch(err){if(epoch===generation){loadFailed=true;status.textContent=err.message;}}
    finally {if(epoch===generation){loading=false;submit.disabled=false;}}
  });
  form.addEventListener('submit',async e=>{
    e.preventDefault();if(saving)return;
    const photos=all();error.textContent='';
    if(loading||loadFailed)error.textContent='Load the listing successfully before saving, or cancel to start a new listing.';
    else if(photos.some(p=>p.state!=='ready'))error.textContent='Wait for uploads to finish. Retry or remove failed photos.';
    else if(photos.length<5)error.textContent='Add at least 5 photos before saving.';
    else if(photos.filter(p=>p.isCover).length!==1)error.textContent='Choose one cover photo using its star button.';
    if(error.textContent){error.focus();return;}
    const body=new URLSearchParams(new FormData(form));body.set('groupCount',listing.rooms.length);body.set('photoCount',photos.length);body.set('coverIndex',photos.findIndex(p=>p.isCover));
    let index=0;listing.rooms.forEach((room,ri)=>{
      body.set('groupId'+ri,room.id);body.set('groupType'+ri,room.type);body.set('groupLabel'+ri,room.label);
      room.photos.forEach(photo=>{body.set('photoRef'+index,photo.ref);body.set('photoGroup'+index,ri);body.set('photoCaption'+index,photo.caption||'');index++;});
    });
    saving=true;form.inert=true;submit.disabled=true;status.textContent='Saving listing...';
    try {
      const res=await fetch(form.action,{method:'POST',body});let data;try{data=await res.json();}catch{}
      if(!res.ok||!data?.saved)throw new Error(data?.message||'Listing was not confirmed saved. Check your session and retry.');
      dirty=false;window.location.assign('listings.html?status=pending');
    }catch(err){status.textContent=err.message;error.textContent=err.message;}
    finally {saving=false;form.inert=false;submit.disabled=false;}
  });
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  render();
})();
