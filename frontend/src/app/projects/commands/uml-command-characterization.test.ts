import assert from 'node:assert/strict';
import test from 'node:test';

import type { ProjectDocument } from '../model/project.ts';
import { commandMetadata } from './uml-command.ts';
import type { UmlCommand } from './uml-command.ts';
import { UmlCommandBus } from './uml-command-bus.ts';
import { UmlCommandError } from './uml-command-error.ts';
import { UmlCommandExecutor } from './uml-command-executor.ts';
import { UmlCommandInverter } from './uml-command-inverter.ts';

function emptyDocument(): ProjectDocument {
  return {
    schemaVersion: '1.0',
    umlModel: { classes: [], relationships: [] },
    layout: { nodes: {} },
  };
}

function createClass(id = 'class-1', name = 'Cliente'): UmlCommand {
  return {
    ...commandMetadata(),
    type: 'CREATE_CLASS',
    umlClass: { id, name, attributes: [] },
    layout: { x: 10, y: 20, width: 220, height: 100 },
  };
}

test('executor never mutates the input document', () => {
  const before = emptyDocument();
  const snapshot = structuredClone(before);
  const after = new UmlCommandExecutor().execute(before, createClass());

  assert.deepEqual(before, snapshot);
  assert.equal(after.umlModel.classes.length, 1);
  assert.equal(after.umlModel.classes[0].name, 'Cliente');
  assert.ok(after.layout.nodes['class-1']);
});

test('inverter restores create, rename and move operations', () => {
  const executor = new UmlCommandExecutor();
  const inverter = new UmlCommandInverter();
  const created = executor.execute(emptyDocument(), createClass());

  const rename: UmlCommand = {
    ...commandMetadata(),
    type: 'RENAME_CLASS',
    classId: 'class-1',
    name: 'Persona',
  };
  const renamed = executor.execute(created, rename);
  const renameInverse = inverter.invert(created, rename);
  assert.deepEqual(executor.execute(renamed, renameInverse), created);

  const move: UmlCommand = {
    ...commandMetadata(),
    type: 'MOVE_CLASS',
    classId: 'class-1',
    layout: { x: 400, y: 300, width: 260, height: 120 },
  };
  const moved = executor.execute(created, move);
  const moveInverse = inverter.invert(created, move);
  assert.deepEqual(executor.execute(moved, moveInverse), created);
});

test('command bus tracks dirty state and reversible history', () => {
  const bus = new UmlCommandBus();
  const original = emptyDocument();
  bus.load(original);

  assert.equal(bus.isDirty(), false);
  assert.equal(bus.canUndo(), false);

  assert.equal(bus.dispatch(createClass()), true);
  assert.equal(bus.isDirty(), true);
  assert.equal(bus.undoDepth(), 1);
  assert.equal(bus.canUndo(), true);

  assert.ok(bus.undoCommand());
  assert.deepEqual(bus.document(), original);
  assert.equal(bus.canRedo(), true);

  assert.ok(bus.redoCommand());
  assert.equal(bus.document().umlModel.classes.length, 1);
  assert.equal(bus.undoDepth(), 1);

  bus.markSaved();
  assert.equal(bus.isDirty(), false);
});

test('batch is atomic from the caller perspective and its inverse restores the document', () => {
  const executor = new UmlCommandExecutor();
  const inverter = new UmlCommandInverter();
  const before = emptyDocument();
  const batch: UmlCommand = {
    ...commandMetadata(),
    type: 'BATCH',
    label: 'crear modelo',
    commands: [
      createClass('a', 'A'),
      createClass('b', 'B'),
      {
        ...commandMetadata(),
        type: 'CREATE_RELATIONSHIP',
        relationship: {
          id: 'r1',
          sourceClassId: 'a',
          targetClassId: 'b',
          type: 'ASSOCIATION',
          sourceMultiplicity: { lower: 0, upper: null },
          targetMultiplicity: { lower: 1, upper: 1 },
        },
      },
    ],
  };

  const after = executor.execute(before, batch);
  assert.equal(after.umlModel.classes.length, 2);
  assert.equal(after.umlModel.relationships.length, 1);

  const inverse = inverter.invert(before, batch);
  assert.deepEqual(executor.execute(after, inverse), before);
});


test('executor preserves typed error contracts across extracted handlers', () => {
  const executor = new UmlCommandExecutor();
  const oneClass = executor.execute(emptyDocument(), createClass());

  assert.throws(
    () => executor.execute(oneClass, createClass('class-1', 'Duplicada')),
    (error: unknown) =>
      error instanceof UmlCommandError
      && error.code === 'DUPLICATE_CLASS_ID'
      && error.elementId === 'class-1',
  );

  const addAttribute: UmlCommand = {
    ...commandMetadata(),
    type: 'ADD_ATTRIBUTE',
    classId: 'class-1',
    attribute: {
      id: 'attr-1',
      name: 'nombre',
      dataType: 'STRING',
      customTypeName: null,
      visibility: 'PRIVATE',
      nullable: false,
      identifier: false,
    },
  };
  const withAttribute = executor.execute(oneClass, addAttribute);
  assert.throws(
    () => executor.execute(withAttribute, addAttribute),
    (error: unknown) =>
      error instanceof UmlCommandError
      && error.code === 'DUPLICATE_ATTRIBUTE_ID'
      && error.elementId === 'class-1',
  );

  const deleteMissingRelationship: UmlCommand = {
    ...commandMetadata(),
    type: 'DELETE_RELATIONSHIP',
    relationshipId: 'missing-r',
  };
  assert.throws(
    () => executor.execute(withAttribute, deleteMissingRelationship),
    (error: unknown) =>
      error instanceof UmlCommandError
      && error.code === 'RELATIONSHIP_NOT_FOUND'
      && error.elementId === 'missing-r',
  );
});
