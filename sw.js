const C='daftar-v11';
const FILES=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','icons/maskable-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(FILES)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C&&k!=='daftar-data').map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;
 e.respondWith(caches.match(e.request,{ignoreSearch:true}).then(hit=>{const net=fetch(e.request).then(r=>{if(r&&r.ok&&new URL(e.request.url).origin===location.origin){const cp=r.clone();caches.open(C).then(c=>c.put(e.request,cp))}return r}).catch(()=>hit);return hit||net}))});
/* daily reminders (best effort: Android decides the exact time) */
const ymd=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
async function daily(force){
 const c=await caches.open('daftar-data');const r=await c.match('/__daftar_rem');if(!r)return;const D=await r.json();
 const now=new Date(),today=ymd(now),wk=ymd(new Date(now-7*864e5));
 const last=await c.match('/__daftar_notified');if(!force&&last&&(await last.text())===today)return;
 const lines=(D.items||[]).filter(x=>x.from<=today&&x.d>=wk).sort((a,b)=>a.d.localeCompare(b.d)).map(x=>x.t+' — '+(x.d===today?'النهارده':x.d<today?'متأخر':'يوم '+Number(x.d.slice(8))));
 const owe=D.owe||[];if(owe.length&&now.getDay()===6)lines.push(...owe);
 if(!lines.length)return;
 await self.registration.showNotification('دفتري: عندك '+lines.length+' تنبيه',{body:lines.slice(0,6).join('\n'),icon:'icons/icon-192.png',badge:'icons/icon-192.png',tag:'daftar-daily',dir:'rtl',lang:'ar'});
 await c.put('/__daftar_notified',new Response(today))}
self.addEventListener('periodicsync',e=>{if(e.tag==='daftar-rem')e.waitUntil(daily())});
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(cs=>cs.length?cs[0].focus():self.clients.openWindow('./')))});
self.addEventListener('message',e=>{if(e.data==='daftar-test')e.waitUntil(daily(true))});
