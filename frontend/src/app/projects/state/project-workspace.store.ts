import {
  HttpErrorResponse,
} from '@angular/common/http';
import {
  computed,
  DestroyRef,
  inject,
  Injectable,
  signal,
} from '@angular/core';
import {
  takeUntilDestroyed,
} from '@angular/core/rxjs-interop';
import {
  finalize,
} from 'rxjs';

import {
  AuthService,
} from '../../auth/data/auth.service';
import {
  ProjectCollaborationService,
} from '../collaboration/project-collaboration.service';
import {
  CollaborationStatus,
  ProjectOperation,
  ProjectOperationApplied,
  ProjectOperationRejected,
} from '../collaboration/collaboration-protocol';
import {
  commandMetadata,
  UmlCommand,
} from '../commands/uml-command';
import {
  UmlCommandError,
} from '../commands/uml-command-error';
import {
  ProjectApiService,
} from '../data/project-api.service';
import { WorkspaceDocumentSession } from './workspace/workspace-document-session';
import { WorkspaceCollaborationState } from './workspace/workspace-collaboration-state';
import {
  normalizeUmlClassLayout,
  UML_CLASS_MIN_WIDTH,
  umlClassHeight,
} from '../diagram/uml-class-geometry';
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

  private readonly auth =
    inject(AuthService);

  private readonly collaboration =
    inject(ProjectCollaborationService);

  private readonly destroyRef =
    inject(DestroyRef);

  private readonly documentSession =
    new WorkspaceDocumentSession();

  private readonly realtimeState =
    new WorkspaceCollaborationState();

  private readonly projectState =
    signal<Project | null>(null);

  private readonly documentDraftState =
    signal<ProjectDocument | null>(null);

  private readonly historyCanUndo =
    signal(false);

  private readonly historyCanRedo =
    signal(false);

  private readonly clientId =
    this.collaboration.clientId;

  readonly project =
    this.projectState.asReadonly();

  readonly documentDraft =
    this.documentDraftState.asReadonly();

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly validating = signal(false);
  readonly renaming = signal(false);
  readonly dirty = signal(false);

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

  readonly collaborationStatus =
    signal<CollaborationStatus>(
      'disconnected',
    );

  readonly collaborationMessage =
    signal<string | null>(null);

  readonly pendingOperationCount =
    signal(0);

  readonly realtimeActive =
    computed(
      () =>
        this.collaborationStatus()
          === 'connected'
        || this.collaborationStatus()
          === 'syncing'
        || this.collaborationStatus()
          === 'resyncing',
    );

  readonly collaborationHistoryBusy =
    computed(
      () =>
        this.pendingOperationCount() > 0
        || this.collaborationStatus()
          === 'connecting'
        || this.collaborationStatus()
          === 'syncing'
        || this.collaborationStatus()
          === 'resyncing'
        || this.collaborationStatus()
          === 'conflict',
    );

  readonly canUndo =
    computed(
      () =>
        !this.collaborationHistoryBusy()
        && this.historyCanUndo(),
    );

  readonly canRedo =
    computed(
      () =>
        !this.collaborationHistoryBusy()
        && this.historyCanRedo(),
    );

  readonly undoDepth = signal(0);
  readonly redoDepth = signal(0);

  readonly collaborationStatusText =
    computed(
      () => {
        switch (
          this.collaborationStatus()
        ) {
          case 'connecting':
            return 'Conectando...';
          case 'connected':
            return 'Sincronizado';
          case 'syncing':
            return 'Sincronizando...';
          case 'resyncing':
            return 'Resincronizando...';
          case 'local_changes':
            return 'Cambios locales';
          case 'conflict':
            return 'Conflicto';
          case 'error':
            return 'Error de conexion';
          default:
            return 'Sin conexion';
        }
      },
    );

  readonly collaborationStatusIcon =
    computed(
      () => {
        switch (
          this.collaborationStatus()
        ) {
          case 'connecting':
          case 'syncing':
          case 'resyncing':
            return 'sync';
          case 'connected':
            return 'cloud_done';
          case 'local_changes':
            return 'cloud_off';
          case 'conflict':
            return 'sync_problem';
          case 'error':
            return 'cloud_alert';
          default:
            return 'cloud_off';
        }
      },
    );

  readonly revision =
    computed(
      () =>
        this.projectState()?.revision
        ?? 0,
    );

  readonly lastSavedAt =
    computed(
      () =>
        this.projectState()?.updatedAt
        ?? null,
    );

  readonly classes =
    computed(
      () =>
        this.documentDraftState()
          ?.umlModel.classes
        ?? [],
    );

  readonly relationships =
    computed(
      () =>
        this.documentDraftState()
          ?.umlModel.relationships
        ?? [],
    );

  constructor() {
    this.collaboration
      .connectionEvents$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (event) => {
          switch (event.type) {
            case 'CONNECTED':
              this.handleConnected();
              break;

            case 'DISCONNECTED':
              this.handleDisconnected();
              break;

            case 'ERROR':
              this.handleConnectionError(
                event.message,
              );
              break;
          }
        },
      );

    this.collaboration
      .appliedOperations$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (operation) =>
          this.handleAppliedOperation(
            operation,
          ),
      );

    this.collaboration
      .rejectedOperations$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (rejection) =>
          this.handleRejectedOperation(
            rejection,
          ),
      );

    this.destroyRef.onDestroy(
      () => {
        this.collaboration.disconnect();
      },
    );
  }

  load(
    projectId: string,
  ): void {
    this.loading.set(true);
    this.collaboration.disconnect();
    this.resetTransientState();

    this.projectApi
      .get(projectId)
      .pipe(
        finalize(
          () =>
            this.loading.set(false),
        ),
      )
      .subscribe({
        next: (project) => {
          this.installAuthoritativeProject(
            project,
            true,
          );

          this.startCollaboration(
            project.id,
          );
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos abrir este proyecto. Puede que no exista o que no tengas acceso.',
          );

          this.saveState.set(
            'error',
          );
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
          this.projectState.set({
            ...updated,
            document:
              this.projectState()
                ?.document
              ?? updated.document,
          });
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

    this.dispatch({
      ...commandMetadata(),
      type: 'CREATE_CLASS',
      umlClass: {
        id,
        name,
        attributes: [],
      },
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
    if (!this.canUndo()) {
      return;
    }

    try {
      const inverseCommand =
        this.documentSession.undoCommand();

      if (!inverseCommand) {
        return;
      }

      this.syncDraftFromBus(true);

      if (
        this.collaborationStatus()
          === 'connected'
      ) {
        this.publishOptimistic(
          inverseCommand,
        );
      }
    } catch (error) {
      this.handleHistoryCommandError(
        error,
        'No pudimos deshacer este cambio.',
      );
    }
  }

  redo(): void {
    if (!this.canRedo()) {
      return;
    }

    try {
      const forwardCommand =
        this.documentSession.redoCommand();

      if (!forwardCommand) {
        return;
      }

      this.syncDraftFromBus(true);

      if (
        this.collaborationStatus()
          === 'connected'
      ) {
        this.publishOptimistic(
          forwardCommand,
        );
      }
    } catch (error) {
      this.handleHistoryCommandError(
        error,
        'No pudimos rehacer este cambio.',
      );
    }
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
      || this.realtimeActive()
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
          this.installAuthoritativeProject(
            updated,
            false,
          );

          this.validationViolations.set(
            [],
          );

          this.collaborationMessage.set(
            null,
          );

          this.startCollaboration(
            updated.id,
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

          this.saveState.set(
            'error',
          );

          this.errorMessage.set(
            'No pudimos guardar el documento.',
          );
        },
      });
  }

  assistantPlanningBlockReason():
    string | null {
    const status =
      this.collaborationStatus();

    if (
      this.pendingOperationCount() > 0
    ) {
      return 'Espera a que terminen de sincronizarse las operaciones pendientes.';
    }

    if (
      status === 'connecting'
      || status === 'syncing'
      || status === 'resyncing'
      || status === 'conflict'
    ) {
      return 'El proyecto se esta sincronizando. Espera antes de usar el Asistente.';
    }

    if (
      this.dirty()
      && !this.realtimeActive()
    ) {
      return 'Hay cambios locales sin guardar. Guardalos antes de pedir un plan al Asistente.';
    }

    return null;
  }

  applyAssistantCommand(
    command: UmlCommand,
    expectedRevision: number,
  ): {
    applied: boolean;
    reason: string | null;
  } {
    if (
      this.revision()
        !== expectedRevision
    ) {
      return {
        applied: false,
        reason:
          'El proyecto cambio desde que se genero el plan. Genera el plan nuevamente.',
      };
    }

    const blockReason =
      this.assistantPlanningBlockReason();

    if (blockReason) {
      return {
        applied: false,
        reason: blockReason,
      };
    }

    this.dispatch(command);

    return {
      applied: true,
      reason: null,
    };
  }

  reconnectCollaboration(): void {
    const project =
      this.projectState();

    if (!project) {
      return;
    }

    if (this.dirty()) {
      this.collaborationStatus.set(
        'local_changes',
      );

      this.collaborationMessage.set(
        'Guarda primero los cambios locales antes de reactivar la colaboracion.',
      );

      return;
    }

    this.startCollaboration(
      project.id,
    );
  }

  private dispatch(
    command: UmlCommand,
  ): void {
    try {
      const changed =
        this.documentSession.dispatch(
          command,
        );

      if (!changed) {
        return;
      }

      this.syncDraftFromBus(true);

      if (
        this.collaborationStatus()
          === 'connected'
        || this.collaborationStatus()
          === 'syncing'
      ) {
        this.publishOptimistic(
          command,
        );
      }
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

  private handleHistoryCommandError(
    error: unknown,
    fallbackMessage: string,
  ): void {
    if (
      error instanceof UmlCommandError
    ) {
      this.errorMessage.set(
        error.message,
      );
      return;
    }

    this.errorMessage.set(
      fallbackMessage,
    );
  }

  private publishOptimistic(
    command: UmlCommand,
  ): void {
    const project =
      this.projectState();

    if (!project) {
      return;
    }

    const operation:
      ProjectOperation = {
        operationId:
          crypto.randomUUID(),
        projectId:
          project.id,
        clientId:
          this.clientId,
        baseRevision:
          this.documentSession.confirmedRevision()
          + this.realtimeState.pendingCount(),
        command,
      };

    const published =
      this.collaboration.publish(
        operation,
      );

    if (!published) {
      this.collaborationStatus.set(
        'disconnected',
      );

      this.collaborationMessage.set(
        'La operacion quedo local porque el canal colaborativo no estaba disponible.',
      );

      return;
    }

    this.realtimeState.addPending(operation);

    this.pendingOperationCount.set(
      this.realtimeState.pendingCount(),
    );

    this.collaborationStatus.set(
      'syncing',
    );
  }

  private startCollaboration(
    projectId: string,
  ): void {
    const token =
      this.auth.token();

    if (!token) {
      this.collaborationStatus.set(
        'disconnected',
      );

      return;
    }

    this.collaborationStatus.set(
      'connecting',
    );

    this.collaboration.connect(
      projectId,
      token,
    );
  }

  private handleConnected(): void {
    if (
      this.realtimeState.resyncRequiredOnReconnect()
    ) {
      this.realtimeState.clearReconnectResyncRequirement();

      this.beginResync(
        'Recuperando el estado autoritativo despues de una desconexion...',
      );

      return;
    }

    if (this.dirty()) {
      this.collaborationStatus.set(
        'local_changes',
      );

      this.collaborationMessage.set(
        'Hay cambios locales sin sincronizar. Guardalos antes de reactivar la colaboracion.',
      );

      this.collaboration.disconnect();
      return;
    }

    /*
     * El REST GET inicial puede haber ocurrido justo antes de una
     * operacion remota. Nos suscribimos primero y luego hacemos un
     * resync, almacenando broadcasts que lleguen durante el GET.
     */
    this.beginResync(null);
  }

  private handleDisconnected(): void {
    if (
      this.collaborationStatus()
        === 'local_changes'
    ) {
      return;
    }

    if (
      this.realtimeState.pendingCount() > 0
    ) {
      this.realtimeState.clearPending();
      this.pendingOperationCount.set(0);

      this.realtimeState.requireResyncOnReconnect();

      this.collaborationMessage.set(
        'La conexion se interrumpio con operaciones pendientes. Al reconectar se recuperara el estado del servidor.',
      );
    }

    this.collaborationStatus.set(
      'disconnected',
    );
  }

  private handleConnectionError(
    message: string,
  ): void {
    if (
      this.collaborationStatus()
        === 'local_changes'
    ) {
      return;
    }

    this.collaborationStatus.set(
      'error',
    );

    this.collaborationMessage.set(
      message,
    );
  }

  private handleAppliedOperation(
    operation: ProjectOperationApplied,
  ): void {
    const project =
      this.projectState();

    if (
      !project
      || operation.projectId
        !== project.id
    ) {
      return;
    }

    if (
      this.collaborationStatus()
        === 'resyncing'
    ) {
      this.realtimeState.buffer(operation);
      return;
    }

    const revisionDisposition =
      this.realtimeState.classifyRevision(
        this.documentSession.confirmedRevision(),
        operation.revision,
      );

    if (revisionDisposition === 'stale') {
      this.realtimeState.removePending(
        operation.operationId,
      );

      this.pendingOperationCount.set(
        this.realtimeState.pendingCount(),
      );

      return;
    }

    if (revisionDisposition === 'gap') {
      this.beginResync(
        `Se detecto un salto de revision (${this.documentSession.confirmedRevision()} → ${operation.revision}). Recuperando el estado del servidor...`,
      );
      return;
    }

    const ownOperation =
      this.realtimeState.hasPending(
        operation.operationId,
      );

    try {
      this.applyToConfirmedDocument(
        operation,
      );
    } catch {
      this.beginResync(
        'La operacion recibida no pudo aplicarse sobre el estado confirmado. Recuperando el proyecto...',
      );
      return;
    }

    if (ownOperation) {
      this.realtimeState.removePending(
        operation.operationId,
      );

      this.pendingOperationCount.set(
        this.realtimeState.pendingCount(),
      );

      if (
        this.realtimeState.pendingCount() === 0
      ) {
        this.finishOwnSynchronization();
      } else {
        this.collaborationStatus.set(
          'syncing',
        );
      }

      return;
    }

    if (
      this.realtimeState.pendingCount() > 0
    ) {
      /*
       * El draft contiene comandos optimistas locales aplicados sobre
       * una base anterior al comando remoto. No intentamos rebase
       * automatico en CU06-002.
       */
      this.beginResync(
        'Otro cliente modifico el proyecto mientras habia operaciones locales pendientes. Resincronizando...',
      );
      return;
    }

    this.installConfirmedDocumentIntoEditor();

    this.collaborationStatus.set(
      'connected',
    );

    this.collaborationMessage.set(
      `Cambio de ${operation.actor.displayName} sincronizado.`,
    );
  }

  private handleRejectedOperation(
    rejection: ProjectOperationRejected,
  ): void {
    const project =
      this.projectState();

    if (
      !project
      || rejection.projectId
        !== project.id
    ) {
      return;
    }

    this.realtimeState.clearPending();
    this.pendingOperationCount.set(0);

    this.collaborationStatus.set(
      'conflict',
    );

    this.collaborationMessage.set(
      rejection.code
        === 'REVISION_CONFLICT'
        ? 'El servidor rechazo una operacion porque la revision cambio. Recuperando el estado mas reciente...'
        : `Operacion rechazada: ${rejection.message}`,
    );

    this.beginResync(
      this.collaborationMessage(),
    );
  }

  private beginResync(
    message: string | null,
  ): void {
    const project =
      this.projectState();

    if (!project) {
      return;
    }

    const generation =
      this.realtimeState.nextResyncGeneration();

    this.collaborationStatus.set(
      'resyncing',
    );

    if (message) {
      this.collaborationMessage.set(
        message,
      );
    }

    this.realtimeState.resetBuffer();

    this.projectApi
      .get(project.id)
      .subscribe({
        next: (fresh) => {
          if (
            generation
              !== this.realtimeState.currentResyncGeneration()
          ) {
            return;
          }

          this.realtimeState.clearPending();
          this.pendingOperationCount.set(0);

          this.installAuthoritativeProject(
            fresh,
            true,
          );

          const buffered =
            this.realtimeState.takeBufferedInRevisionOrder();

          for (
            const operation
            of buffered
          ) {
            const revisionDisposition =
              this.realtimeState.classifyRevision(
                this.documentSession.confirmedRevision(),
                operation.revision,
              );

            if (revisionDisposition === 'stale') {
              continue;
            }

            if (revisionDisposition === 'gap') {
              this.collaborationStatus.set(
                'connected',
              );

              this.beginResync(
                'Llegaron varias revisiones durante la resincronizacion. Verificando nuevamente...',
              );

              return;
            }

            try {
              this.applyToConfirmedDocument(
                operation,
              );
            } catch {
              this.collaborationStatus.set(
                'connected',
              );

              this.beginResync(
                'No pudimos reconstruir una operacion recibida durante la resincronizacion.',
              );

              return;
            }
          }

          this.installConfirmedDocumentIntoEditor();

          this.collaborationStatus.set(
            'connected',
          );

          if (!message) {
            this.collaborationMessage.set(
              null,
            );
          }
        },
        error: () => {
          if (
            generation
              !== this.realtimeState.currentResyncGeneration()
          ) {
            return;
          }

          this.collaborationStatus.set(
            'error',
          );

          this.collaborationMessage.set(
            'No pudimos recuperar el estado autoritativo del proyecto.',
          );
        },
      });
  }

  private applyToConfirmedDocument(
    operation: ProjectOperationApplied,
  ): void {
    const confirmedDocument =
      this.documentSession.applyConfirmed(
        operation,
      );

    const project =
      this.projectState();

    if (project) {
      this.projectState.set({
        ...project,
        revision:
          operation.revision,
        document:
          confirmedDocument,
        updatedAt:
          operation.appliedAt,
      });
    }
  }

  private finishOwnSynchronization(): void {
    if (!this.documentSession.confirmedDocument()) {
      return;
    }

    this.documentSession.finishOwnSynchronization();
    this.syncDraftFromBus(false);
    this.collaborationStatus.set('connected');
    this.collaborationMessage.set(null);
  }

  private installConfirmedDocumentIntoEditor(): void {
    if (!this.documentSession.confirmedDocument()) {
      return;
    }

    /* A remote change invalidates local history; authoritative state wins. */
    this.documentSession.installConfirmedIntoEditor();
    this.syncDraftFromBus(false);
  }

  private installAuthoritativeProject(
    project: Project,
    resetHistory: boolean,
  ): void {
    this.projectState.set(project);
    this.documentSession.loadAuthoritative(
      project,
      resetHistory,
    );
    this.syncDraftFromBus(false);
  }

  private syncDraftFromBus(
    clearDiagnostics: boolean,
  ): void {
    this.documentDraftState.set(
      this.documentSession.draft(),
    );

    const isDirty =
      this.documentSession.dirty();

    this.dirty.set(isDirty);
    this.historyCanUndo.set(
      this.documentSession.canUndo(),
    );
    this.historyCanRedo.set(
      this.documentSession.canRedo(),
    );
    this.undoDepth.set(
      this.documentSession.undoDepth(),
    );
    this.redoDepth.set(
      this.documentSession.redoDepth(),
    );
    this.conflictRevision.set(null);

    if (clearDiagnostics) {
      this.clearValidationFeedback();
      this.errorMessage.set(null);
    }

    this.saveState.set(
      isDirty ? 'dirty' : 'saved',
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
    this.historyCanUndo.set(false);
    this.historyCanRedo.set(false);
    this.undoDepth.set(0);
    this.redoDepth.set(0);
    this.saveState.set('saved');

    this.collaborationStatus.set('disconnected');
    this.collaborationMessage.set(null);
    this.pendingOperationCount.set(0);
    this.realtimeState.reset();
    this.documentSession.reset();
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