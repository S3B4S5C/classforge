import type {
  Multiplicity,
  UmlClass,
  UmlRelationship,
} from './project';

const ASSOCIATION_CLASS_MARKER =
  '__classforge_association_class_v1__';

export interface AssociationClassMetadata {
  markerAttributeId: string;
  relationship: UmlRelationship;
}

export function associationClassMarker(
  relationship: UmlRelationship,
): string {
  return [
    ASSOCIATION_CLASS_MARKER,
    relationship.id,
    relationship.sourceClassId,
    relationship.targetClassId,
    encodeMultiplicity(
      relationship.sourceMultiplicity,
    ),
    encodeMultiplicity(
      relationship.targetMultiplicity,
    ),
  ].join('|');
}

export function associationClassMetadata(
  umlClass: UmlClass,
): AssociationClassMetadata | null {
  for (const attribute of umlClass.attributes) {
    const value = attribute.customTypeName;

    if (!isAssociationClassMarker(value)) {
      continue;
    }

    const parts = value.split('|');

    if (parts.length !== 6) {
      continue;
    }

    const sourceMultiplicity =
      decodeMultiplicity(parts[4]);
    const targetMultiplicity =
      decodeMultiplicity(parts[5]);

    if (
      sourceMultiplicity === undefined
      || targetMultiplicity === undefined
    ) {
      continue;
    }

    return {
      markerAttributeId: attribute.id,
      relationship: {
        id: parts[1],
        sourceClassId: parts[2],
        targetClassId: parts[3],
        type: 'ASSOCIATION',
        sourceMultiplicity,
        targetMultiplicity,
      },
    };
  }

  return null;
}

export function isAssociationClassMarker(
  value: string | null | undefined,
): value is string {
  return Boolean(
    value?.startsWith(
      `${ASSOCIATION_CLASS_MARKER}|`,
    ),
  );
}

function encodeMultiplicity(
  multiplicity: Multiplicity | null,
): string {
  if (!multiplicity) {
    return '-';
  }

  return `${multiplicity.lower}:${
    multiplicity.upper ?? '*'
  }`;
}

function decodeMultiplicity(
  value: string,
): Multiplicity | null | undefined {
  if (value === '-') {
    return null;
  }

  const [lowerRaw, upperRaw, extra] =
    value.split(':');

  if (extra !== undefined) {
    return undefined;
  }

  const lower = Number(lowerRaw);
  const upper =
    upperRaw === '*'
      ? null
      : Number(upperRaw);

  if (
    !Number.isInteger(lower)
    || lower < 0
    || (
      upper !== null
      && (
        !Number.isInteger(upper)
        || upper < lower
      )
    )
  ) {
    return undefined;
  }

  return { lower, upper };
}
