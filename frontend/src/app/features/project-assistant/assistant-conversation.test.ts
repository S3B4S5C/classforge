import assert from 'node:assert/strict';
import test from 'node:test';
import { AssistantConversation } from './assistant-conversation.ts';
import type { ProjectAiMessage } from './project-assistant-model.ts';

function message(sequence: number, projectId = 'p1'): ProjectAiMessage {
  return { id: `m${sequence}`, sequence, projectId, requestId: `r${sequence}`,
    authorUserId: 'u1', authorDisplayName: 'Ana', role: 'USER', type: 'ASK',
    content: `Mensaje ${sequence}`, projectRevision: 4, createdAt: '2026-11-07T00:00:00Z', review: null, plan: null };
}

test('HTTP and realtime messages merge by ID in authoritative sequence order', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.merge(token, [message(3), message(1)]);
  state.merge(token, [message(2), { ...message(3), content: 'Respuesta completa' }]);
  assert.deepEqual(state.messages.map(item => item.sequence), [1, 2, 3]);
  assert.equal(state.messages[2].content, 'Respuesta completa');
});

test('project switch and returning to same project reject all earlier in-flight generations', () => {
  const state = new AssistantConversation();
  const previous = state.open('p1');
  state.merge(previous, [message(1)]);
  const other = state.open('p2');
  state.merge(previous, [message(2)]);
  state.merge(other, [message(3, 'p1'), message(4, 'p2')]);
  assert.deepEqual(state.messages.map(item => item.sequence), [4]);
  const current = state.open('p1');
  assert.equal(state.accepts(previous), false);
  assert.equal(state.accepts(current), true);
  assert.deepEqual(state.messages, []);
});

test('older pagination keeps a stable cursor despite realtime and reconciliation', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.page(token, { messages: [message(40), message(41)], hasMore: true });
  state.merge(token, [message(42), message(100)]);
  assert.equal(state.before, 40);
  state.page(token, { messages: [message(38), message(39), message(40)], hasMore: true });
  assert.equal(state.before, 38);
  state.page(token, { messages: [message(1)], hasMore: false });
  assert.equal(state.hasMore, false);
  assert.equal(state.before, 1);
});

test('failed request retries preserve request ID and payload until resolved', () => {
  const state = new AssistantConversation();
  state.open('p1');
  const request = state.prepare('¿Qué falta?', 'REVIEW', 'request-one');
  assert.deepEqual(state.retry(), request);
  state.merge(state.token(), [{ ...message(2), role: 'ASSISTANT', requestId: 'request-one' }]);
  assert.equal(state.retry(), null);
  state.prepare('Nueva consulta', 'ASK', 'request-two');
  state.open('p2');
  assert.equal(state.retry(), null);
});

test('reconnection traverses recent pages until it overlaps the last known sequence', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.merge(token, [message(10)]);
  assert.equal(state.reconcileBefore({ messages: [message(90), message(100)], hasMore: true }, 10), 90);
  assert.equal(state.reconcileBefore({ messages: [message(10), message(89)], hasMore: true }, 10), null);
  assert.equal(state.reconcileBefore({ messages: [], hasMore: true }, 10), null);
});

test('a late latest-page response cannot reopen exhausted older pagination', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.page(token, { messages: [message(40)], hasMore: true });
  state.page(token, { messages: [message(1)], hasMore: false });
  state.page(token, { messages: [message(40), message(41)], hasMore: true });
  assert.equal(state.hasMore, false);
  assert.equal(state.before, 1);
});

test('reconnect checkpoint ignores newer realtime messages until history is reconciled', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.merge(token, [message(100)]);
  state.page(token, { messages: [message(9), message(10)], hasMore: true });
  assert.equal(state.syncedThrough, 10);
  state.merge(token, [message(101)]);
  state.page(token, { messages: [message(1)], hasMore: false });
  assert.equal(state.syncedThrough, 10);
  state.reconciled(token, 100);
  assert.equal(state.syncedThrough, 100);
  state.open('p2');
  state.reconciled(token, 101);
  assert.equal(state.syncedThrough, undefined);
});

test('empty initial history anchors catch-up at zero rather than skipping new messages', () => {
  const state = new AssistantConversation();
  const token = state.open('p1');
  state.page(token, { messages: [], hasMore: false });
  state.merge(token, [message(100)]);
  assert.equal(state.syncedThrough, 0);
  assert.equal(state.reconcileBefore({ messages: [message(51), message(100)], hasMore: true }, state.syncedThrough!), 51);
});
