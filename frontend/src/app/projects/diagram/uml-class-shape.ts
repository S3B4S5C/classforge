import { dia, shapes } from '@joint/core';

import {
  DiagramNodeLayout,
  UmlAttribute,
  UmlClass,
  UmlDataType,
  UmlVisibility,
} from '../model/project';
import {
  normalizeUmlClassLayout,
  UML_CLASS_HEADER_HEIGHT,
} from './uml-class-geometry';

export const UmlClassShape = dia.Element.define(
  'classforge.UmlClass',
  {
    attrs: {
      body: {
        width: 'calc(w)',
        height: 'calc(h)',
        fill: '#ffffff',
        stroke: '#98a2b3',
        strokeWidth: 1.4,
        rx: 12,
        ry: 12,
        cursor: 'move',
      },
      header: {
        width: 'calc(w)',
        height: UML_CLASS_HEADER_HEIGHT,
        fill: '#f8f9fc',
        stroke: 'none',
        rx: 12,
        ry: 12,
        pointerEvents: 'none',
      },
      headerMask: {
        y: UML_CLASS_HEADER_HEIGHT - 12,
        width: 'calc(w)',
        height: 12,
        fill: '#f8f9fc',
        stroke: 'none',
        pointerEvents: 'none',
      },
      divider: {
        x1: 0,
        y1: UML_CLASS_HEADER_HEIGHT,
        x2: 'calc(w)',
        y2: UML_CLASS_HEADER_HEIGHT,
        stroke: '#d0d5dd',
        strokeWidth: 1,
        pointerEvents: 'none',
      },
      stereotype: {
        x: 16,
        y: 19,
        fill: '#667085',
        fontSize: 10,
        fontFamily:
          'Inter, ui-sans-serif, system-ui, sans-serif',
        fontWeight: 700,
        letterSpacing: '0.12em',
        text: 'CLASS',
        pointerEvents: 'none',
      },
      className: {
        x: 16,
        y: 43,
        fill: '#101828',
        fontSize: 18,
        fontFamily:
          'Inter, ui-sans-serif, system-ui, sans-serif',
        fontWeight: 750,
        text: 'Class',
        pointerEvents: 'none',
      },
      attributes: {
        x: 16,
        y: 84,
        fill: '#344054',
        fontSize: 13,
        fontFamily:
          'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
        lineHeight: 22,
        text: '',
        pointerEvents: 'none',
      },
    },
  },
  {
    markup: [
      {
        tagName: 'rect',
        selector: 'body',
      },
      {
        tagName: 'rect',
        selector: 'header',
      },
      {
        tagName: 'rect',
        selector: 'headerMask',
      },
      {
        tagName: 'line',
        selector: 'divider',
      },
      {
        tagName: 'text',
        selector: 'stereotype',
      },
      {
        tagName: 'text',
        selector: 'className',
      },
      {
        tagName: 'text',
        selector: 'attributes',
      },
    ],
  },
);

export const classForgeCellNamespace = {
  ...shapes,
  classforge: {
    UmlClass: UmlClassShape,
  },
};

export function createUmlClassCell(
  umlClass: UmlClass,
  layout: DiagramNodeLayout,
): dia.Element {
  const normalized =
    normalizeUmlClassLayout(
      layout,
      umlClass.attributes.length,
    );

  const cell = new UmlClassShape({
    id: umlClass.id,
    position: {
      x: normalized.x,
      y: normalized.y,
    },
    size: {
      width: normalized.width,
      height: normalized.height,
    },
    attrs: {
      className: {
        text: umlClass.name,
      },
      attributes: {
        text:
          umlClass.attributes.length === 0
            ? '  (sin atributos)'
            : umlClass.attributes
                .map(formatAttribute)
                .join('\n'),
        fill:
          umlClass.attributes.length === 0
            ? '#98a2b3'
            : '#344054',
      },
    },
  });

  cell.set('umlClassId', umlClass.id);

  return cell;
}

function formatAttribute(
  attribute: UmlAttribute,
): string {
  const visibility =
    visibilitySymbol(attribute.visibility);

  const type = dataTypeLabel(attribute);

  const identifier =
    attribute.identifier ? ' {id}' : '';

  const nullable =
    attribute.nullable ? ' [0..1]' : '';

  return `${visibility} ${attribute.name}: ${type}${identifier}${nullable}`;
}

function visibilitySymbol(
  visibility: UmlVisibility,
): string {
  switch (visibility) {
    case 'PUBLIC':
      return '+';
    case 'PROTECTED':
      return '#';
    case 'PACKAGE':
      return '~';
    default:
      return '-';
  }
}

function dataTypeLabel(
  attribute: UmlAttribute,
): string {
  if (
    attribute.dataType === 'CUSTOM'
    && attribute.customTypeName
  ) {
    return attribute.customTypeName;
  }

  const labels: Record<UmlDataType, string> = {
    STRING: 'String',
    INTEGER: 'Integer',
    LONG: 'Long',
    DECIMAL: 'Decimal',
    BOOLEAN: 'Boolean',
    DATE: 'Date',
    DATETIME: 'DateTime',
    UUID: 'UUID',
    CUSTOM: 'Custom',
  };

  return labels[attribute.dataType];
}