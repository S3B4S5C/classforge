import type {
  Project,
  ProjectDocument,
} from '../../model/project';
import type { ProjectOperationApplied } from '../../collaboration/collaboration-protocol';
import type { UmlCommand } from '../../commands/uml-command';
import { UmlCommandBus } from '../../commands/uml-command-bus';
import { UmlCommandExecutor } from '../../commands/uml-command-executor';

/**
 * Pure editor/session state used by ProjectWorkspaceStore.
 * It owns local command history and the last server-confirmed document/revision.
 * No Angular, HTTP or STOMP dependency lives here, which makes the invariants testable.
 */
export class WorkspaceDocumentSession {
  private readonly commandBus = new UmlCommandBus();
  private readonly authoritativeExecutor = new UmlCommandExecutor();

  private confirmedDocumentState: ProjectDocument | null = null;
  private confirmedRevisionState = 0;

  loadAuthoritative(project: Project, resetHistory: boolean): void {
    this.confirmedDocumentState = structuredClone(project.document);
    this.confirmedRevisionState = project.revision;
    if (resetHistory) {
      this.commandBus.load(project.document);
    } else {
      this.commandBus.markSaved(project.document);
    }
  }

  reset(): void {
    this.confirmedDocumentState = null;
    this.confirmedRevisionState = 0;
  }

  dispatch(command: UmlCommand): boolean {
    return this.commandBus.dispatch(command);
  }

  undoCommand(): UmlCommand | null {
    return this.commandBus.undoCommand();
  }

  redoCommand(): UmlCommand | null {
    return this.commandBus.redoCommand();
  }

  draft(): ProjectDocument {
    return this.commandBus.document();
  }

  dirty(): boolean {
    return this.commandBus.isDirty();
  }

  canUndo(): boolean {
    return this.commandBus.canUndo();
  }

  canRedo(): boolean {
    return this.commandBus.canRedo();
  }

  undoDepth(): number {
    return this.commandBus.undoDepth();
  }

  redoDepth(): number {
    return this.commandBus.redoDepth();
  }

  markSaved(document?: ProjectDocument): void {
    this.commandBus.markSaved(document);
  }

  confirmedDocument(): ProjectDocument | null {
    return this.confirmedDocumentState
      ? structuredClone(this.confirmedDocumentState)
      : null;
  }

  confirmedRevision(): number {
    return this.confirmedRevisionState;
  }

  applyConfirmed(operation: ProjectOperationApplied): ProjectDocument {
    if (!this.confirmedDocumentState) {
      throw new Error('Confirmed document is not loaded');
    }

    this.confirmedDocumentState = this.authoritativeExecutor.execute(
      this.confirmedDocumentState,
      operation.command,
    );
    this.confirmedRevisionState = operation.revision;
    return structuredClone(this.confirmedDocumentState);
  }

  installConfirmedIntoEditor(): void {
    if (!this.confirmedDocumentState) return;
    this.commandBus.load(this.confirmedDocumentState);
  }

  finishOwnSynchronization(): void {
    if (!this.confirmedDocumentState) return;
    this.commandBus.markSaved(this.confirmedDocumentState);
  }
}
