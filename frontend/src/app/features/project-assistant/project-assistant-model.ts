import type { AssistantPlanResponse } from '../../projects/assistant/assistant-model';

export type AssistantMode = 'ASK' | 'REVIEW' | 'EDIT' | 'SUMMARY_CURRENT';
export interface AssistantReviewFinding {
  severity: string;
  category: string;
  message: string;
  elementRef: string | null;
}
export interface AssistantReview {
  id: string;
  projectId: string;
  projectRevision: number;
  requestedBy: string;
  summary: string;
  findings: AssistantReviewFinding[];
  createdAt: string;
}
export interface ProjectAiMessage {
  id: string;
  sequence: number;
  projectId: string;
  requestId: string;
  authorUserId: string | null;
  authorDisplayName: string;
  role: string;
  type: AssistantMode;
  content: string;
  projectRevision: number;
  createdAt: string;
  review: AssistantReview | null;
  plan: AssistantPlanResponse | null;
}
export interface AssistantMessagesPage { messages: ProjectAiMessage[]; hasMore: boolean; }
export interface AssistantMessageRequest { content: string; mode: AssistantMode; requestId: string; }
export interface AssistantMessageResponse {
  userMessage: ProjectAiMessage;
  assistantMessage: ProjectAiMessage;
  projectRevision: number;
}
export interface ProjectAssistantEvent {
  type: 'ASSISTANT_MESSAGE_CREATED' | 'ASSISTANT_RESPONSE_CREATED' | 'ASSISTANT_REVIEW_COMPLETED';
  projectId: string;
  message: ProjectAiMessage;
}
