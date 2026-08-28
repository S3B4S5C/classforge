import {
  DiagramNodeLayout,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../model/project';

interface UmlCommandBase {
  commandId: string;
  issuedAt: string;
}

export type UmlCommand =
  | (UmlCommandBase & {
      type: 'CREATE_CLASS';
      umlClass: UmlClass;
      layout: DiagramNodeLayout;
    })
  | (UmlCommandBase & {
      type: 'RENAME_CLASS';
      classId: string;
      name: string;
    })
  | (UmlCommandBase & {
      type: 'DELETE_CLASS';
      classId: string;
    })
  | (UmlCommandBase & {
      type: 'RESTORE_CLASS';
      umlClass: UmlClass;
      layout: DiagramNodeLayout | null;
      relationships: UmlRelationship[];
    })
  | (UmlCommandBase & {
      type: 'ADD_ATTRIBUTE';
      classId: string;
      attribute: UmlAttribute;
    })
  | (UmlCommandBase & {
      type: 'UPDATE_ATTRIBUTE';
      classId: string;
      attribute: UmlAttribute;
    })
  | (UmlCommandBase & {
      type: 'DELETE_ATTRIBUTE';
      classId: string;
      attributeId: string;
    })
  | (UmlCommandBase & {
      type: 'CREATE_RELATIONSHIP';
      relationship: UmlRelationship;
    })
  | (UmlCommandBase & {
      type: 'UPDATE_RELATIONSHIP';
      relationship: UmlRelationship;
    })
  | (UmlCommandBase & {
      type: 'DELETE_RELATIONSHIP';
      relationshipId: string;
    })
  | (UmlCommandBase & {
      type: 'MOVE_CLASS';
      classId: string;
      layout: DiagramNodeLayout;
    })
  | (UmlCommandBase & {
      type: 'BATCH';
      label: string;
      commands: UmlCommand[];
    });

export type UmlCommandType = UmlCommand['type'];

export function commandMetadata(): Pick<
  UmlCommandBase,
  'commandId' | 'issuedAt'
> {
  return {
    commandId: crypto.randomUUID(),
    issuedAt: new Date().toISOString(),
  };
}