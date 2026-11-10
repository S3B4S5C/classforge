import {notificationView,safeNotificationUrl} from './assets/push/worker-core.js';

// No fetch handler: authenticated HTML/API and JWTs are never cached here.
const database=new Promise((resolve,reject)=>{
  const request=indexedDB.open('classforge-push',1);
  request.onupgradeneeded=()=>request.result.createObjectStore('state');
  request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);
});
async function owner(){
  const db=await database;
  return new Promise((resolve,reject)=>{const r=db.transaction('state').objectStore('state').get('owner');r.onsuccess=()=>resolve(r.result??null);r.onerror=()=>reject(r.error);});
}
async function bindOwner(value){
  const db=await database;
  return new Promise((resolve,reject)=>{const tx=db.transaction('state','readwrite');tx.objectStore('state').put(value,'owner');tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);});
}
let messages=Promise.resolve();
self.addEventListener('install',event=>event.waitUntil(self.skipWaiting()));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('message',event=>{
  if(event.data?.type!=='CLASSFORGE_PUSH_OWNER')return;
  let origin;try{origin=new URL(event.source?.url).origin;}catch{return;}
  if(origin!==self.location.origin)return;
  const value=typeof event.data.ownerId==='string'?event.data.ownerId:null;
  event.waitUntil(messages=messages.catch(()=>{}).then(async()=>{
    try {
      const previous=await owner();await bindOwner(value);
      if(!value||previous!==value)for(const n of await self.registration.getNotifications())if(n.tag.startsWith('classforge:'))n.close();
      event.ports[0]?.postMessage({ok:true});
    }catch{event.ports[0]?.postMessage({ok:false});}
  }));
});
self.addEventListener('push',event=>event.waitUntil(messages=messages.catch(()=>{}).then(async()=>{
  let payload;try{payload=event.data?.json();}catch{return;}
  const view=notificationView(payload,await owner(),self.location.origin);
  if(!view)return;
  await self.registration.showNotification(view.title,view.options);
  for(const client of await self.clients.matchAll({type:'window',includeUncontrolled:true}))client.postMessage({type:'CLASSFORGE_PUSH',ownerId:payload.ownerId});
})));
self.addEventListener('notificationclick',event=>{
  event.notification.close();
  event.waitUntil((async()=>{
    await messages;const current=await owner();const data=event.notification.data;
    const path=current&&data?.ownerId===current?safeNotificationUrl(data.url,self.location.origin):'/projects';
    const absolute=new URL(path,self.location.origin).href;
    for(const client of await self.clients.matchAll({type:'window',includeUncontrolled:true})){
      if(new URL(client.url).origin!==self.location.origin)continue;
      try {await client.navigate(absolute);await client.focus();return;}catch{ }
    }
    await self.clients.openWindow(absolute);
  })());
});
