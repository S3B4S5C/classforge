import assert from 'node:assert/strict';
import test from 'node:test';
import { VirtualTimeScheduler } from 'rxjs';
import { assistantReconciliationTicks } from './assistant-reconciliation.ts';

test('periodic catch-up runs every five seconds only while connected and stops on unsubscribe', () => {
  const scheduler = new VirtualTimeScheduler(undefined, 20_000);
  let connected = true;
  const recoveredAt: number[] = [];
  const subscription = assistantReconciliationTicks(() => connected, scheduler).subscribe(() => recoveredAt.push(scheduler.now()));
  scheduler.schedule(() => { connected = false; }, 6000);
  scheduler.schedule(() => { connected = true; }, 11000);
  scheduler.schedule(() => subscription.unsubscribe(), 16000);
  scheduler.flush();
  assert.deepEqual(recoveredAt, [5000, 15000]);
});
