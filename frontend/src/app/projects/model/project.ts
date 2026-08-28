export interface UmlModelSnapshot {
  schemaVersion: string;
  elements: unknown[];
}

export interface Project {
  id: string;
  name: string;
  revision: number;
  umlModel: UmlModelSnapshot;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
}