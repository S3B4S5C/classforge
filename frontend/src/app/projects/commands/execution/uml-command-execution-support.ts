import type {
  DiagramNodeLayout,
  Multiplicity,
  ProjectDocument,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../../model/project';
import { UmlCommandError } from '../uml-command-error';
import {
  normalizeUmlClassLayout,
  UML_CLASS_MIN_WIDTH,
  umlClassHeight,
} from '../../diagram/uml-class-geometry';

const CODE_NAME = /^[A-Za-z_][A-Za-z0-9_]*$/;

export class UmlCommandExecutionSupport {

  assertClassName(
    document: ProjectDocument,
    name: string,
    currentClassId: string | null,
  ): void {
    this.assertCodeName(
      name,
      'CLASS_NAME_INVALID',
      'Usa un nombre de clase compatible con codigo.',
      currentClassId,
    );

    const normalized =
      name.toLowerCase();

    const duplicate =
      document.umlModel.classes.some(
        (umlClass) =>
          umlClass.id !== currentClassId
          && umlClass.name.toLowerCase()
            === normalized,
      );

    if (duplicate) {
      throw new UmlCommandError(
        'DUPLICATE_CLASS_NAME',
        `Ya existe otra clase llamada '${name}'.`,
        currentClassId,
      );
    }
  }

  assertAttribute(
    umlClass: UmlClass,
    attribute: UmlAttribute,
    currentAttributeId: string | null,
  ): void {
    this.assertCodeName(
      attribute.name,
      'ATTRIBUTE_NAME_INVALID',
      'Usa un nombre de atributo compatible con codigo.',
      umlClass.id,
    );

    const normalized =
      attribute.name.toLowerCase();

    const duplicate =
      umlClass.attributes.some(
        (candidate) =>
          candidate.id !== currentAttributeId
          && candidate.name.toLowerCase()
            === normalized,
      );

    if (duplicate) {
      throw new UmlCommandError(
        'DUPLICATE_ATTRIBUTE_NAME',
        `La clase '${umlClass.name}' ya tiene un atributo llamado '${attribute.name}'.`,
        umlClass.id,
      );
    }

    if (
      attribute.identifier
      && attribute.nullable
    ) {
      throw new UmlCommandError(
        'IDENTIFIER_CANNOT_BE_NULLABLE',
        'Un atributo identificador no puede ser nullable.',
        umlClass.id,
      );
    }

    if (
      attribute.dataType === 'CUSTOM'
      && (
        !attribute.customTypeName
        || !CODE_NAME.test(
          attribute.customTypeName,
        )
      )
    ) {
      throw new UmlCommandError(
        'CUSTOM_TYPE_NAME_INVALID',
        'El tipo personalizado necesita un nombre compatible con codigo.',
        umlClass.id,
      );
    }
  }

  assertRelationship(
    document: ProjectDocument,
    relationship: UmlRelationship,
  ): void {
    this.requireClass(
      document,
      relationship.sourceClassId,
    );

    this.requireClass(
      document,
      relationship.targetClassId,
    );

    if (
      relationship.type === 'GENERALIZATION'
      && relationship.sourceClassId
        === relationship.targetClassId
    ) {
      throw new UmlCommandError(
        'GENERALIZATION_SELF_REFERENCE',
        'Una clase no puede generalizarse a si misma.',
        relationship.id,
      );
    }

    if (
      relationship.type !== 'GENERALIZATION'
    ) {
      this.assertMultiplicity(
        relationship.sourceMultiplicity,
        relationship.id,
      );

      this.assertMultiplicity(
        relationship.targetMultiplicity,
        relationship.id,
      );
    }
  }

  assertMultiplicity(
    multiplicity: Multiplicity | null,
    relationshipId: string,
  ): void {
    if (!multiplicity) {
      throw new UmlCommandError(
        'MULTIPLICITY_REQUIRED',
        'La multiplicidad es obligatoria para esta relacion.',
        relationshipId,
      );
    }

    if (multiplicity.lower < 0) {
      throw new UmlCommandError(
        'MULTIPLICITY_LOWER_INVALID',
        'El limite inferior de la multiplicidad no puede ser negativo.',
        relationshipId,
      );
    }

    if (
      multiplicity.upper !== null
      && multiplicity.upper
        < multiplicity.lower
    ) {
      throw new UmlCommandError(
        'MULTIPLICITY_RANGE_INVALID',
        'El limite superior no puede ser menor que el inferior.',
        relationshipId,
      );
    }
  }

  assertNoGeneralizationCycle(
    document: ProjectDocument,
    relationshipId: string,
  ): void {
    const graph =
      new Map<string, string[]>();

    for (
      const relationship
      of document.umlModel.relationships
    ) {
      if (
        relationship.type
          !== 'GENERALIZATION'
      ) {
        continue;
      }

      const parents =
        graph.get(
          relationship.sourceClassId,
        )
        ?? [];

      parents.push(
        relationship.targetClassId,
      );

      graph.set(
        relationship.sourceClassId,
        parents,
      );
    }

    const state =
      new Map<string, number>();

    const visit = (
      classId: string,
    ): boolean => {
      const currentState =
        state.get(classId) ?? 0;

      if (currentState === 1) {
        return true;
      }

      if (currentState === 2) {
        return false;
      }

      state.set(classId, 1);

      for (
        const parentId
        of graph.get(classId) ?? []
      ) {
        if (visit(parentId)) {
          return true;
        }
      }

      state.set(classId, 2);
      return false;
    };

    for (
      const umlClass
      of document.umlModel.classes
    ) {
      if (visit(umlClass.id)) {
        throw new UmlCommandError(
          'GENERALIZATION_CYCLE',
          'La generalizacion crearia un ciclo de herencia.',
          relationshipId,
        );
      }
    }
  }

  normalizedRelationship(
    relationship: UmlRelationship,
  ): UmlRelationship {
    const cloned =
      structuredClone(relationship);

    if (
      cloned.type === 'GENERALIZATION'
    ) {
      cloned.sourceMultiplicity = null;
      cloned.targetMultiplicity = null;
    }

    return cloned;
  }

  requireClass(
    document: ProjectDocument,
    classId: string,
  ): UmlClass {
    const umlClass =
      document.umlModel.classes.find(
        (candidate) =>
          candidate.id === classId,
      );

    if (!umlClass) {
      throw new UmlCommandError(
        'CLASS_NOT_FOUND',
        'La clase indicada ya no existe.',
        classId,
      );
    }

    return umlClass;
  }

  assertCodeName(
    value: string,
    code: string,
    message: string,
    elementId: string | null,
  ): void {
    if (
      !value
      || !CODE_NAME.test(value)
    ) {
      throw new UmlCommandError(
        code,
        message,
        elementId,
      );
    }
  }

  assertFiniteLayout(
    layout: DiagramNodeLayout,
    classId: string,
  ): void {
    if (
      !Number.isFinite(layout.x)
      || !Number.isFinite(layout.y)
      || !Number.isFinite(layout.width)
      || !Number.isFinite(layout.height)
      || layout.width <= 0
      || layout.height <= 0
    ) {
      throw new UmlCommandError(
        'LAYOUT_INVALID',
        'La posicion o el tamano de la clase no es valido.',
        classId,
      );
    }
  }

  normalizeClassLayout(
    document: ProjectDocument,
    umlClass: UmlClass,
  ): void {
    const current =
      document.layout.nodes[umlClass.id]
      ?? this.defaultLayout(
        document.umlModel.classes
          .findIndex(
            (candidate) =>
              candidate.id === umlClass.id,
          ),
      );

    document.layout.nodes[umlClass.id] =
      normalizeUmlClassLayout(
        current,
        umlClass.attributes.length,
      );
  }

  defaultLayout(
    index: number,
  ): DiagramNodeLayout {
    const safeIndex =
      Math.max(index, 0);

    const column =
      safeIndex % 3;

    const row =
      Math.floor(
        safeIndex / 3,
      );

    return {
      x: 80 + (column * 300),
      y: 80 + (row * 220),
      width: UML_CLASS_MIN_WIDTH,
      height: umlClassHeight(0),
    };
  }
}
