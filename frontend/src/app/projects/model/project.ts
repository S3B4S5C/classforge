export interface UmlModel {
  classes: Record<string, unknown>[];
  relationships: Record<string, unknown>[];
}

export interface DiagramLayout {
  nodes: Record<string, Record<string, unknown>>;
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