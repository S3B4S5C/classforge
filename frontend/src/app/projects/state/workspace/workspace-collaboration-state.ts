import type {
  ProjectOperation,
  ProjectOperationApplied,
} from '../../collaboration/collaboration-protocol';


export type AppliedRevisionDisposition = 'stale' | 'next' | 'gap';

/** Pure bookkeeping for optimistic realtime operations and resync buffering. */
export class WorkspaceCollaborationState {
  private readonly pending = new Map<string, ProjectOperation>();
  private buffered: ProjectOperationApplied[] = [];
  private reconnectResyncRequired = false;
  private generation = 0;


  classifyRevision(
    confirmedRevision: number,
    incomingRevision: number,
  ): AppliedRevisionDisposition {
    if (incomingRevision <= confirmedRevision) {
      return 'stale';
    }
    return incomingRevision === confirmedRevision + 1
      ? 'next'
      : 'gap';
  }

  pendingCount(): number {
    return this.pending.size;
  }

  addPending(operation: ProjectOperation): void {
    this.pending.set(operation.operationId, operation);
  }

  hasPending(operationId: string): boolean {
    return this.pending.has(operationId);
  }

  removePending(operationId: string): void {
    this.pending.delete(operationId);
  }

  clearPending(): void {
    this.pending.clear();
  }

  buffer(operation: ProjectOperationApplied): void {
    this.buffered.push(operation);
  }

  resetBuffer(): void {
    this.buffered = [];
  }

  takeBufferedInRevisionOrder(): ProjectOperationApplied[] {
    const result = [...this.buffered].sort(
      (left, right) => left.revision - right.revision,
    );
    this.buffered = [];
    return result;
  }

  requireResyncOnReconnect(): void {
    this.reconnectResyncRequired = true;
  }

  clearReconnectResyncRequirement(): void {
    this.reconnectResyncRequired = false;
  }

  resyncRequiredOnReconnect(): boolean {
    return this.reconnectResyncRequired;
  }

  nextResyncGeneration(): number {
    this.generation += 1;
    return this.generation;
  }

  currentResyncGeneration(): number {
    return this.generation;
  }

  reset(): void {
    this.pending.clear();
    this.buffered = [];
    this.reconnectResyncRequired = false;
    this.generation += 1;
  }
}
