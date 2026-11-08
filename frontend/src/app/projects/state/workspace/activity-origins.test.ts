import assert from 'node:assert/strict';
import test from 'node:test';
import { WorkspaceDocumentSession } from './workspace-document-session.ts';
import { commandMetadata } from '../../commands/uml-command.ts';
import type { Project } from '../../model/project.ts';

function project(): Project {
  return { id: 'p', name: 'Proyecto', accessRole: 'OWNER', revision: 0,
    document: { schemaVersion: '1.0', umlModel: { classes: [], relationships: [] }, layout: { nodes: {} } },
    createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T00:00:00Z' };
}
function create(name: string) {
  return { ...commandMetadata(), type: 'CREATE_CLASS' as const,
    umlClass: { id: name, name, attributes: [] }, layout: { x: 10, y: 10, width: 260, height: 160 } };
}

test('local document saves retain each supported origin and reset after persistence', () => {
  for (const origin of ['MANUAL', 'ASSISTANT_TEXT', 'ASSISTANT_VOICE', 'VISION', 'XMI', 'SYSTEM'] as const) {
    const session = new WorkspaceDocumentSession(); session.loadAuthoritative(project(), true);
    session.dispatch(create('Cliente'), origin);
    assert.equal(session.activitySource(), origin);
    session.markSaved(); assert.equal(session.activitySource(), 'MANUAL');
  }
});

test('mixed-origin document saves are identified as system consolidations', () => {
  const session = new WorkspaceDocumentSession(); session.loadAuthoritative(project(), true);
  session.dispatch(create('Cliente'), 'ASSISTANT_TEXT');
  session.dispatch(create('Pedido'), 'MANUAL');
  assert.equal(session.activitySource(), 'SYSTEM');
  session.loadAuthoritative(project(), true); assert.equal(session.activitySource(), 'MANUAL');
});

test('undo and redo represent manual user actions and confirmed synchronization resets origins', () => {
  const session = new WorkspaceDocumentSession(); session.loadAuthoritative(project(), true);
  session.dispatch(create('Cliente'), 'VISION');
  session.undoCommand(); session.redoCommand(); assert.equal(session.activitySource(), 'SYSTEM');
  session.finishOwnSynchronization(); assert.equal(session.activitySource(), 'MANUAL');
  session.dispatch(create('Pedido'), 'ASSISTANT_VOICE'); session.reset();
  assert.equal(session.activitySource(), 'MANUAL');
});
