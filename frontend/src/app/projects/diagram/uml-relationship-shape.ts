import { dia, shapes } from '@joint/core';

import {
  Multiplicity,
  UmlRelationship,
  UmlRelationshipType,
} from '../model/project';

const LINK_COLOR = '#667085';
const SELECTED_LINK_COLOR = '#4f46e5';

export function createUmlRelationshipCell(
  relationship: UmlRelationship,
): dia.Link {
  const link = new shapes.standard.Link({
    id: relationship.id,
    source: {
      id: relationship.sourceClassId,
    },
    target: {
      id: relationship.targetClassId,
    },
    attrs: {
      line: {
        stroke: LINK_COLOR,
        strokeWidth: 1.7,
        strokeLinejoin: 'round',
        sourceMarker:
          sourceMarker(relationship.type),
        targetMarker:
          targetMarker(relationship.type),
      },
      wrapper: {
        strokeWidth: 14,
      },
    },
  });

  link.router(
    'manhattan',
    {
      step: 20,
      padding: 24,
    },
  );

  link.connector(
    'rounded',
    {
      radius: 10,
    },
  );

  link.set(
    'umlRelationshipId',
    relationship.id,
  );

  link.set(
    'relationshipType',
    relationship.type,
  );

  if (
    relationship.type
    !== 'GENERALIZATION'
  ) {
    addMultiplicityLabel(
      link,
      formatMultiplicity(
        relationship.sourceMultiplicity,
      ),
      0.14,
    );

    addMultiplicityLabel(
      link,
      formatMultiplicity(
        relationship.targetMultiplicity,
      ),
      0.86,
    );
  }

  return link;
}

export function selectRelationshipCell(
  link: dia.Link,
): void {
  link.attr({
    line: {
      stroke: SELECTED_LINK_COLOR,
      strokeWidth: 2.8,
    },
  });

  link.toFront();
}

export function resetRelationshipCell(
  link: dia.Link,
): void {
  link.attr({
    line: {
      stroke: LINK_COLOR,
      strokeWidth: 1.7,
    },
  });
}

export function formatMultiplicity(
  multiplicity: Multiplicity | null | undefined,
): string {
  if (!multiplicity) {
    return '—';
  }

  if (
    multiplicity.lower === 0
    && multiplicity.upper === null
  ) {
    return '0..*';
  }

  if (
    multiplicity.lower === 1
    && multiplicity.upper === null
  ) {
    return '1..*';
  }

  if (
    multiplicity.upper !== null
    && multiplicity.lower
      === multiplicity.upper
  ) {
    return String(multiplicity.lower);
  }

  return `${multiplicity.lower}..${
    multiplicity.upper ?? '*'
  }`;
}

export function relationshipTypeLabel(
  type: UmlRelationshipType,
): string {
  switch (type) {
    case 'AGGREGATION':
      return 'Agregacion';
    case 'COMPOSITION':
      return 'Composicion';
    case 'GENERALIZATION':
      return 'Generalizacion';
    default:
      return 'Asociacion';
  }
}

function sourceMarker(
  type: UmlRelationshipType,
): Record<string, unknown> {
  if (type === 'AGGREGATION') {
    return {
      type: 'path',
      d: 'M 18 0 9 -6 0 0 9 6 Z',
      fill: '#ffffff',
      stroke: LINK_COLOR,
      strokeWidth: 1.4,
    };
  }

  if (type === 'COMPOSITION') {
    return {
      type: 'path',
      d: 'M 18 0 9 -6 0 0 9 6 Z',
      fill: LINK_COLOR,
      stroke: LINK_COLOR,
      strokeWidth: 1.4,
    };
  }

  return noMarker();
}

function targetMarker(
  type: UmlRelationshipType,
): Record<string, unknown> {
  if (type === 'GENERALIZATION') {
    return {
      type: 'path',
      d: 'M 16 -8 0 0 16 8 Z',
      fill: '#ffffff',
      stroke: LINK_COLOR,
      strokeWidth: 1.5,
    };
  }

  return noMarker();
}

function noMarker(): Record<string, unknown> {
  return {
    type: 'path',
    d: 'M 0 0',
    fill: 'none',
    stroke: 'none',
  };
}

function addMultiplicityLabel(
  link: dia.Link,
  text: string,
  distance: number,
): void {
  link.appendLabel({
    position: {
      distance,
      offset: -15,
    },
    attrs: {
      text: {
        text,
        fill: '#344054',
        fontSize: 12,
        fontWeight: 700,
        fontFamily:
          'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
      },
      rect: {
        fill: '#ffffff',
        stroke: '#d0d5dd',
        strokeWidth: 1,
        rx: 4,
        ry: 4,
      },
    },
  });
}