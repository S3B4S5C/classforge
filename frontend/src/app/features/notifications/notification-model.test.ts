import assert from 'node:assert/strict';
import test from 'node:test';
import { mergeNotifications, type ProjectNotification } from './notification-model.ts';
function item(id: string, recipient = 'u', createdAt = '2026-11-08T00:00:00Z'): ProjectNotification {
  return { id, recipientUserId: recipient, projectId: 'p', type: 'PROJECT_ACTIVITY', title: 'Actividad', message: 'Marco creó Cliente',
    activityId: 'a', sessionId: 's', read: false, createdAt, readAt: null };
}
test('HTTP and realtime notifications reconcile by ID with updated read state', () => {
  const original = item('a'); const read = { ...original, read: true, readAt: '2026-11-08T00:01:00Z' };
  assert.deepEqual(mergeNotifications([original], [read, item('b')], 'u').map(item => [item.id, item.read]), [['b', false], ['a', true]]);
});
test('switching authenticated users removes previous inbox data and ignores foreign events', () => {
  assert.deepEqual(mergeNotifications([item('old', 'old-user')], [item('wrong', 'other'), item('own')], 'u').map(item => item.id), ['own']);
});
test('notification ordering preserves submillisecond timestamps after reload', () => {
  const list = mergeNotifications([], [item('z', 'u', '2026-11-08T00:00:00.000001Z'), item('a', 'u', '2026-11-08T00:00:00.000002Z'), item('b')], 'u');
  assert.deepEqual(list.map(item => item.id), ['a', 'z', 'b']);
});
