import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {notificationView,safeNotificationUrl} from '../public/assets/push/worker-core.js';
const origin='https://classforge.example';
const owner='24b0c067-f572-45d7-9f40-3a98d14d6c64';
test('worker discards another account and malformed payloads',()=>{
  const payload={ownerId:owner,body:'Hay actividad',url:'/projects',tag:'classforge:test'};
  assert.equal(notificationView(payload,'another',origin),null);
  assert.equal(notificationView(null,owner,origin),null);
  assert.equal(notificationView(payload,null,origin),null);
  assert.equal(notificationView(payload,owner,origin).title,'ClassForge');
});
test('notification clicks never navigate outside authenticated project routes',()=>{
  assert.equal(safeNotificationUrl('/projects/'+owner+'/activity?sessionId='+owner,origin),'/projects/'+owner+'/activity?sessionId='+owner);
  for(const url of ['https://evil.test/','//evil.test','javascript:alert(1)','/api/auth/login','/projects/../../evil','/projects/'+owner+'/activity?redirect=https://evil.test'])assert.equal(safeNotificationUrl(url,origin),'/projects');
});
test('worker bounds text and reuses server notification ID for visible dedup',()=>{
  const view=notificationView({ownerId:owner,body:'x'.repeat(1000),url:'/projects',tag:'classforge:'+owner},owner,origin);
  assert.equal(view.options.body.length,180);assert.equal(view.options.tag,'classforge:'+owner);
});
test('logout acknowledges only after an in-flight notification is closed',async()=>{
  const handlers={},visible=[],order=[];let storedOwner=owner,finishShowing,started;
  const showing=new Promise(resolve=>{started=resolve;});
  const db={transaction:()=>{
    const tx={objectStore:()=>({
      get:()=>{const request={};queueMicrotask(()=>{request.result=storedOwner;request.onsuccess();});return request;},
      put:value=>{storedOwner=value;queueMicrotask(()=>tx.oncomplete());},
    })};return tx;
  }};
  const worker={location:{origin},addEventListener:(type,handler)=>{handlers[type]=handler;},
    registration:{showNotification:async(_title,options)=>{started();await new Promise(resolve=>{finishShowing=resolve;});visible.push({tag:options.tag,close(){order.push('close');}});order.push('show');},getNotifications:async()=>visible},
    clients:{matchAll:async()=>[]},
  };
  const indexedDB={open:()=>{const request={};queueMicrotask(()=>{request.result=db;request.onsuccess();});return request;}};
  const source=(await readFile(new URL('../public/push-worker.js',import.meta.url),'utf8')).replace(/^import[^\n]+\n/,'');
  vm.runInNewContext(source,{self:worker,indexedDB,URL,notificationView,safeNotificationUrl});
  let pushDone,logoutDone;
  handlers.push({data:{json:()=>({ownerId:owner,body:'Actividad',url:'/projects',tag:'classforge:test'})},waitUntil:p=>{pushDone=p;}});
  await showing;
  handlers.message({data:{type:'CLASSFORGE_PUSH_OWNER',ownerId:null},source:{url:origin+'/projects'},ports:[{postMessage:()=>order.push('ack')}],waitUntil:p=>{logoutDone=p;}});
  await new Promise(resolve=>setImmediate(resolve));finishShowing();
  await Promise.all([pushDone,logoutDone]);
  assert.deepEqual(order,['show','close','ack']);
});
test('notification click focuses the existing window before awaiting navigation',async()=>{
  const handlers={},order=[];
  const indexedDB={open:()=>{const request={};queueMicrotask(()=>{request.result={transaction:()=>({objectStore:()=>({get:()=>{const read={};queueMicrotask(()=>{read.result=owner;read.onsuccess();});return read;}})})};request.onsuccess();});return request;}};
  const client={url:origin+'/projects',visibilityState:'visible',focus:async()=>{order.push('focus');},navigate:async()=>{order.push('navigate');return client;}};
  const self={location:{origin},addEventListener:(type,handler)=>{handlers[type]=handler;},clients:{matchAll:async()=>[client],openWindow:async()=>order.push('open')}};
  const source=(await readFile(new URL('../public/push-worker.js',import.meta.url),'utf8')).replace(/^import[^\n]+\n/,'');
  vm.runInNewContext(source,{self,indexedDB,URL,notificationView,safeNotificationUrl});
  let done;handlers.notificationclick({notification:{close(){},data:{ownerId:owner,url:'/projects/'+owner+'/activity'}},waitUntil:p=>{done=p;}});
  await done;assert.deepEqual(order,['focus','navigate']);
});
for(const failure of ['rejected','null','hidden'])test(`notification click falls back when client is ${failure}`,async()=>{
  const handlers={},order=[];
  const indexedDB={open:()=>{const request={};queueMicrotask(()=>{request.result={transaction:()=>({objectStore:()=>({get:()=>{const read={};queueMicrotask(()=>{read.result=owner;read.onsuccess();});return read;}})})};request.onsuccess();});return request;}};
  const client={url:origin+'/projects',visibilityState:failure==='hidden'?'hidden':'visible',focus:async()=>{order.push('focus');},navigate:async()=>{order.push('navigate');if(failure==='rejected')throw Error('closed');return null;}};
  const self={location:{origin},addEventListener:(type,handler)=>{handlers[type]=handler;},clients:{matchAll:async()=>[client],openWindow:async()=>order.push('open')}};
  const source=(await readFile(new URL('../public/push-worker.js',import.meta.url),'utf8')).replace(/^import[^\n]+\n/,'');
  vm.runInNewContext(source,{self,indexedDB,URL,notificationView,safeNotificationUrl,console:{warn(){}}});
  let done;handlers.notificationclick({notification:{close(){},data:{ownerId:owner,url:'/projects/'+owner+'/activity'}},waitUntil:p=>{done=p;}});
  await done;assert.deepEqual(order,failure==='hidden'?['open']:['focus','navigate','open']);
});
