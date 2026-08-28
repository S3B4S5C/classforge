import {
  HttpErrorResponse,
} from '@angular/common/http';
import {
  computed,
  inject,
  Injectable,
  signal,
} from '@angular/core';
import {
  finalize,
} from 'rxjs';

import {
  commandMetadata,
  UmlCommand,
} from '../commands/uml-command';
import {
  UmlCommandBus,
} from '../commands/uml-command-bus';
import {
  UmlCommandError,
} from '../commands/uml-command-error';
import {
  normalizeUmlClassLayout,
  UML_CLASS_MIN_WIDTH,
  umlClassHeight,
} from '../diagram/uml-class-geometry';
import {
  ProjectApiService,
} from '../data/project-api.service';
import {
  BackendValidationError,
  BackendValidationViolation,
  DiagramNodeLayout,
  Project,
  ProjectDocument,
  ProjectValidationResult,
  UmlAttribute,
  UmlClass,
  UmlRelationship,
} from '../model/project';

export type ProjectSaveState =
  | 'saved'
  | 'dirty'
  | 'saving'
  | 'conflict'
  | 'error';

@Injectable()
export class ProjectWorkspaceStore {
  private readonly projectApi =
    inject(ProjectApiService);

  private readonly commandBus =
    new UmlCommandBus();

  private readonly projectState =
    signal<Project | null>(null);

  private readonly documentDraftState =
    signal<ProjectDocument | null>(null);

  readonly project =
    this.projectState.asReadonly();

  readonly documentDraft =
    this.documentDraftState.asReadonly();

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly validating = signal(false);
  readonly renaming = signal(false);
  readonly dirty = signal(false);

  readonly canUndo = signal(false);
  readonly canRedo = signal(false);
  readonly undoDepth = signal(0);
  readonly redoDepth = signal(0);

  readonly saveState =
    signal<ProjectSaveState>('saved');

  readonly errorMessage =
    signal<string | null>(null);

  readonly conflictRevision =
    signal<number | null>(null);

  readonly validationViolations =
    signal<BackendValidationViolation[]>([]);

  readonly validationResult =
    signal<ProjectValidationResult | null>(null);

  readonly validationRequestError =
    signal<string | null>(null);

  readonly validationCheckedAt =
    signal<string | null>(null);

  readonly revision = computed(
    () =>
      this.projectState()?.revision
      ?? 0,
  );

  readonly lastSavedAt = computed(
    () =>
      this.projectState()?.updatedAt
      ?? null,
  );

  readonly classes = computed(
    () =>
      this.documentDraftState()
        ?.umlModel.classes
      ?? [],
  );

  readonly relationships = computed(
    () =>
      this.documentDraftState()
        ?.umlModel.relationships
      ?? [],
  );

  load(
    projectId: string,
  ): void {
    this.loading.set(true);
    this.resetTransientState();

    this.projectApi
      .get(projectId)
      .pipe(
        finalize(
          () => this.loading.set(false),
        ),
      )
      .subscribe({
        next: (project) => {
          this.projectState.set(project);

          this.commandBus.load(
            project.document,
          );

          this.syncDraftFromBus(
            false,
          );
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos abrir este proyecto. Puede que no exista o que no tengas acceso.',
          );

          this.saveState.set('error');
        },
      });
  }

  rename(
    name: string,
  ): void {
    const project =
      this.projectState();

    if (
      !project
      || this.renaming()
    ) {
      return;
    }

    this.renaming.set(true);
    this.errorMessage.set(null);

    this.projectApi
      .rename(
        project.id,
        { name },
      )
      .pipe(
        finalize(
          () =>
            this.renaming.set(false),
        ),
      )
      .subscribe({
        next: (updated) => {
          this.projectState.set(
            updated,
          );
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos cambiar el nombre del proyecto.',
          );
        },
      });
  }

  addClass(
    name: string,
  ): void {
    const id =
      crypto.randomUUID();

    const umlClass: UmlClass = {
      id,
      name,
      attributes: [],
    };

    this.dispatch({
      ...commandMetadata(),
      type: 'CREATE_CLASS',
      umlClass,
      layout:
        this.defaultLayoutForIndex(
          this.classes().length,
        ),
    });
  }

  updateClass(
    classId: string,
    changes: Pick<UmlClass, 'name'>,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'RENAME_CLASS',
      classId,
      name: changes.name,
    });
  }

  removeClass(
    classId: string,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'DELETE_CLASS',
      classId,
    });
  }

  addAttribute(
    classId: string,
    attribute: Omit<UmlAttribute, 'id'>,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'ADD_ATTRIBUTE',
      classId,
      attribute: {
        ...attribute,
        id: crypto.randomUUID(),
      },
    });
  }

  updateAttribute(
    classId: string,
    attributeId: string,
    changes: Omit<UmlAttribute, 'id'>,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'UPDATE_ATTRIBUTE',
      classId,
      attribute: {
        ...changes,
        id: attributeId,
      },
    });
  }

  removeAttribute(
    classId: string,
    attributeId: string,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'DELETE_ATTRIBUTE',
      classId,
      attributeId,
    });
  }

  addRelationship(
    relationship:
      Omit<UmlRelationship, 'id'>,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'CREATE_RELATIONSHIP',
      relationship: {
        ...relationship,
        id: crypto.randomUUID(),
      },
    });
  }

  updateRelationship(
    relationshipId: string,
    changes:
      Omit<UmlRelationship, 'id'>,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'UPDATE_RELATIONSHIP',
      relationship: {
        ...changes,
        id: relationshipId,
      },
    });
  }

  removeRelationship(
    relationshipId: string,
  ): void {
    this.dispatch({
      ...commandMetadata(),
      type: 'DELETE_RELATIONSHIP',
      relationshipId,
    });
  }

  updateNodeLayout(
    classId: string,
    layout: DiagramNodeLayout,
  ): void {
    const umlClass =
      this.classes().find(
        (candidate) =>
          candidate.id === classId,
      );

    if (!umlClass) {
      return;
    }

    const normalized =
      normalizeUmlClassLayout(
        layout,
        umlClass.attributes.length,
      );

    const current =
      this.documentDraftState()
        ?.layout.nodes[classId];

    if (
      current
      && current.x === normalized.x
      && current.y === normalized.y
      && current.width
        === normalized.width
      && current.height
        === normalized.height
    ) {
      return;
    }

    this.dispatch({
      ...commandMetadata(),
      type: 'MOVE_CLASS',
      classId,
      layout: normalized,
    });
  }

  undo(): void {
    if (!this.commandBus.undo()) {
      return;
    }

    this.syncDraftFromBus(true);
  }

  redo(): void {
    if (!this.commandBus.redo()) {
      return;
    }

    this.syncDraftFromBus(true);
  }

  validateDocument(): void {
    const project =
      this.projectState();

    const document =
      this.documentDraftState();

    if (
      !project
      || !document
      || this.validating()
    ) {
      return;
    }

    this.validating.set(true);
    this.validationRequestError.set(null);

    this.projectApi
      .validateDocument(
        project.id,
        {
          document,
        },
      )
      .pipe(
        finalize(
          () =>
            this.validating.set(false),
        ),
      )
      .subscribe({
        next: (result) => {
          this.validationResult.set(
            result,
          );

          this.validationCheckedAt.set(
            new Date().toISOString(),
          );
        },
        error: () => {
          this.validationResult.set(null);
          this.validationCheckedAt.set(
            null,
          );

          this.validationRequestError.set(
            'No pudimos validar el modelo. Verifica la conexion con el backend e intenta nuevamente.',
          );
        },
      });
  }

  saveDocument(): void {
    const project =
      this.projectState();

    const document =
      this.documentDraftState();

    if (
      !project
      || !document
      || !this.dirty()
      || this.saving()
    ) {
      return;
    }

    this.saving.set(true);
    this.saveState.set('saving');
    this.errorMessage.set(null);
    this.conflictRevision.set(null);
    this.validationViolations.set([]);
    this.validationResult.set(null);
    this.validationRequestError.set(null);
    this.validationCheckedAt.set(null);

    this.projectApi
      .saveDocument(
        project.id,
        {
          baseRevision:
            project.revision,
          document,
        },
      )
      .pipe(
        finalize(
          () =>
            this.saving.set(false),
        ),
      )
      .subscribe({
        next: (updated) => {
          this.projectState.set(
            updated,
          );

          this.commandBus.markSaved(
            updated.document,
          );

          this.syncDraftFromBus(
            false,
          );

          this.validationViolations.set(
            [],
          );
        },
        error: (
          error: HttpErrorResponse,
        ) => {
          if (
            error.status === 409
            && error.error?.error
              === 'PROJECT_REVISION_CONFLICT'
          ) {
            this.conflictRevision.set(
              Number(
                error.error
                  .currentRevision,
              ),
            );

            this.saveState.set(
              'conflict',
            );

            this.errorMessage.set(
              'El proyecto cambio en otra sesion. Recarga antes de volver a guardar.',
            );

            return;
          }

          if (
            error.status === 400
            && error.error?.error
              === 'VALIDATION_ERROR'
          ) {
            const validation =
              error.error as BackendValidationError;

            this.validationViolations.set(
              validation.violations
              ?? [],
            );

            this.saveState.set(
              'error',
            );

            this.errorMessage.set(
              validation.message
              || 'El modelo contiene datos invalidos.',
            );

            return;
          }

          this.saveState.set('error');

          this.errorMessage.set(
            'No pudimos guardar el documento.',
          );
        },
      });
  }

  private dispatch(
    command: UmlCommand,
  ): void {
    try {
      const changed =
        this.commandBus.dispatch(
          command,
        );

      if (!changed) {
        return;
      }

      this.syncDraftFromBus(true);
    } catch (error) {
      if (
        error instanceof UmlCommandError
      ) {
        this.errorMessage.set(
          error.message,
        );

        this.saveState.set(
          this.dirty()
            ? 'dirty'
            : 'saved',
        );

        return;
      }

      throw error;
    }
  }

  private syncDraftFromBus(
    clearDiagnostics: boolean,
  ): void {
    this.documentDraftState.set(
      this.commandBus.document(),
    );

    const isDirty =
      this.commandBus.isDirty();

    this.dirty.set(isDirty);

    this.canUndo.set(
      this.commandBus.canUndo(),
    );

    this.canRedo.set(
      this.commandBus.canRedo(),
    );

    this.undoDepth.set(
      this.commandBus.undoDepth(),
    );

    this.redoDepth.set(
      this.commandBus.redoDepth(),
    );

    this.conflictRevision.set(null);

    if (clearDiagnostics) {
      this.clearValidationFeedback();
      this.errorMessage.set(null);
    }

    this.saveState.set(
      isDirty
        ? 'dirty'
        : 'saved',
    );
  }

  private resetTransientState(): void {
    this.errorMessage.set(null);
    this.conflictRevision.set(null);
    this.validationViolations.set([]);
    this.validationResult.set(null);
    this.validationRequestError.set(null);
    this.validationCheckedAt.set(null);
    this.dirty.set(false);
    this.canUndo.set(false);
    this.canRedo.set(false);
    this.undoDepth.set(0);
    this.redoDepth.set(0);
    this.saveState.set('saved');
  }

  private clearValidationFeedback(): void {
    this.validationViolations.set([]);
    this.validationResult.set(null);
    this.validationRequestError.set(null);
    this.validationCheckedAt.set(null);
  }

  private defaultLayoutForIndex(
    index: number,
  ): DiagramNodeLayout {
    const safeIndex =
      Math.max(index, 0);

    const column =
      safeIndex % 3;

    const row =
      Math.floor(
        safeIndex / 3,
      );

    return {
      x: 80 + (column * 300),
      y: 80 + (row * 220),
      width: UML_CLASS_MIN_WIDTH,
      height: umlClassHeight(0),
    };
  }
}