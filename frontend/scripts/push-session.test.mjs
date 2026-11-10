import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';
import * as enrollment from '../src/app/features/notifications/push-enrollment.ts';

test('account switch clears worker ownership before a failing network status check',async()=>{
  const values=new Map([['classforge.push.owner','account-a']]);
  const localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
  const signal=value=>Object.assign(()=>value,{set:next=>{value=next;}});
  const source=await readFile(new URL('../src/app/features/notifications/push-device.service.ts',import.meta.url),'utf8');
  const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
  const exports={};
  vm.runInNewContext(compiled,{exports,localStorage,window:{isSecureContext:true,PushManager:{},Notification:{},addEventListener(){}},navigator:{serviceWorker:{}},Notification:{permission:'granted'},require:name=>{
    if(name==='@angular/core')return {signal,Injectable:()=>value=>value};
    if(name==='rxjs')return {};
    if(name==='./push-enrollment')return enrollment;
    if(name==='./push-registration')return {};
    throw Error('Unexpected dependency '+name);
  }});
  let requests=0,binding='account-a',finishClear;
  const service=new exports.PushDeviceService({get(){requests++;throw Error('offline');}});
  service.flow.setOwner('account-a');
  service.bind=async value=>{assert.equal(value,null);await new Promise(resolve=>{finishClear=resolve;});binding=value;};
  service.setUserId('account-b');
  assert.equal(typeof finishClear,'function');assert.equal(requests,0);
  finishClear();await new Promise(resolve=>setImmediate(resolve));
  assert.equal(binding,null);assert.equal(requests,1);assert.equal(service.active(),false);
});
