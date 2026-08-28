export type PresenceElementType =
  | 'CLASS'
  | 'RELATIONSHIP';

export interface PresenceSelection {
  elementId: string;
  elementType: PresenceElementType;
}

export interface PresenceCursor {
  x: number;
  y: number;
}

export type PresenceEventType =
  | 'USER_JOINED'
  | 'USER_LEFT'
  | 'USER_SELECTED_ELEMENT'
  | 'USER_MOVED_CURSOR';

export interface PresenceEventRequest {
  eventId: string;
  clientId: string;
  type: PresenceEventType;
  selectedElement:
    PresenceSelection | null;
  cursor:
    PresenceCursor | null;
}

export interface PresenceActor {
  id: string;
  displayName: string;
}

export interface PresenceParticipant {
  clientId: string;
  actor: PresenceActor;
  selectedElement:
    PresenceSelection | null;
  cursor:
    PresenceCursor | null;
  joinedAt: string;
}

export interface ProjectPresenceEvent {
  type: PresenceEventType;
  projectId: string;
  participant: PresenceParticipant;
  occurredAt: string;
}

export interface ProjectPresenceSnapshot {
  type: 'PRESENCE_SNAPSHOT';
  projectId: string;
  participants: PresenceParticipant[];
}