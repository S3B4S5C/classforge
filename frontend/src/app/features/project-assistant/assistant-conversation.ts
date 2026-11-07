import type { AssistantMode, AssistantMessageRequest, AssistantMessagesPage, ProjectAiMessage } from './project-assistant-model';

export interface ConversationToken { projectId: string | null; generation: number; }

/** Framework-independent conversation identity, ordering and page boundaries. */
export class AssistantConversation {
  messages: ProjectAiMessage[] = [];
  hasMore = false;
  before: number | undefined;
  syncedThrough: number | undefined;
  private current: ConversationToken = { projectId: null, generation: 0 };
  private pending: AssistantMessageRequest | null = null;
  open(projectId: string | null): ConversationToken {
    this.current = { projectId, generation: this.current.generation + 1 };
    this.messages = [];
    this.hasMore = false;
    this.before = undefined;
    this.syncedThrough = undefined;
    this.pending = null;
    return this.current;
  }
  token(): ConversationToken { return this.current; }
  accepts(token: ConversationToken): boolean {
    return token.projectId === this.current.projectId && token.generation === this.current.generation;
  }
  merge(token: ConversationToken, incoming: ProjectAiMessage[]): void {
    if (!this.accepts(token)) return;
    const byId = new Map(this.messages.map(message => [message.id, message]));
    for (const message of incoming) {
      if (message.projectId !== token.projectId) continue;
      byId.set(message.id, message);
      if (message.role.toUpperCase() === 'ASSISTANT' && message.requestId === this.pending?.requestId) {
        this.pending = null;
      }
    }
    this.messages = [...byId.values()].sort((a, b) => a.sequence - b.sequence || a.id.localeCompare(b.id));
  }
  page(token: ConversationToken, page: AssistantMessagesPage): void {
    if (!this.accepts(token)) return;
    this.merge(token, page.messages);
    const sequences = page.messages.filter(message => message.projectId === token.projectId).map(message => message.sequence);
    if (this.syncedThrough === undefined) this.syncedThrough = Math.max(0, ...sequences);
    const oldest = sequences.length ? Math.min(...sequences) : this.before;
    if (this.before === undefined || oldest === undefined || oldest <= this.before) {
      this.before = oldest;
      this.hasMore = page.hasMore && sequences.length > 0;
    }
  }
  prepare(content: string, mode: AssistantMode, requestId: string): AssistantMessageRequest {
    this.pending = { content, mode, requestId };
    return this.pending;
  }
  retry(): AssistantMessageRequest | null { return this.pending; }
  reconciled(token: ConversationToken, sequence?: number): void {
    if (this.accepts(token) && sequence !== undefined) this.syncedThrough = Math.max(this.syncedThrough ?? 0, sequence);
  }
  reconcileBefore(page: AssistantMessagesPage, knownSequence: number): number | null {
    const oldest = Math.min(...page.messages.map(message => message.sequence));
    return page.hasMore && page.messages.length > 0 && oldest > knownSequence ? oldest : null;
  }
}
