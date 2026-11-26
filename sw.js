const C='daftar-v62';
const FILES=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','icons/maskable-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>Promise.all(FILES.map(f=>fetch(f,{cache:'no-store'}).then(r=>r.ok&&c.put(f,r)).catch(()=>{})))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C&&k!=='daftar-data').map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
const timeout=(p,ms)=>new Promise((ok,no)=>{const t=setTimeout(()=>no(new Error('timeout')),ms);p.then(v=>{clearTimeout(t);ok(v)},er=>{clearTimeout(t);no(er)})});
self.addEventListener('fetch',e=>{const req=e.request;if(req.method!=='GET')return;
 const url=new URL(req.url);if(url.origin!==location.origin)return; /* never touch fonts/cdn: the browser handles them */
 if(req.mode==='navigate'&&!/\/(index\.html)?$/.test(url.pathname))return; /* other pages (privacy, download…) go to the network as usual */
 if(req.mode==='navigate'){ /* page: open instantly from the saved copy, refresh it in the background (new version shows next time) */
  const upd=fetch(req,{cache:'no-store'}).then(r=>{if(r&&r.ok){const cp=r.clone();caches.open(C).then(c=>c.put('index.html',cp))}return r});
  e.waitUntil(upd.catch(()=>{}));
  e.respondWith(caches.match('index.html').then(h=>h||caches.match('./')).then(h=>h||timeout(upd,8000)).catch(()=>upd));return}
 e.respondWith(caches.match(req,{ignoreSearch:true}).then(hit=>{const net=fetch(req).then(r=>{if(r&&r.ok){const cp=r.clone();caches.open(C).then(c=>c.put(req,cp))}return r});return hit?(net.catch(()=>{}),hit):net}))});
/* reminders: notify only on the days the user chose before each date (once per item per chosen day) */
const ymd=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
const minus=(s,n)=>{const d=new Date(s+'T12:00');d.setDate(d.getDate()-n);return ymd(d)};
async function check(){
 const c=await caches.open('daftar-data');const r=await c.match('/__daftar_rem');if(!r)return;const D=await r.json();
 const sr=await c.match('/__daftar_sent');let sent=sr?await sr.json():[];
 const now=new Date(),today=ymd(now),days=(D.days&&D.days.length?D.days:[0]).slice().sort((a,b)=>a-b);
 const lines=[];
 for(const it of D.items||[]){if(it.d<today)continue;
  const hit=days.find(k=>minus(it.d,k)<=today);if(hit===undefined)continue;
  const key=it.id+'|'+hit;if(sent.includes(key))continue;
  days.filter(k=>k>=hit).forEach(k=>sent.push(it.id+'|'+k));
  const n=Math.round((new Date(it.d+'T12:00')-new Date(today+'T12:00'))/864e5);
  lines.push(it.t+' — '+(n===0?'النهارده':n===1?'بكرة':'بعد '+n+' يوم'))}
 if((D.owe||[]).length&&now.getDay()===6&&!sent.includes('owe|'+today)){sent.push('owe|'+today);lines.push(...D.owe)}
 if(!lines.length)return;
 await c.put('/__daftar_sent',new Response(JSON.stringify(sent.slice(-400))));
 await self.registration.showNotification(lines.length===1?'دفتري':'دفتري: عندك '+lines.length+' تنبيه',{body:lines.slice(0,6).join('\n'),icon:'icons/icon-192.png',badge:'icons/icon-192.png',tag:'daftar-'+today,dir:'rtl',lang:'ar'})}
self.addEventListener('periodicsync',e=>{if(e.tag==='daftar-rem')e.waitUntil(check())});
self.addEventListener('message',e=>{if(e.data==='daftar-check'||e.data==='daftar-test')e.waitUntil(check())});
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(cs=>cs.length?cs[0].focus():self.clients.openWindow('./')))});
