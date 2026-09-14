import type {
  DiagramNodeLayout,
  ProjectDocument,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../../model/project';
import { normalizeUmlClassLayout } from '../../diagram/uml-class-geometry';
import { UmlCommandError } from '../uml-command-error';
import { UmlCommandExecutionSupport } from './uml-command-execution-support';

export class UmlClassCommandExecution {
  constructor(
    private readonly support =
      new UmlCommandExecutionSupport(),
  ) {}

  createClass(
    document: ProjectDocument,
    umlClass: UmlClass,
    layout: DiagramNodeLayout,
  ): void {
    this.support.assertClassName(
      document,
      umlClass.name,
      null,
    );

    if (
      document.umlModel.classes.some(
        (candidate) =>
          candidate.id === umlClass.id,
      )
    ) {
      throw new UmlCommandError(
        'DUPLICATE_CLASS_ID',
        'El identificador de la clase ya existe.',
        umlClass.id,
      );
    }

    this.support.assertFiniteLayout(
      layout,
      umlClass.id,
    );

    document.umlModel.classes.push(
      structuredClone(umlClass),
    );

    document.layout.nodes[umlClass.id] =
      normalizeUmlClassLayout(
        layout,
        umlClass.attributes.length,
      );
  }

  restoreClass(
    document: ProjectDocument,
    umlClass: UmlClass,
    layout: DiagramNodeLayout | null,
    relationships: UmlRelationship[],
  ): void {
    this.support.assertClassName(
      document,
      umlClass.name,
      null,
    );

    if (
      document.umlModel.classes.some(
        (candidate) =>
          candidate.id === umlClass.id,
      )
    ) {
      throw new UmlCommandError(
        'DUPLICATE_CLASS_ID',
        'El identificador de la clase ya existe.',
        umlClass.id,
      );
    }

    for (
      const attribute
      of umlClass.attributes
    ) {
      const duplicateAttributeId =
        document.umlModel.classes.some(
          (candidateClass) =>
            candidateClass.attributes.some(
              (candidate) =>
                candidate.id
                  === attribute.id,
            ),
        );

      if (duplicateAttributeId) {
        throw new UmlCommandError(
          'DUPLICATE_ATTRIBUTE_ID',
          'Uno de los atributos restaurados ya existe.',
          umlClass.id,
        );
      }
    }

    document.umlModel.classes.push(
      structuredClone(umlClass),
    );

    if (layout) {
      this.support.assertFiniteLayout(
        layout,
        umlClass.id,
      );

      document.layout.nodes[umlClass.id] =
        normalizeUmlClassLayout(
          layout,
          umlClass.attributes.length,
        );
    }

    for (
      const relationship
      of relationships
    ) {
      if (
        relationship.sourceClassId
          !== umlClass.id
        && relationship.targetClassId
          !== umlClass.id
      ) {
        throw new UmlCommandError(
          'RESTORE_RELATIONSHIP_NOT_CONNECTED',
          'RESTORE_CLASS solo puede recuperar relaciones conectadas a la clase.',
          relationship.id,
        );
      }

      if (
        document.umlModel.relationships
          .some(
            (candidate) =>
              candidate.id
                === relationship.id,
          )
      ) {
        throw new UmlCommandError(
          'DUPLICATE_RELATIONSHIP_ID',
          'Una de las relaciones restauradas ya existe.',
          relationship.id,
        );
      }

      this.support.assertRelationship(
        document,
        relationship,
      );

      document.umlModel.relationships.push(
        this.support.normalizedRelationship(
          relationship,
        ),
      );
    }

    this.support.assertNoGeneralizationCycle(
      document,
      umlClass.id,
    );
  }

  renameClass(
    document: ProjectDocument,
    classId: string,
    name: string,
  ): void {
    const umlClass =
      this.support.requireClass(
        document,
        classId,
      );

    this.support.assertClassName(
      document,
      name,
      classId,
    );

    umlClass.name = name;
  }

  deleteClass(
    document: ProjectDocument,
    classId: string,
  ): void {
    this.support.requireClass(
      document,
      classId,
    );

    document.umlModel.classes =
      document.umlModel.classes.filter(
        (umlClass) =>
          umlClass.id !== classId,
      );

    document.umlModel.relationships =
      document.umlModel.relationships.filter(
        (relationship) =>
          relationship.sourceClassId !== classId
          && relationship.targetClassId !== classId,
      );

    delete document.layout.nodes[classId];
  }
}
