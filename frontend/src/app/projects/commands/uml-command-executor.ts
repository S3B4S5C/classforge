import {
  normalizeUmlClassLayout,
  UML_CLASS_MIN_WIDTH,
  umlClassHeight,
} from '../diagram/uml-class-geometry';
import {
  DiagramNodeLayout,
  Multiplicity,
  ProjectDocument,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../model/project';
import { UmlCommand } from './uml-command';
import { UmlCommandError } from './uml-command-error';

const CODE_NAME =
  /^[A-Za-z_][A-Za-z0-9_]*$/;

export class UmlCommandExecutor {
  execute(
    current: ProjectDocument,
    command: UmlCommand,
  ): ProjectDocument {
    const document =
      structuredClone(current);

    switch (command.type) {
      case 'CREATE_CLASS':
        this.createClass(
          document,
          command.umlClass,
          command.layout,
        );
        break;

      case 'RENAME_CLASS':
        this.renameClass(
          document,
          command.classId,
          command.name,
        );
        break;

      case 'DELETE_CLASS':
        this.deleteClass(
          document,
          command.classId,
        );
        break;

      case 'ADD_ATTRIBUTE':
        this.addAttribute(
          document,
          command.classId,
          command.attribute,
        );
        break;

      case 'UPDATE_ATTRIBUTE':
        this.updateAttribute(
          document,
          command.classId,
          command.attribute,
        );
        break;

      case 'DELETE_ATTRIBUTE':
        this.deleteAttribute(
          document,
          command.classId,
          command.attributeId,
        );
        break;

      case 'CREATE_RELATIONSHIP':
        this.createRelationship(
          document,
          command.relationship,
        );
        break;

      case 'UPDATE_RELATIONSHIP':
        this.updateRelationship(
          document,
          command.relationship,
        );
        break;

      case 'DELETE_RELATIONSHIP':
        this.deleteRelationship(
          document,
          command.relationshipId,
        );
        break;

      case 'MOVE_CLASS':
        this.moveClass(
          document,
          command.classId,
          command.layout,
        );
        break;
    }

    return document;
  }

  private createClass(
    document: ProjectDocument,
    umlClass: UmlClass,
    layout: DiagramNodeLayout,
  ): void {
    this.assertClassName(
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

    this.assertFiniteLayout(
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

  private renameClass(
    document: ProjectDocument,
    classId: string,
    name: string,
  ): void {
    const umlClass =
      this.requireClass(
        document,
        classId,
      );

    this.assertClassName(
      document,
      name,
      classId,
    );

    umlClass.name = name;
  }

  private deleteClass(
    document: ProjectDocument,
    classId: string,
  ): void {
    this.requireClass(
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

  private addAttribute(
    document: ProjectDocument,
    classId: string,
    attribute: UmlAttribute,
  ): void {
    const umlClass =
      this.requireClass(
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

    this.assertAttribute(
      umlClass,
      attribute,
      null,
    );

    umlClass.attributes.push(
      structuredClone(attribute),
    );

    this.normalizeClassLayout(
      document,
      umlClass,
    );
  }

  private updateAttribute(
    document: ProjectDocument,
    classId: string,
    attribute: UmlAttribute,
  ): void {
    const umlClass =
      this.requireClass(
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

    this.assertAttribute(
      umlClass,
      attribute,
      attribute.id,
    );

    umlClass.attributes[index] =
      structuredClone(attribute);

    this.normalizeClassLayout(
      document,
      umlClass,
    );
  }

  private deleteAttribute(
    document: ProjectDocument,
    classId: string,
    attributeId: string,
  ): void {
    const umlClass =
      this.requireClass(
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

    this.normalizeClassLayout(
      document,
      umlClass,
    );
  }

  private createRelationship(
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

    this.assertRelationship(
      document,
      relationship,
    );

    document.umlModel.relationships.push(
      this.normalizedRelationship(
        relationship,
      ),
    );

    this.assertNoGeneralizationCycle(
      document,
      relationship.id,
    );
  }

  private updateRelationship(
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

    this.assertRelationship(
      document,
      relationship,
    );

    document.umlModel.relationships[index] =
      this.normalizedRelationship(
        relationship,
      );

    this.assertNoGeneralizationCycle(
      document,
      relationship.id,
    );
  }

  private deleteRelationship(
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

  private moveClass(
    document: ProjectDocument,
    classId: string,
    layout: DiagramNodeLayout,
  ): void {
    const umlClass =
      this.requireClass(
        document,
        classId,
      );

    this.assertFiniteLayout(
      layout,
      classId,
    );

    document.layout.nodes[classId] =
      normalizeUmlClassLayout(
        layout,
        umlClass.attributes.length,
      );
  }

  private assertClassName(
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

  private assertAttribute(
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

  private assertRelationship(
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

  private assertMultiplicity(
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

  private assertNoGeneralizationCycle(
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

  private normalizedRelationship(
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

  private requireClass(
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

  private assertCodeName(
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

  private assertFiniteLayout(
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

  private normalizeClassLayout(
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

  private defaultLayout(
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