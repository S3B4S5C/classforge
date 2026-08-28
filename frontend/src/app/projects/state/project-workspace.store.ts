import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { ProjectApiService } from '../data/project-api.service';
import { Project, ProjectDocument } from '../model/project';

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
  private readonly documentDraftState = signal<ProjectDocument | null>(null);

  readonly project = this.projectState.asReadonly();
  readonly documentDraft = this.documentDraftState.asReadonly();

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly renaming = signal(false);
  readonly dirty = signal(false);
  readonly saveState = signal<ProjectSaveState>('saved');
  readonly errorMessage = signal<string | null>(null);
  readonly conflictRevision = signal<number | null>(null);

  readonly revision = computed(
    () => this.projectState()?.revision ?? 0,
  );

  readonly lastSavedAt = computed(
    () => this.projectState()?.updatedAt ?? null,
  );

  load(projectId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.conflictRevision.set(null);

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

  replaceDocument(document: ProjectDocument): void {
    this.documentDraftState.set(structuredClone(document));
    this.dirty.set(true);
    this.conflictRevision.set(null);
    this.saveState.set('dirty');
  }

  saveDocument(): void {
    const project = this.projectState();
    const document = this.documentDraftState();

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
        },
        error: (error: HttpErrorResponse) => {
          if (
            error.status === 409
            && error.error?.error === 'PROJECT_REVISION_CONFLICT'
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

          this.saveState.set('error');
          this.errorMessage.set(
            'No pudimos guardar el documento.',
          );
        },
      });
  }
}