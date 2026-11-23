const C='daftar-v15';
const FILES=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','icons/maskable-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(FILES)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C&&k!=='daftar-data').map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;
 e.respondWith(caches.match(e.request,{ignoreSearch:true}).then(hit=>{const net=fetch(e.request).then(r=>{if(r&&r.ok&&new URL(e.request.url).origin===location.origin){const cp=r.clone();caches.open(C).then(c=>c.put(e.request,cp))}return r}).catch(()=>hit);return hit||net}))});
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
