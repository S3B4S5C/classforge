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
  source: 'TEXT' | 'VOICE' | 'IMAGE';
  transcript: string;
  baseRevision: number;
  summary: string;
  plan: AssistantSemanticPlan;
  command: UmlCommand | null;
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
  readyForImage: boolean;
  llama: AssistantRuntimeStatus;
  whisper: AssistantRuntimeStatus;
  vision: AssistantRuntimeStatus;
  checkedAt: string;
}


export type AssistantImagePlanDisposition =
  | 'READY'
  | 'NO_CHANGES'
  | 'NO_ACTIONABLE_UML';

export interface AssistantImageEvidenceItem {
  kind: 'CLASS' | 'ATTRIBUTE' | 'RELATIONSHIP' | string;
  symbol: string;
  label: string;
  confidence: number | null;
  x: number | null;
  y: number | null;
  width: number | null;
  height: number | null;
}

export interface AssistantImageMetadata {
  filename: string;
  originalMediaType: string;
  normalizedMediaType: string;
  width: number;
  height: number;
  originalBytes: number;
  sha256: string;
  reencoded: boolean;
}

export interface AssistantImagePlanResponse
  extends AssistantPlanResponse {
  source: 'IMAGE';
  warnings: string[];
  confidence: number;
  image: AssistantImageMetadata;
  disposition: AssistantImagePlanDisposition;
  evidence: AssistantImageEvidenceItem[];
}
