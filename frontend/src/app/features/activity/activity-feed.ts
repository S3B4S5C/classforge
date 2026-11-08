import type { ActivityFilters, Paged, ProjectActivity } from './activity-model';

/** Filter identity and stable revision cursor prevent duplicates and stale project responses. */
export class ActivityFeed {
  items: ProjectActivity[] = [];
  total = 0;
  hasMore = false;
  private generation = 0;
  private project = '';
  filters: ActivityFilters = {};
  reset(project: string, filters: ActivityFilters = {}): number {
    this.generation++; this.project = project; this.filters = { ...filters }; this.items = [];
    this.total = 0; this.hasMore = false; return this.generation;
  }
  token(): number { return this.generation; }
  accepts(token: number): boolean { return token === this.generation; }
  merge(token: number, page: Paged<ProjectActivity>, older = false): boolean {
    if (!this.accepts(token)) return false;
    const byId = new Map((older ? this.items : []).map(item => [item.id, item]));
    for (const item of page.items) if (item.projectId === this.project) byId.set(item.id, item);
    this.items = [...byId.values()].sort((a, b) => b.revisionAfter - a.revisionAfter);
    if (!older) this.total = page.totalElements;
    this.hasMore = page.totalElements > page.items.length;
    return true;
  }
  olderFilters(): ActivityFilters {
    const oldest = this.items.at(-1)?.revisionAfter;
    return { ...this.filters, beforeRevision: oldest === undefined ? this.filters.beforeRevision
      : Math.min(oldest, this.filters.beforeRevision ?? Number.MAX_SAFE_INTEGER) };
  }
  refresh(token: number, page: Paged<ProjectActivity>): boolean {
    if (!this.accepts(token)) return false;
    this.merge(token, page, true); this.total = page.totalElements;
    this.hasMore = this.items.length < this.total; return true;
  }
}
