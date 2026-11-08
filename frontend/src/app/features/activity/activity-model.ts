export interface Paged<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface ProjectActivity {
  id: string; projectId: string; operationId: string; actorUserId: string; actorDisplayName: string;
  revisionBefore: number; revisionAfter: number; operationType: string; source: string;
  description: string; payloadJson: string; sessionId: string; createdAt: string;
}
export interface ActivityParticipant { userId: string; displayName: string; changes: number; }
export interface ProjectContinuity {
  projectId: string; firstVisit: boolean; lastSeenAt: string | null; lastSeenRevision: number;
  currentRevision: number; newActivityCount: number; activities: ProjectActivity[];
  participants: ActivityParticipant[]; page: number; size: number; hasMore: boolean;
}
export interface ProjectSession {
  id: string; projectId: string; startedAt: string; endedAt: string | null; startRevision: number;
  endRevision: number; summary: string; status: 'OPEN' | 'CLOSED'; closeReason: string | null;
  totalOperations: number; totalChanges: number; operationCounts: Record<string, number>;
  participants: Array<ActivityParticipant & { operations: number }>;
}
export interface ActivityFilters {
  actorId?: string; from?: string; to?: string; afterRevision?: number; beforeRevision?: number;
  operationType?: string; sessionId?: string;
}
export const operationLabels: Record<string, string> = {
  CREATE_CLASS: 'Clase creada', RENAME_CLASS: 'Clase renombrada', DELETE_CLASS: 'Clase eliminada',
  RESTORE_CLASS: 'Clase restaurada', ADD_ATTRIBUTE: 'Atributo añadido', UPDATE_ATTRIBUTE: 'Atributo actualizado',
  DELETE_ATTRIBUTE: 'Atributo eliminado', CREATE_RELATIONSHIP: 'Relación creada', UPDATE_RELATIONSHIP: 'Relación actualizada',
  DELETE_RELATIONSHIP: 'Relación eliminada', MOVE_CLASS: 'Posición o tamaño', BATCH: 'Lote de cambios', SAVE_DOCUMENT: 'Documento guardado',
};
export const sourceLabels: Record<string, string> = {
  MANUAL: 'Manual', ASSISTANT_TEXT: 'IA · texto', ASSISTANT_VOICE: 'IA · voz', VISION: 'Imagen', XMI: 'Importación XMI', SYSTEM: 'Sistema',
};
