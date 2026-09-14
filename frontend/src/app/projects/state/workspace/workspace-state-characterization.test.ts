import assert from 'node:assert/strict';
import test from 'node:test';

import type { Project, ProjectDocument } from '../../model/project.ts';
import { commandMetadata } from '../../commands/uml-command.ts';
import { WorkspaceDocumentSession } from './workspace-document-session.ts';
import { WorkspaceCollaborationState } from './workspace-collaboration-state.ts';

function document(): ProjectDocument {
  return {
    schemaVersion: '1.0',
    umlModel: { classes: [], relationships: [] },
    layout: { nodes: {} },
  };
}

function project(): Project {
  return {
    id: 'p1',
    name: 'Demo',
    accessRole: 'OWNER',
    revision: 4,
    document: document(),
    createdAt: '2026-09-14T00:00:00Z',
    updatedAt: '2026-09-14T00:00:00Z',
  };
}

test('document session preserves local history separately from confirmed revision', () => {
  const session = new WorkspaceDocumentSession();
  session.loadAuthoritative(project(), true);
  assert.equal(session.confirmedRevision(), 4);
  assert.equal(session.dirty(), false);

  session.dispatch({
    ...commandMetadata(),
    type: 'CREATE_CLASS',
    umlClass: { id: 'c1', name: 'Cliente', attributes: [] },
    layout: { x: 10, y: 10, width: 220, height: 100 },
  });
  assert.equal(session.dirty(), true);
  assert.equal(session.canUndo(), true);
  assert.equal(session.confirmedRevision(), 4);

  session.undoCommand();
  assert.deepEqual(session.draft(), document());
});

test('document session applies authoritative operations and can replace local editor history', () => {
  const session = new WorkspaceDocumentSession();
  session.loadAuthoritative(project(), true);
  const command = {
    ...commandMetadata(),
    type: 'CREATE_CLASS' as const,
    umlClass: { id: 'c1', name: 'Cliente', attributes: [] },
    layout: { x: 10, y: 10, width: 220, height: 100 },
  };
  const confirmed = session.applyConfirmed({
    type: 'OPERATION_APPLIED',
    operationId: 'op1',
    projectId: 'p1',
    clientId: 'client',
    revision: 5,
    command,
    actor: { id: 'u1', displayName: 'User' },
    appliedAt: '2026-09-14T00:01:00Z',
  });

  assert.equal(session.confirmedRevision(), 5);
  assert.equal(confirmed.umlModel.classes.length, 1);
  session.installConfirmedIntoEditor();
  assert.equal(session.draft().umlModel.classes.length, 1);
  assert.equal(session.canUndo(), false);
});

test('collaboration state tracks pending operations, buffering and resync generations', () => {
  const state = new WorkspaceCollaborationState();
  const operation = {
    operationId: 'op1',
    projectId: 'p1',
    clientId: 'c1',
    baseRevision: 4,
    command: {
      ...commandMetadata(),
      type: 'CREATE_CLASS' as const,
      umlClass: { id: 'c1', name: 'Cliente', attributes: [] },
      layout: { x: 1, y: 1, width: 220, height: 100 },
    },
  };
  state.addPending(operation);
  assert.equal(state.pendingCount(), 1);
  assert.equal(state.hasPending('op1'), true);
  state.removePending('op1');
  assert.equal(state.pendingCount(), 0);

  const first = state.nextResyncGeneration();
  const second = state.nextResyncGeneration();
  assert.equal(second, first + 1);
  state.requireResyncOnReconnect();
  assert.equal(state.resyncRequiredOnReconnect(), true);
  state.reset();
  assert.equal(state.resyncRequiredOnReconnect(), false);
  assert.ok(state.currentResyncGeneration() > second);
});

test('collaboration state classifies stale, contiguous and gap revisions deterministically', () => {
  const state = new WorkspaceCollaborationState();
  assert.equal(state.classifyRevision(4, 4), 'stale');
  assert.equal(state.classifyRevision(4, 3), 'stale');
  assert.equal(state.classifyRevision(4, 5), 'next');
  assert.equal(state.classifyRevision(4, 6), 'gap');
});

test('disconnect marker requires exactly one reconnect resync until cleared', () => {
  const state = new WorkspaceCollaborationState();
  assert.equal(state.resyncRequiredOnReconnect(), false);
  state.requireResyncOnReconnect();
  assert.equal(state.resyncRequiredOnReconnect(), true);
  state.clearReconnectResyncRequirement();
  assert.equal(state.resyncRequiredOnReconnect(), false);
});
