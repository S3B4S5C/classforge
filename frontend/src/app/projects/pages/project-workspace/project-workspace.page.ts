import { DatePipe } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  ProjectSaveState,
  ProjectWorkspaceStore,
} from '../../state/project-workspace.store';

@Component({
  selector: 'app-project-workspace-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  providers: [ProjectWorkspaceStore],
  templateUrl: './project-workspace.page.html',
  styleUrl: './project-workspace.page.scss',
})
export class ProjectWorkspacePage {
  private readonly route = inject(ActivatedRoute);

  readonly store = inject(ProjectWorkspaceStore);
  readonly editingName = signal(false);

  readonly layoutNodeCount = computed(
    () =>
      Object.keys(
        this.store.project()?.document.layout.nodes ?? {},
      ).length,
  );

  readonly nameControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(120),
    ],
  });

  constructor() {
    const projectId = this.route.snapshot.paramMap.get('id');

    if (projectId) {
      this.store.load(projectId);
    }

    effect(() => {
      const project = this.store.project();

      if (project && !this.editingName()) {
        this.nameControl.setValue(
          project.name,
          { emitEvent: false },
        );
      }
    });
  }

  retry(): void {
    const projectId = this.route.snapshot.paramMap.get('id');

    if (projectId) {
      this.store.load(projectId);
    }
  }

  startRename(): void {
    const project = this.store.project();

    if (!project) {
      return;
    }

    this.nameControl.setValue(project.name);
    this.editingName.set(true);
  }

  cancelRename(): void {
    const project = this.store.project();

    if (project) {
      this.nameControl.setValue(project.name);
    }

    this.editingName.set(false);
  }

  rename(): void {
    this.nameControl.markAsTouched();

    const name = this.nameControl.value.trim();

    if (
      !name
      || this.nameControl.invalid
      || this.store.renaming()
    ) {
      return;
    }

    this.store.rename(name);
    this.editingName.set(false);
  }

  save(): void {
    this.store.saveDocument();
  }

  saveStateIcon(state: ProjectSaveState): string {
    switch (state) {
      case 'dirty':
        return 'edit';
      case 'saving':
        return 'sync';
      case 'conflict':
        return 'warning';
      case 'error':
        return 'error';
      default:
        return 'check_circle';
    }
  }

  saveStateText(state: ProjectSaveState): string {
    switch (state) {
      case 'dirty':
        return 'Cambios sin guardar';
      case 'saving':
        return 'Guardando...';
      case 'conflict':
        return 'Conflicto de revision';
      case 'error':
        return 'Error de guardado';
      default:
        return 'Guardado';
    }
  }
}