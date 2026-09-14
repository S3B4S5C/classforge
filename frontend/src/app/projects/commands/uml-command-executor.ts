import type { ProjectDocument } from '../model/project';
import type { UmlCommand } from './uml-command';
import { UmlCommandError } from './uml-command-error';
import { UmlClassCommandExecution } from './execution/uml-class-command-execution';
import { UmlAttributeCommandExecution } from './execution/uml-attribute-command-execution';
import { UmlRelationshipCommandExecution } from './execution/uml-relationship-command-execution';
import { UmlLayoutCommandExecution } from './execution/uml-layout-command-execution';

/**
 * Stable dispatcher for UML commands. Mutations are grouped by command family in
 * execution/ so this file documents the command protocol instead of implementation detail.
 */
export class UmlCommandExecutor {
  private readonly classCommands = new UmlClassCommandExecution();
  private readonly attributeCommands = new UmlAttributeCommandExecution();
  private readonly relationshipCommands = new UmlRelationshipCommandExecution();
  private readonly layoutCommands = new UmlLayoutCommandExecution();

  execute(current: ProjectDocument, command: UmlCommand): ProjectDocument {
    const document = structuredClone(current);
    switch (command.type) {
      case 'CREATE_CLASS':
        this.classCommands.createClass(document, command.umlClass, command.layout);
        break;
      case 'RENAME_CLASS':
        this.classCommands.renameClass(document, command.classId, command.name);
        break;
      case 'DELETE_CLASS':
        this.classCommands.deleteClass(document, command.classId);
        break;
      case 'RESTORE_CLASS':
        this.classCommands.restoreClass(document, command.umlClass, command.layout, command.relationships);
        break;
      case 'ADD_ATTRIBUTE':
        this.attributeCommands.addAttribute(document, command.classId, command.attribute);
        break;
      case 'UPDATE_ATTRIBUTE':
        this.attributeCommands.updateAttribute(document, command.classId, command.attribute);
        break;
      case 'DELETE_ATTRIBUTE':
        this.attributeCommands.deleteAttribute(document, command.classId, command.attributeId);
        break;
      case 'CREATE_RELATIONSHIP':
        this.relationshipCommands.createRelationship(document, command.relationship);
        break;
      case 'UPDATE_RELATIONSHIP':
        this.relationshipCommands.updateRelationship(document, command.relationship);
        break;
      case 'DELETE_RELATIONSHIP':
        this.relationshipCommands.deleteRelationship(document, command.relationshipId);
        break;
      case 'MOVE_CLASS':
        this.layoutCommands.moveClass(document, command.classId, command.layout);
        break;
      case 'BATCH':
        return this.executeBatch(document, command.commands);
    }
    return document;
  }

  private executeBatch(document: ProjectDocument, commands: UmlCommand[]): ProjectDocument {
    if (commands.length === 0 || commands.length > 50) {
      throw new UmlCommandError('BATCH_SIZE_INVALID', 'Un BATCH debe contener entre 1 y 50 comandos.');
    }
    let next = structuredClone(document);
    for (const command of commands) {
      if (command.type === 'BATCH') {
        throw new UmlCommandError('NESTED_BATCH_NOT_ALLOWED', 'No se permiten BATCH anidados.');
      }
      next = this.execute(next, command);
    }
    return next;
  }
}
