import {
  ProjectDocument,
} from '../model/project';
import {
  UmlCommand,
} from './uml-command';
import {
  UmlCommandExecutor,
} from './uml-command-executor';

export interface UmlHistoryEntry {
  command: UmlCommand;
  before: ProjectDocument;
  after: ProjectDocument;
}

export class UmlCommandBus {
  private currentDocument:
    ProjectDocument | null = null;

  private savedFingerprint = '';

  private readonly undoStack:
    UmlHistoryEntry[] = [];

  private readonly redoStack:
    UmlHistoryEntry[] = [];

  constructor(
    private readonly executor =
      new UmlCommandExecutor(),
    private readonly maxHistory = 100,
  ) {}

  load(
    document: ProjectDocument,
  ): void {
    this.currentDocument =
      structuredClone(document);

    this.savedFingerprint =
      this.fingerprint(document);

    this.undoStack.length = 0;
    this.redoStack.length = 0;
  }

  dispatch(
    command: UmlCommand,
  ): boolean {
    const current =
      this.requireCurrent();

    const before =
      structuredClone(current);

    const after =
      this.executor.execute(
        before,
        command,
      );

    if (
      this.fingerprint(before)
        === this.fingerprint(after)
    ) {
      return false;
    }

    this.undoStack.push({
      command,
      before,
      after: structuredClone(after),
    });

    this.trimUndoStack();

    this.redoStack.length = 0;

    this.currentDocument =
      structuredClone(after);

    return true;
  }

  undo(): boolean {
    const entry =
      this.undoStack.pop();

    if (!entry) {
      return false;
    }

    this.currentDocument =
      structuredClone(entry.before);

    this.redoStack.push(entry);

    return true;
  }

  redo(): boolean {
    const entry =
      this.redoStack.pop();

    if (!entry) {
      return false;
    }

    this.currentDocument =
      structuredClone(entry.after);

    this.undoStack.push(entry);
    this.trimUndoStack();

    return true;
  }

  markSaved(
    persistedDocument?: ProjectDocument,
  ): void {
    if (persistedDocument) {
      this.currentDocument =
        structuredClone(
          persistedDocument,
        );
    }

    const current =
      this.requireCurrent();

    this.savedFingerprint =
      this.fingerprint(current);
  }

  document(): ProjectDocument {
    return structuredClone(
      this.requireCurrent(),
    );
  }

  isDirty(): boolean {
    const current =
      this.requireCurrent();

    return this.fingerprint(current)
      !== this.savedFingerprint;
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  undoDepth(): number {
    return this.undoStack.length;
  }

  redoDepth(): number {
    return this.redoStack.length;
  }

  clearHistory(): void {
    this.undoStack.length = 0;
    this.redoStack.length = 0;
  }

  private requireCurrent():
    ProjectDocument {
    if (!this.currentDocument) {
      throw new Error(
        'Project document is not loaded',
      );
    }

    return this.currentDocument;
  }

  private trimUndoStack(): void {
    const overflow =
      this.undoStack.length
      - this.maxHistory;

    if (overflow > 0) {
      this.undoStack.splice(
        0,
        overflow,
      );
    }
  }

  private fingerprint(
    document: ProjectDocument,
  ): string {
    return JSON.stringify(document);
  }
}