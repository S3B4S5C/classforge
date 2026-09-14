import type {
  DiagramNodeLayout,
  ProjectDocument,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../../model/project';
import { UmlCommandError } from '../uml-command-error';
import { UmlCommandExecutionSupport } from './uml-command-execution-support';

export class UmlRelationshipCommandExecution {
  constructor(
    private readonly support =
      new UmlCommandExecutionSupport(),
  ) {}

  createRelationship(
    document: ProjectDocument,
    relationship: UmlRelationship,
  ): void {
    if (
      document.umlModel.relationships.some(
        (candidate) =>
          candidate.id === relationship.id,
      )
    ) {
      throw new UmlCommandError(
        'DUPLICATE_RELATIONSHIP_ID',
        'El identificador de la relacion ya existe.',
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

    this.support.assertNoGeneralizationCycle(
      document,
      relationship.id,
    );
  }

  updateRelationship(
    document: ProjectDocument,
    relationship: UmlRelationship,
  ): void {
    const index =
      document.umlModel.relationships.findIndex(
        (candidate) =>
          candidate.id === relationship.id,
      );

    if (index < 0) {
      throw new UmlCommandError(
        'RELATIONSHIP_NOT_FOUND',
        'La relacion que intentas editar ya no existe.',
        relationship.id,
      );
    }

    this.support.assertRelationship(
      document,
      relationship,
    );

    document.umlModel.relationships[index] =
      this.support.normalizedRelationship(
        relationship,
      );

    this.support.assertNoGeneralizationCycle(
      document,
      relationship.id,
    );
  }

  deleteRelationship(
    document: ProjectDocument,
    relationshipId: string,
  ): void {
    const exists =
      document.umlModel.relationships.some(
        (relationship) =>
          relationship.id === relationshipId,
      );

    if (!exists) {
      throw new UmlCommandError(
        'RELATIONSHIP_NOT_FOUND',
        'La relacion que intentas eliminar ya no existe.',
        relationshipId,
      );
    }

    document.umlModel.relationships =
      document.umlModel.relationships.filter(
        (relationship) =>
          relationship.id !== relationshipId,
      );
  }
}
