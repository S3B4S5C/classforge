import {
  UmlCommand,
} from '../commands/uml-command';

export type AssistantTypeSource =
  | 'EXPLICIT'
  | 'INFERRED'
  | 'DEFAULT';

export interface AssistantAttributePlan {
  name: string | null;
  dataType: string | null;
  customTypeName: string | null;
  visibility: string | null;
  nullable: boolean | null;
  identifier: boolean | null;
  typeSource:
    AssistantTypeSource | null;
}

export interface AssistantPlanAction {
  type:
    | 'CREATE_CLASS'
    | 'RENAME_CLASS'
    | 'DELETE_CLASS'
    | 'ADD_ATTRIBUTES'
    | 'UPDATE_ATTRIBUTE'
    | 'DELETE_ATTRIBUTE'
    | 'CREATE_RELATIONSHIP'
    | 'UPDATE_RELATIONSHIP'
    | 'DELETE_RELATIONSHIP';

  className: string | null;
  newName: string | null;
  attributes: AssistantAttributePlan[];
  attributeName: string | null;
  newAttributeName: string | null;
  dataType: string | null;
  customTypeName: string | null;
  visibility: string | null;
  nullable: boolean | null;
  identifier: boolean | null;
  sourceClassName: string | null;
  targetClassName: string | null;
  relationshipType: string | null;
  sourceLower: number | null;
  sourceUpper: number | null;
  targetLower: number | null;
  targetUpper: number | null;
}

export interface AssistantSemanticPlan {
  summary: string;
  actions: AssistantPlanAction[];
}

export interface AssistantPlanResponse {
  source: 'TEXT' | 'VOICE';
  transcript: string;
  baseRevision: number;
  summary: string;
  plan: AssistantSemanticPlan;
  command: UmlCommand;
}

export interface AssistantRuntimeStatus {
  name: string;
  available: boolean;
  state:
    | 'READY'
    | 'LOADING'
    | 'UNAVAILABLE'
    | 'UNREACHABLE'
    | string;
  latencyMs: number;
  message: string;
}

export interface AssistantRuntimeHealthResponse {
  readyForText: boolean;
  readyForVoice: boolean;
  llama: AssistantRuntimeStatus;
  whisper: AssistantRuntimeStatus;
  checkedAt: string;
}
