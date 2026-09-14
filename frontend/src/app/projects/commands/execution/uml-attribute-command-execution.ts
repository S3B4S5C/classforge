import type {
  DiagramNodeLayout,
  ProjectDocument,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../../model/project';
import { UmlCommandError } from '../uml-command-error';
import { UmlCommandExecutionSupport } from './uml-command-execution-support';

export class UmlAttributeCommandExecution {
  constructor(
    private readonly support =
      new UmlCommandExecutionSupport(),
  ) {}

  addAttribute(
    document: ProjectDocument,
    classId: string,
    attribute: UmlAttribute,
  ): void {
    const umlClass =
      this.support.requireClass(
        document,
        classId,
      );

    if (
      document.umlModel.classes.some(
        (candidateClass) =>
          candidateClass.attributes.some(
            (candidate) =>
              candidate.id === attribute.id,
          ),
      )
    ) {
      throw new UmlCommandError(
        'DUPLICATE_ATTRIBUTE_ID',
        'El identificador del atributo ya existe.',
        classId,
      );
    }

    this.support.assertAttribute(
      umlClass,
      attribute,
      null,
    );

    umlClass.attributes.push(
      structuredClone(attribute),
    );

    this.support.normalizeClassLayout(
      document,
      umlClass,
    );
  }

  updateAttribute(
    document: ProjectDocument,
    classId: string,
    attribute: UmlAttribute,
  ): void {
    const umlClass =
      this.support.requireClass(
        document,
        classId,
      );

    const index =
      umlClass.attributes.findIndex(
        (candidate) =>
          candidate.id === attribute.id,
      );

    if (index < 0) {
      throw new UmlCommandError(
        'ATTRIBUTE_NOT_FOUND',
        'El atributo que intentas editar ya no existe.',
        classId,
      );
    }

    this.support.assertAttribute(
      umlClass,
      attribute,
      attribute.id,
    );

    umlClass.attributes[index] =
      structuredClone(attribute);

    this.support.normalizeClassLayout(
      document,
      umlClass,
    );
  }

  deleteAttribute(
    document: ProjectDocument,
    classId: string,
    attributeId: string,
  ): void {
    const umlClass =
      this.support.requireClass(
        document,
        classId,
      );

    const exists =
      umlClass.attributes.some(
        (attribute) =>
          attribute.id === attributeId,
      );

    if (!exists) {
      throw new UmlCommandError(
        'ATTRIBUTE_NOT_FOUND',
        'El atributo que intentas eliminar ya no existe.',
        classId,
      );
    }

    umlClass.attributes =
      umlClass.attributes.filter(
        (attribute) =>
          attribute.id !== attributeId,
      );

    this.support.normalizeClassLayout(
      document,
      umlClass,
    );
  }
}
