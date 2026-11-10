import test from 'node:test';
import assert from 'node:assert/strict';
import {resolvePushRegistration} from './push-registration.ts';
test('logout can reach an existing worker offline without registering over the network',async()=>{
  const existing={active:{}};let network=0;
  const port={getRegistration:async()=>existing,register:async()=>{network++;throw Error('offline');},ready:Promise.resolve(existing)};
  assert.equal(await resolvePushRegistration(port,false),existing);assert.equal(network,0);
});
test('checking state without a worker does not create a subscription infrastructure',async()=>{
  let network=0;
  assert.equal(await resolvePushRegistration({getRegistration:async()=>undefined,register:async()=>{network++;return {};},ready:Promise.resolve({})},false),undefined);
  assert.equal(network,0);
});
