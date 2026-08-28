export type UmlVisibility =
  | 'PUBLIC'
  | 'PRIVATE'
  | 'PROTECTED'
  | 'PACKAGE';

export type UmlDataType =
  | 'STRING'
  | 'INTEGER'
  | 'LONG'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'UUID'
  | 'CUSTOM';

export type UmlRelationshipType =
  | 'ASSOCIATION'
  | 'AGGREGATION'
  | 'COMPOSITION'
  | 'GENERALIZATION';

export interface UmlAttribute {
  id: string;
  name: string;
  dataType: UmlDataType;
  customTypeName: string | null;
  visibility: UmlVisibility;
  nullable: boolean;
  identifier: boolean;
}

export interface UmlClass {
  id: string;
  name: string;
  attributes: UmlAttribute[];
}

export interface Multiplicity {
  lower: number;
  upper: number | null;
}

export interface UmlRelationship {
  id: string;
  sourceClassId: string;
  targetClassId: string;
  type: UmlRelationshipType;
  sourceMultiplicity: Multiplicity | null;
  targetMultiplicity: Multiplicity | null;
}

export interface UmlModel {
  classes: UmlClass[];
  relationships: UmlRelationship[];
}

export interface DiagramNodeLayout {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DiagramLayout {
  nodes: Record<string, DiagramNodeLayout>;
}

export interface ProjectDocument {
  schemaVersion: string;
  umlModel: UmlModel;
  layout: DiagramLayout;
}

export interface Project {
  id: string;
  name: string;
  revision: number;
  document: ProjectDocument;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
}

export interface UpdateProjectRequest {
  name: string;
}

export interface SaveProjectDocumentRequest {
  baseRevision: number;
  document: ProjectDocument;
}

export type ValidationSeverity =
  | 'ERROR'
  | 'WARNING'
  | 'INFO';

export interface ProjectValidationDiagnostic {
  severity: ValidationSeverity;
  code: string;
  field: string;
  elementId: string | null;
  message: string;
}

export interface ProjectValidationResult {
  valid: boolean;
  errors: number;
  warnings: number;
  infos: number;
  diagnostics: ProjectValidationDiagnostic[];
}

export interface ValidateProjectDocumentRequest {
  document: ProjectDocument;
}

export interface BackendValidationViolation {
  field: string;
  code: string;
  message: string;
}

export interface BackendValidationError {
  error: string;
  message: string;
  violations: BackendValidationViolation[];
}