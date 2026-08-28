import {
  UmlCommand,
} from '../commands/uml-command';

export interface ProjectOperation {
  operationId: string;
  projectId: string;
  clientId: string;
  baseRevision: number;
  command: UmlCommand;
}

export interface CollaborationActor {
  id: string;
  displayName: string;
}

export interface ProjectOperationApplied {
  type: 'OPERATION_APPLIED';
  operationId: string;
  projectId: string;
  clientId: string;
  revision: number;
  command: UmlCommand;
  actor: CollaborationActor;
  appliedAt: string;
}

export interface ProjectOperationRejected {
  type: 'OPERATION_REJECTED';
  operationId: string | null;
  projectId: string;
  clientId: string | null;
  code: string;
  message: string;
  currentRevision: number | null;
  rejectedAt: string;
}

export type CollaborationConnectionEvent =
  | {
      type: 'CONNECTED';
    }
  | {
      type: 'DISCONNECTED';
    }
  | {
      type: 'ERROR';
      message: string;
    };

export type CollaborationStatus =
  | 'connecting'
  | 'connected'
  | 'syncing'
  | 'disconnected'
  | 'resyncing'
  | 'local_changes'
  | 'conflict'
  | 'error';