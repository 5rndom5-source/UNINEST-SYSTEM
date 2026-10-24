(() => {
  'use strict';
  if(window.uninestMaps)return;
  const base=new URL('.',document.currentScript.src);
  const css=document.createElement('link');css.rel='stylesheet';css.href=new URL('maps.css',base);document.head.append(css);
  const config=fetch(new URL('MapsConfigServlet',base),{cache:'no-store'}).then(r=>r.ok?r.json():{enabled:false}).catch(()=>({enabled:false}));
  const instances=new WeakMap();
  function address(parts){const seen=new Set();return parts.map(s=>String(s||'').trim()).filter(s=>{const key=s.toLowerCase();if(!s||seen.has(key))return false;seen.add(key);return true;}).join(', ');}
  function mount(host,initial){
    if(instances.has(host)){instances.get(host).update(initial);return instances.get(host);}
    host.classList.add('listing-map');
    const actions=document.createElement('div');actions.className='map-actions';
    const link=document.createElement('a');link.textContent='Open in Google Maps';link.target='_blank';link.rel='noopener noreferrer';
    const directions=document.createElement('a');directions.textContent='Directions';directions.target='_blank';directions.rel='noopener noreferrer';
    const preview=document.createElement('button');preview.type='button';preview.textContent='Preview map';preview.hidden=true;
    const box=document.createElement('div');box.className='map-frame';box.hidden=true;
    actions.append(link,directions,preview);host.replaceChildren(actions,box);
    let current=null,key='',frame=null,timer;
    function update(value){const next=String(value||'').trim();if(next===current)return;current=next;host.hidden=!current;
      const search=new URL('https://www.google.com/maps/search/');search.searchParams.set('api','1');search.searchParams.set('query',current);link.href=search;
      const route=new URL('https://www.google.com/maps/dir/');route.searchParams.set('api','1');route.searchParams.set('destination',current);directions.href=route;
      clearTimeout(timer);if(frame)timer=setTimeout(render,700);
    }
    function render(){if(!current||!key)return;
      if(!frame){frame=document.createElement('iframe');frame.title='Google Maps accommodation location';frame.loading='lazy';frame.referrerPolicy='no-referrer-when-downgrade';frame.allowFullscreen=true;box.append(frame);}
      const url=new URL('https://www.google.com/maps/embed/v1/place');url.searchParams.set('key',key);url.searchParams.set('q',current);frame.src=url;box.hidden=false;preview.textContent='Refresh map';
    }
    preview.addEventListener('click',render);
    config.then(data=>{if(data.enabled&&/^[A-Za-z0-9_-]{20,100}$/.test(data.key)){key=data.key;preview.hidden=false;}});
    const api={update};instances.set(host,api);update(initial);return api;
  }
  window.uninestMaps={mount,address};
  const form=document.getElementById('propertyForm');
  if(form){
    const host=document.getElementById('listingAddressMap');
    if(host){const map=mount(host,'');const sync=()=>map.update(address(['location','suburb','city'].map(k=>form.elements[k]?.value)));
      form.addEventListener('input',e=>{if(['location','suburb','city'].includes(e.target.name))sync();});
      form.addEventListener('listing:loaded',sync);form.addEventListener('reset',()=>setTimeout(sync,0));sync();
    }
  }
  function scan(){document.querySelectorAll('[data-listing-map-address]').forEach(host=>mount(host,host.dataset.listingMapAddress));}
  const observer=new MutationObserver(scan);observer.observe(document.body,{childList:true,subtree:true});scan();
})();
