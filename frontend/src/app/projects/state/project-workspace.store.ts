import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize } from 'rxjs';

import {
  normalizeUmlClassLayout,
  UML_CLASS_MIN_WIDTH,
  umlClassHeight,
} from '../diagram/uml-class-geometry';
import { ProjectApiService } from '../data/project-api.service';
import {
  BackendValidationError,
  BackendValidationViolation,
  DiagramNodeLayout,
  Project,
  ProjectDocument,
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
  private readonly projectApi = inject(ProjectApiService);

  private readonly projectState = signal<Project | null>(null);
  private readonly documentDraftState =
    signal<ProjectDocument | null>(null);

  readonly project = this.projectState.asReadonly();
  readonly documentDraft = this.documentDraftState.asReadonly();

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly renaming = signal(false);
  readonly dirty = signal(false);
  readonly saveState = signal<ProjectSaveState>('saved');
  readonly errorMessage = signal<string | null>(null);
  readonly conflictRevision = signal<number | null>(null);
  readonly validationViolations =
    signal<BackendValidationViolation[]>([]);

  readonly revision = computed(
    () => this.projectState()?.revision ?? 0,
  );

  readonly lastSavedAt = computed(
    () => this.projectState()?.updatedAt ?? null,
  );

  readonly classes = computed(
    () => this.documentDraftState()?.umlModel.classes ?? [],
  );

  readonly relationships = computed(
    () =>
      this.documentDraftState()?.umlModel.relationships
      ?? [],
  );

  load(projectId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.conflictRevision.set(null);
    this.validationViolations.set([]);

    this.projectApi
      .get(projectId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (project) => {
          this.projectState.set(project);
          this.documentDraftState.set(
            structuredClone(project.document),
          );
          this.dirty.set(false);
          this.saveState.set('saved');
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos abrir este proyecto. Puede que no exista o que no tengas acceso.',
          );
          this.saveState.set('error');
        },
      });
  }

  rename(name: string): void {
    const project = this.projectState();

    if (!project || this.renaming()) {
      return;
    }

    this.renaming.set(true);
    this.errorMessage.set(null);

    this.projectApi
      .rename(project.id, { name })
      .pipe(finalize(() => this.renaming.set(false)))
      .subscribe({
        next: (updated) => {
          this.projectState.set(updated);
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos cambiar el nombre del proyecto.',
          );
        },
      });
  }

  addClass(name: string): void {
    const document = this.mutableDocument();
    const id = crypto.randomUUID();
    const classIndex =
      document.umlModel.classes.length;

    const umlClass: UmlClass = {
      id,
      name,
      attributes: [],
    };

    document.umlModel.classes.push(umlClass);

    document.layout.nodes[id] =
      this.defaultLayoutForIndex(classIndex);

    this.commitDraft(document);
  }

  updateClass(
    classId: string,
    changes: Pick<UmlClass, 'name'>,
  ): void {
    const document = this.mutableDocument();
    const index =
      document.umlModel.classes.findIndex(
        (item) => item.id === classId,
      );

    if (index < 0) {
      return;
    }

    document.umlModel.classes[index] = {
      ...document.umlModel.classes[index],
      ...changes,
    };

    this.commitDraft(document);
  }

  removeClass(classId: string): void {
    const document = this.mutableDocument();

    document.umlModel.classes =
      document.umlModel.classes.filter(
        (item) => item.id !== classId,
      );

    document.umlModel.relationships =
      document.umlModel.relationships.filter(
        (relationship) =>
          relationship.sourceClassId !== classId
          && relationship.targetClassId !== classId,
      );

    delete document.layout.nodes[classId];

    this.commitDraft(document);
  }

  addAttribute(
    classId: string,
    attribute: Omit<UmlAttribute, 'id'>,
  ): void {
    const document = this.mutableDocument();
    const umlClass =
      document.umlModel.classes.find(
        (item) => item.id === classId,
      );

    if (!umlClass) {
      return;
    }

    umlClass.attributes.push({
      ...attribute,
      id: crypto.randomUUID(),
    });

    this.normalizeLayoutForClass(
      document,
      umlClass,
    );

    this.commitDraft(document);
  }

  updateAttribute(
    classId: string,
    attributeId: string,
    changes: Omit<UmlAttribute, 'id'>,
  ): void {
    const document = this.mutableDocument();
    const umlClass =
      document.umlModel.classes.find(
        (item) => item.id === classId,
      );

    if (!umlClass) {
      return;
    }

    const attributeIndex =
      umlClass.attributes.findIndex(
        (item) => item.id === attributeId,
      );

    if (attributeIndex < 0) {
      return;
    }

    umlClass.attributes[attributeIndex] = {
      ...changes,
      id: attributeId,
    };

    this.normalizeLayoutForClass(
      document,
      umlClass,
    );

    this.commitDraft(document);
  }

  removeAttribute(
    classId: string,
    attributeId: string,
  ): void {
    const document = this.mutableDocument();
    const umlClass =
      document.umlModel.classes.find(
        (item) => item.id === classId,
      );

    if (!umlClass) {
      return;
    }

    umlClass.attributes =
      umlClass.attributes.filter(
        (item) => item.id !== attributeId,
      );

    this.normalizeLayoutForClass(
      document,
      umlClass,
    );

    this.commitDraft(document);
  }

  addRelationship(
    relationship: Omit<UmlRelationship, 'id'>,
  ): void {
    const document = this.mutableDocument();

    document.umlModel.relationships.push({
      ...relationship,
      id: crypto.randomUUID(),
    });

    this.commitDraft(document);
  }

  updateRelationship(
    relationshipId: string,
    changes: Omit<UmlRelationship, 'id'>,
  ): void {
    const document = this.mutableDocument();

    const index =
      document.umlModel.relationships.findIndex(
        (relationship) =>
          relationship.id === relationshipId,
      );

    if (index < 0) {
      return;
    }

    document.umlModel.relationships[index] = {
      ...changes,
      id: relationshipId,
    };

    this.commitDraft(document);
  }

  removeRelationship(
    relationshipId: string,
  ): void {
    const document = this.mutableDocument();

    document.umlModel.relationships =
      document.umlModel.relationships.filter(
        (relationship) =>
          relationship.id !== relationshipId,
      );

    this.commitDraft(document);
  }

  updateNodeLayout(
    classId: string,
    layout: DiagramNodeLayout,
  ): void {
    const document = this.mutableDocument();

    const umlClass =
      document.umlModel.classes.find(
        (item) => item.id === classId,
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
      document.layout.nodes[classId];

    if (
      current
      && current.x === normalized.x
      && current.y === normalized.y
      && current.width === normalized.width
      && current.height === normalized.height
    ) {
      return;
    }

    document.layout.nodes[classId] =
      normalized;

    this.commitDraft(document);
  }

  saveDocument(): void {
    const project = this.projectState();
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

    this.projectApi
      .saveDocument(project.id, {
        baseRevision: project.revision,
        document,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (updated) => {
          this.projectState.set(updated);
          this.documentDraftState.set(
            structuredClone(updated.document),
          );
          this.dirty.set(false);
          this.saveState.set('saved');
          this.validationViolations.set([]);
        },
        error: (error: HttpErrorResponse) => {
          if (
            error.status === 409
            && error.error?.error
              === 'PROJECT_REVISION_CONFLICT'
          ) {
            this.conflictRevision.set(
              Number(error.error.currentRevision),
            );
            this.saveState.set('conflict');
            this.errorMessage.set(
              'El proyecto cambio en otra sesion. Recarga antes de volver a guardar.',
            );
            return;
          }

          if (
            error.status === 400
            && error.error?.error === 'VALIDATION_ERROR'
          ) {
            const validation =
              error.error as BackendValidationError;

            this.validationViolations.set(
              validation.violations ?? [],
            );
            this.saveState.set('error');
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

  private mutableDocument(): ProjectDocument {
    const current =
      this.documentDraftState();

    if (!current) {
      throw new Error(
        'Project document is not loaded',
      );
    }

    return structuredClone(current);
  }

  private commitDraft(
    document: ProjectDocument,
  ): void {
    this.documentDraftState.set(document);
    this.dirty.set(true);
    this.conflictRevision.set(null);
    this.validationViolations.set([]);
    this.errorMessage.set(null);
    this.saveState.set('dirty');
  }

  private normalizeLayoutForClass(
    document: ProjectDocument,
    umlClass: UmlClass,
  ): void {
    const current =
      document.layout.nodes[umlClass.id]
      ?? this.defaultLayoutForIndex(
        document.umlModel.classes.findIndex(
          (item) => item.id === umlClass.id,
        ),
      );

    document.layout.nodes[umlClass.id] =
      normalizeUmlClassLayout(
        current,
        umlClass.attributes.length,
      );
  }

  private defaultLayoutForIndex(
    index: number,
  ): DiagramNodeLayout {
    const safeIndex =
      Math.max(index, 0);

    const column = safeIndex % 3;
    const row =
      Math.floor(safeIndex / 3);

    return {
      x: 80 + (column * 300),
      y: 80 + (row * 220),
      width: UML_CLASS_MIN_WIDTH,
      height: umlClassHeight(0),
    };
  }
}