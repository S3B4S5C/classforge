export type ActivitySource = 'MANUAL' | 'ASSISTANT_TEXT' | 'ASSISTANT_VOICE' | 'VISION' | 'XMI' | 'SYSTEM';

/** A full-document save containing several origins is a system consolidation. */
export class ActivityOrigins {
  private readonly sources = new Set<ActivitySource>();
  record(source: ActivitySource): void { this.sources.add(source); }
  clear(): void { this.sources.clear(); }
  source(): ActivitySource { return this.sources.size === 1 ? [...this.sources][0] : this.sources.size === 0 ? 'MANUAL' : 'SYSTEM'; }
}
