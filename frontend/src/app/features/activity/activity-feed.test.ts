import assert from 'node:assert/strict';
import test from 'node:test';
import { ActivityFeed } from './activity-feed.ts';
import type { Paged, ProjectActivity } from './activity-model.ts';
function item(revision: number, projectId = 'p'): ProjectActivity {
  return { id: `a${revision}`, projectId, operationId: `o${revision}`, actorUserId: 'u', actorDisplayName: 'Marco',
    revisionBefore: revision - 1, revisionAfter: revision, operationType: 'CREATE_CLASS', source: 'MANUAL',
    description: 'creó Cliente', payloadJson: '{}', sessionId: 's', createdAt: '2026-11-08T00:00:00Z' };
}
function page(items: ProjectActivity[], totalElements = items.length): Paged<ProjectActivity> {
  return { items, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}
test('activity pagination uses exclusive revisions and merges without duplicates', () => {
  const feed = new ActivityFeed(); const token = feed.reset('p');
  feed.merge(token, page([item(5), item(4)], 5)); assert.equal(feed.olderFilters().beforeRevision, 4);
  feed.merge(token, page([item(3), item(2)], 3), true);
  feed.merge(token, page([item(2), item(1)]), true);
  assert.deepEqual(feed.items.map(item => item.revisionAfter), [5, 4, 3, 2, 1]);
  assert.equal(feed.total, 5); assert.equal(feed.hasMore, false);
});
test('late responses from another project or earlier filter cannot overwrite the current feed', () => {
  const feed = new ActivityFeed(); const first = feed.reset('p'); const second = feed.reset('q', { actorId: 'v' });
  assert.equal(feed.merge(first, page([item(3)])), false);
  feed.merge(second, page([item(1, 'q'), item(5, 'p')]));
  assert.deepEqual(feed.items.map(item => item.projectId), ['q']);
  const filtered = feed.reset('q', { afterRevision: 1 });
  assert.equal(feed.merge(second, page([item(4, 'q')])), false); assert.equal(feed.accepts(filtered), true);
});
test('head refresh keeps already loaded older activity and updates the total', () => {
  const feed = new ActivityFeed(); const token = feed.reset('p');
  feed.merge(token, page([item(3), item(2)], 3)); feed.merge(token, page([item(1)]), true);
  feed.refresh(token, page([item(4), item(3)], 4));
  assert.deepEqual(feed.items.map(item => item.revisionAfter), [4, 3, 2, 1]); assert.equal(feed.hasMore, false);
});
test('paging retains every selected filter and never widens an upper revision bound', () => {
  const feed = new ActivityFeed(); const filters = { actorId: 'u', operationType: 'BATCH', afterRevision: 2, beforeRevision: 10, sessionId: 's' };
  const token = feed.reset('p', filters); feed.merge(token, page([item(8), item(7)], 3));
  assert.deepEqual(feed.olderFilters(), { ...filters, beforeRevision: 7 }); assert.equal(filters.beforeRevision, 10);
});
