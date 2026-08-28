import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ProjectApiService } from '../../data/project-api.service';
import { Project } from '../../model/project';

@Component({
  selector: 'app-project-list-page',
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
  templateUrl: './project-list.page.html',
  styleUrl: './project-list.page.scss',
})
export class ProjectListPage {
  private readonly projectApi = inject(ProjectApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly creating = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly nameControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(120),
    ],
  });

  readonly form = new FormGroup({
    name: this.nameControl,
  });

  constructor() {
    this.loadProjects();
  }

  createProject(): void {
    this.nameControl.markAsTouched();

    if (this.nameControl.invalid || this.creating()) {
      return;
    }

    const name = this.nameControl.value.trim();

    if (!name) {
      this.nameControl.setErrors({ required: true });
      return;
    }

    this.creating.set(true);
    this.errorMessage.set(null);

    this.projectApi
      .create({ name })
      .pipe(
        finalize(() => this.creating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (project) => {
          void this.router.navigate(['/projects', project.id]);
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos crear el proyecto. Verifica que el backend este disponible.',
          );
        },
      });
  }

  retry(): void {
    this.loadProjects();
  }

  private loadProjects(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.projectApi
      .list()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (projects) => this.projects.set(projects),
        error: () => {
          this.errorMessage.set(
            'No pudimos cargar tus proyectos. Verifica la conexion con ClassForge Backend.',
          );
        },
      });
  }
}