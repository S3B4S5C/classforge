import type {
  Multiplicity,
  UmlClass,
  UmlRelationship,
  UmlRelationshipType,
} from './project';

const ASSOCIATION_CLASS_MARKER_V1 =
  '__classforge_association_class_v1__';
const ASSOCIATION_CLASS_MARKER_V2 =
  '__classforge_association_class_v2__';

export interface AssociationClassMetadata {
  markerAttributeId: string;
  relationship: UmlRelationship;
}

export function associationClassMarker(
  relationship: UmlRelationship,
): string {
  return [
    ASSOCIATION_CLASS_MARKER_V2,
    relationship.id,
    relationship.sourceClassId,
    relationship.targetClassId,
    relationship.type,
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
    const v1 = parts[0] === ASSOCIATION_CLASS_MARKER_V1;
    const v2 = parts[0] === ASSOCIATION_CLASS_MARKER_V2;

    if ((v1 && parts.length !== 6)
      || (v2 && parts.length !== 7)) {
      continue;
    }

    const parsedType = v1
      ? 'ASSOCIATION'
      : parseRelationshipType(parts[4]);
    if (!parsedType || parsedType === 'GENERALIZATION') {
      continue;
    }
    const type: UmlRelationshipType = parsedType;

    const sourceMultiplicity =
      decodeMultiplicity(parts[v1 ? 4 : 5]);
    const targetMultiplicity =
      decodeMultiplicity(parts[v1 ? 5 : 6]);

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
        type,
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
    value?.startsWith(`${ASSOCIATION_CLASS_MARKER_V1}|`)
    || value?.startsWith(`${ASSOCIATION_CLASS_MARKER_V2}|`),
  );
}

function parseRelationshipType(
  value: string,
): UmlRelationshipType | null {
  switch (value) {
    case 'ASSOCIATION':
    case 'AGGREGATION':
    case 'COMPOSITION':
    case 'GENERALIZATION':
      return value;
    default:
      return null;
  }
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
