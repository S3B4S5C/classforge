import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ProjectApiService } from '../../data/project-api.service';
import { Project } from '../../model/project';

@Component({
  selector: 'app-project-workspace-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './project-workspace.page.html',
  styleUrl: './project-workspace.page.scss',
})
export class ProjectWorkspacePage {
  private readonly projectApi = inject(ProjectApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly project = signal<Project | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.loadProject();
  }

  retry(): void {
    this.loadProject();
  }

  private loadProject(): void {
    const projectId = this.route.snapshot.paramMap.get('id');

    if (!projectId) {
      this.loading.set(false);
      this.errorMessage.set('La ruta no contiene un identificador de proyecto.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.projectApi
      .get(projectId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (project) => this.project.set(project),
        error: () => {
          this.errorMessage.set(
            'No pudimos abrir este proyecto. Puede que no exista o que el backend no este disponible.',
          );
        },
      });
  }
}