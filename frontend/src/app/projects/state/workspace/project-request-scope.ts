export interface ProjectRequestToken { projectId: string; generation: number; }

/** Guards callbacks that can outlive a route or project selection. */
export class ProjectRequestScope {
  private generation = 0;
  private projectId: string | null = null;
  begin(projectId: string): ProjectRequestToken {
    this.projectId = projectId;
    return { projectId, generation: ++this.generation };
  }
  accepts(token: ProjectRequestToken): boolean {
    return token.projectId === this.projectId && token.generation === this.generation;
  }
  capture(): ProjectRequestToken | null {
    return this.projectId ? { projectId: this.projectId, generation: this.generation } : null;
  }
  guard<Args extends unknown[], Result>(token: ProjectRequestToken | null, callback: (...args: Args) => Result): (...args: Args) => Result | undefined {
    return (...args) => token && this.accepts(token) ? callback(...args) : undefined;
  }
  clear(): void { this.projectId = null; this.generation++; }
}
