import test from 'node:test';
import assert from 'node:assert/strict';
import { PushEnrollment, ownerBindingMessage } from './push-enrollment.ts';

test('logout while permission is pending never registers a device for the next user',async()=>{
  const flow=new PushEnrollment(); flow.setOwner('owner-a');
  let grant!:(value:string)=>void; let registered=0,subscribed=0;
  const result=flow.enroll({permission:()=>new Promise<string>(r=>grant=r),subscribe:async()=>{subscribed++;return {unsubscribe:async()=>true};},register:async()=>{registered++;},bind:async()=>{},currentOwner:()=>flow.owner()});
  flow.setOwner('owner-b'); grant('granted');
  await assert.rejects(result,/sesión/);assert.equal(registered,0);assert.equal(subscribed,0);
});
test('session changed during subscription revokes stale endpoint and never registers it',async()=>{
  const flow=new PushEnrollment();flow.setOwner('a');let removed=0,registered=0;
  await assert.rejects(flow.enroll({permission:async()=>'granted',subscribe:async()=>{flow.setOwner('b');return {unsubscribe:async()=>{removed++;return true;}};},register:async()=>{registered++;},bind:async()=>{},currentOwner:()=>flow.owner()}),/sesión/);
  assert.equal(removed,1);assert.equal(registered,0);
});
test('denied permission and an account changed in another tab cannot enroll',async()=>{
  const flow=new PushEnrollment();flow.setOwner('a');let calls=0;
  const ports={permission:async()=>'denied',subscribe:async()=>{calls++;return {unsubscribe:async()=>true};},register:async()=>{},bind:async()=>{},currentOwner:()=> 'a'};
  await assert.rejects(flow.enroll(ports),/permiso/);assert.equal(calls,0);
  await assert.rejects(flow.enroll({...ports,permission:async()=>'granted',currentOwner:()=> 'b'}),/sesión/);assert.equal(calls,0);
});
test('success registers before persisting owner binding',async()=>{
  const flow=new PushEnrollment();flow.setOwner('a');let steps:string[]=[];
  await flow.enroll({permission:async()=>'granted',subscribe:async()=>({unsubscribe:async()=>true}),register:async()=>{steps.push('server');},bind:async()=>{steps.push('worker');},currentOwner:()=> 'a'});
  assert.deepEqual(steps,['server','worker']);
});
test('deactivation clears only its own worker binding and cannot clear a new account',()=>{
  assert.deepEqual(ownerBindingMessage(null,'a','a'),{type:'CLASSFORGE_PUSH_OWNER',ownerId:null});
  assert.equal(ownerBindingMessage(null,'a','b'),null);
  assert.equal(ownerBindingMessage('a','a','b'),null);
});
