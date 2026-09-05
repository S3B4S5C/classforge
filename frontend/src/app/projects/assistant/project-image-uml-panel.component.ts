import {
  HttpErrorResponse,
} from '@angular/common/http';
import {
  Component,
  effect,
  inject,
  OnDestroy,
  signal,
} from '@angular/core';
import {
  MatButtonModule,
} from '@angular/material/button';
import {
  MatProgressSpinnerModule,
} from '@angular/material/progress-spinner';
import {
  finalize,
  Subscription,
} from 'rxjs';

import {
  ProjectWorkspaceStore,
} from '../state/project-workspace.store';
import {
  AssistantApiService,
} from './assistant-api.service';
import {
  AssistantImagePreparationService,
} from './assistant-image-preparation.service';
import {
  AssistantImagePlanResponse,
  AssistantRuntimeHealthResponse,
} from './assistant-model';

@Component({
  selector: 'app-project-image-uml-panel',
  imports: [MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './project-image-uml-panel.component.html',
  styleUrl: './project-image-uml-panel.component.scss',
})
export class ProjectImageUmlPanelComponent
  implements OnDestroy {
  private readonly api = inject(AssistantApiService);
  private readonly imagePreparation = inject(AssistantImagePreparationService);
  private healthProjectId: string | null = null;
  private originalImageFile: File | null = null;
  private imageRequest: Subscription | null = null;

  readonly store = inject(ProjectWorkspaceStore);
  readonly selectedImage = signal<File | null>(null);
  readonly imagePreviewUrl = signal<string | null>(null);
  readonly imagePlanning = signal(false);
  readonly imageResult = signal<AssistantImagePlanResponse | null>(null);
  readonly imageRotation = signal(0);
  readonly imageCropInsetPercent = signal(0);
  readonly error = signal<string | null>(null);
  private readonly runtimeHealth = signal<AssistantRuntimeHealthResponse | null>(null);

  private readonly runtimeHealthEffect = effect(() => {
    const projectId = this.store.project()?.id ?? null;
    if (!projectId || projectId === this.healthProjectId) {
      return;
    }
    this.healthProjectId = projectId;
    queueMicrotask(() => this.refreshRuntimeHealth());
  });

  onImageSelected(event: Event): void {
    if (this.imagePlanning()) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (file) {
      void this.selectImage(file);
    }
  }

  onImageDragOver(event: DragEvent): void {
    event.preventDefault();
    if (this.imagePlanning()) {
      return;
    }
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onImageDrop(event: DragEvent): void {
    event.preventDefault();
    if (this.imagePlanning()) {
      return;
    }
    const file = Array.from(event.dataTransfer?.files ?? [])
      .find((candidate) => candidate.type.startsWith('image/'));
    if (file) {
      void this.selectImage(file);
    }
  }

  rotateImage(delta: number): void {
    if (!this.originalImageFile || this.imagePlanning()) {
      return;
    }
    this.imageRotation.set(this.imageRotation() + delta);
    void this.refreshPreparedImage();
  }

  cropImage(): void {
    if (!this.originalImageFile || this.imagePlanning()) {
      return;
    }
    this.imageCropInsetPercent.set(Math.min(30, this.imageCropInsetPercent() + 5));
    void this.refreshPreparedImage();
  }

  resetImageEdits(): void {
    if (!this.originalImageFile || this.imagePlanning()) {
      return;
    }
    this.imageRotation.set(0);
    this.imageCropInsetPercent.set(0);
    void this.refreshPreparedImage();
  }

  clearSelectedImage(): void {
    const preview = this.imagePreviewUrl();
    if (preview) {
      URL.revokeObjectURL(preview);
    }
    this.imagePreviewUrl.set(null);
    this.selectedImage.set(null);
    this.originalImageFile = null;
    this.imageRotation.set(0);
    this.imageCropInsetPercent.set(0);
    this.imageResult.set(null);
    this.error.set(null);
  }

  analyzeImage(): void {
    const project = this.store.project();
    const image = this.selectedImage();
    if (!project || !image || this.imagePlanning()) {
      return;
    }
    if (!this.runtimeReadyForImage()) {
      this.error.set('No se pudo analizar la imagen. Intenta nuevamente.');
      return;
    }
    const blockReason = this.store.assistantPlanningBlockReason();
    if (blockReason) {
      this.error.set('El proyecto está sincronizándose. Intenta nuevamente.');
      return;
    }

    this.error.set(null);
    this.imageResult.set(null);
    this.imagePlanning.set(true);
    this.imageRequest = this.api.imagePlan(project.id, image, this.store.revision()).pipe(
      finalize(() => this.imagePlanning.set(false)),
    ).subscribe({
      next: (result) => {
        this.imageRequest = null;
        if (result.disposition === 'READY') {
          if (this.store.revision() !== result.baseRevision) {
            this.error.set('El proyecto cambió. Analiza la imagen nuevamente.');
            return;
          }
          this.imageResult.set(result);
          return;
        }
        this.error.set(this.dispositionMessage(result.disposition));
      },
      error: (response: HttpErrorResponse) => {
        this.imageRequest = null;
        this.error.set(this.formatError(response));
      },
    });
  }

  retryImageAnalysis(): void {
    this.analyzeImage();
  }

  discardImagePlan(): void {
    this.imageResult.set(null);
    this.error.set(null);
  }

  applyImagePlan(): void {
    const result = this.imageResult();
    if (!result?.command) {
      return;
    }
    const applied = this.store.applyAssistantCommand(result.command, result.baseRevision);
    if (!applied.applied) {
      this.imageResult.set(null);
      this.error.set(applied.reason ?? 'El proyecto cambió. Analiza la imagen nuevamente.');
      return;
    }
    this.clearSelectedImage();
  }

  ngOnDestroy(): void {
    this.imageRequest?.unsubscribe();
    this.clearSelectedImage();
  }

  private async selectImage(file: File): Promise<void> {
    if (this.imagePlanning()) {
      return;
    }
    const allowed = new Set(['image/png', 'image/jpeg', 'image/webp']);
    if (!allowed.has(file.type) || file.size > 10 * 1024 * 1024) {
      this.error.set('No se pudo preparar la imagen. Prueba con otra.');
      return;
    }
    this.clearSelectedImage();
    this.originalImageFile = file;
    await this.refreshPreparedImage();
  }

  private async refreshPreparedImage(): Promise<void> {
    const original = this.originalImageFile;
    if (!original) {
      return;
    }
    try {
      const prepared = await this.imagePreparation.prepare(
        original,
        this.imageRotation(),
        this.imageCropInsetPercent(),
      );
      const previous = this.imagePreviewUrl();
      if (previous) {
        URL.revokeObjectURL(previous);
      }
      this.selectedImage.set(prepared.file);
      this.imagePreviewUrl.set(prepared.previewUrl);
      this.imageResult.set(null);
      this.error.set(null);
    } catch {
      this.error.set('No se pudo preparar la imagen. Prueba con otra.');
    }
  }

  private refreshRuntimeHealth(): void {
    const project = this.store.project();
    if (!project) {
      return;
    }
    this.api.health(project.id).subscribe({
      next: (health) => this.runtimeHealth.set(health),
      error: () => this.runtimeHealth.set(null),
    });
  }

  private runtimeReadyForImage(): boolean {
    return this.runtimeHealth()?.readyForImage ?? false;
  }

  private dispositionMessage(disposition: AssistantImagePlanResponse['disposition']): string {
    return disposition === 'NO_CHANGES'
      ? 'No hay cambios nuevos para aplicar.'
      : 'No encontramos cambios UML claros en esta imagen.';
  }

  private formatError(response: HttpErrorResponse): string {
    if (response.status === 409) {
      return 'El proyecto cambió. Analiza la imagen nuevamente.';
    }
    if (response.error?.visionReason === 'OUTPUT_CONTRACT') {
      return 'No pudimos interpretar esta imagen. Prueba otra vez o usa una imagen más clara.';
    }
    if (response.error?.visionReason === 'TRANSPORT') {
      return 'No se pudo analizar la imagen. Intenta nuevamente.';
    }
    return 'No se pudo completar el análisis.';
  }
}
