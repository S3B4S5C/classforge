import {
  HttpErrorResponse,
} from '@angular/common/http';
import {
  Component,
  effect,
  HostListener,
  inject,
  OnDestroy,
  signal,
} from '@angular/core';
import {
  FormControl,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
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
  BrowserWavRecorderService,
} from './browser-wav-recorder.service';
import {
  AssistantImagePreparationService,
} from './assistant-image-preparation.service';
import {
  AssistantAttributePlan,
  AssistantImageEvidenceItem,
  AssistantImagePlanResponse,
  AssistantPlanAction,
  AssistantPlanResponse,
  AssistantRuntimeHealthResponse,
  AssistantRuntimeStatus,
} from './assistant-model';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

interface ValidationViolationPayload {
  field?: string;
  code?: string;
  message?: string;
}

@Component({
  selector:
    'app-project-assistant-panel',
  imports: [
    MatButtonModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl:
    './project-assistant-panel.component.html',
  styleUrl:
    './project-assistant-panel.component.scss',
})
export class ProjectAssistantPanelComponent
  implements OnDestroy {
  private readonly api =
    inject(AssistantApiService);

  private readonly recorder =
    inject(BrowserWavRecorderService);

  private readonly imagePreparation =
    inject(AssistantImagePreparationService);

  private voiceTimer:
    number | null = null;

  private healthProjectId:
    string | null = null;

  private healthRefreshTimer:
    number | null = null;

  private originalImageFile:
    File | null = null;

  private imageRequest:
    Subscription | null = null;

  readonly store =
    inject(ProjectWorkspaceStore);

  readonly prompt =
    new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(1000),
      ],
    });

  readonly planning =
    signal(false);

  readonly healthLoading =
    signal(false);

  readonly runtimeHealth =
    signal<
      AssistantRuntimeHealthResponse | null
    >(null);

  readonly runtimeError =
    signal<string | null>(
      null,
    );

  private readonly runtimeHealthEffect =
    effect(
      () => {
        const projectId =
          this.store.project()?.id
          ?? null;

        if (
          !projectId
          || projectId
            === this.healthProjectId
        ) {
          return;
        }

        this.healthProjectId =
          projectId;

        queueMicrotask(
          () =>
            this.refreshRuntimeHealth(),
        );
      },
    );

  readonly voiceState =
    signal<
      'idle'
      | 'recording'
      | 'transcribing'
    >('idle');

  readonly plan =
    signal<AssistantPlanResponse | null>(
      null,
    );

  readonly selectedImage =
    signal<File | null>(null);

  readonly imagePreviewUrl =
    signal<string | null>(null);

  readonly imagePlanning =
    signal(false);

  readonly imageResult =
    signal<AssistantImagePlanResponse | null>(null);

  readonly imageRotation = signal(0);

  readonly imageCropInsetPercent = signal(0);

  readonly imageRetryAvailable = signal(false);

  readonly activeEvidence =
    signal<AssistantImageEvidenceItem | null>(null);

  readonly error =
    signal<string | null>(
      null,
    );

  readonly messages =
    signal<ChatMessage[]>([]);

  constructor() {
    this.healthRefreshTimer = window.setInterval(
      () => {
        if (
          !this.planning()
          && this.voiceState() === 'idle'
          && !this.imagePlanning()
        ) {
          this.refreshRuntimeHealth();
        }
      },
      30_000,
    );
  }

  send(): void {
    const project =
      this.store.project();

    const text =
      this.prompt.value.trim();

    if (
      !project
      || !text
      || this.prompt.invalid
      || this.planning()
      || this.voiceBusy()
      || this.imagePlanning()
    ) {
      return;
    }

    if (!this.runtimeReadyForText()) {
      this.error.set(
        'llama.cpp no esta listo. Revisa el estado de runtimes y vuelve a intentar.',
      );
      return;
    }

    const blockReason =
      this.store
        .assistantPlanningBlockReason();

    if (blockReason) {
      this.error.set(
        blockReason,
      );
      return;
    }

    this.messages.update(
      (messages) => [
        ...messages,
        {
          role: 'user',
          text,
        },
      ],
    );

    this.error.set(null);
    this.plan.set(null);
    this.planning.set(true);

    this.api
      .plan(
        project.id,
        text,
      )
      .pipe(
        finalize(
          () =>
            this.planning.set(false),
        ),
      )
      .subscribe({
        next: (plan) => {
          if (
            !this.acceptFreshPlan(
              plan,
            )
          ) {
            return;
          }

          this.messages.update(
            (messages) => [
              ...messages,
              {
                role: 'assistant',
                text: plan.summary,
              },
            ],
          );
        },
        error: (
          response:
            HttpErrorResponse,
        ) => {
          const message =
            this.formatError(
              response,
            );

          this.error.set(message);

          this.messages.update(
            (messages) => [
              ...messages,
              {
                role: 'assistant',
                text:
                  `No pude preparar el cambio: ${message}`,
              },
            ],
          );
        },
      });
  }

  onImageSelected(
    event: Event,
  ): void {
    const input =
      event.target as HTMLInputElement;

    const file =
      input.files?.[0] ?? null;

    input.value = '';
    if (file) {
      void this.selectImage(file);
    }
  }

  onCameraSelected(
    event: Event,
  ): void {
    this.onImageSelected(event);
  }

  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    if (this.imagePlanning()) {
      return;
    }

    const directFile = Array.from(event.clipboardData?.files ?? [])
      .find((candidate) => candidate.type.startsWith('image/'));
    const itemFile = Array.from(event.clipboardData?.items ?? [])
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .find((candidate): candidate is File => candidate !== null);
    const file = directFile ?? itemFile;

    if (file) {
      event.preventDefault();
      void this.selectImage(file);
    }
  }

  onImageDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onImageDrop(event: DragEvent): void {
    event.preventDefault();
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
    this.imageCropInsetPercent.set(
      Math.min(30, this.imageCropInsetPercent() + 5),
    );
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

  cancelImageAnalysis(): void {
    if (!this.imagePlanning()) {
      return;
    }
    this.imageRequest?.unsubscribe();
    this.imageRequest = null;
    this.imagePlanning.set(false);
    this.imageRetryAvailable.set(true);
    this.messages.update((messages) => [
      ...messages,
      {
        role: 'assistant',
        text: 'Analisis visual cancelado. No se aplico ningun cambio.',
      },
    ]);
  }

  retryImageAnalysis(): void {
    this.imageRetryAvailable.set(false);
    this.analyzeImage();
  }

  evidenceLeft(item: AssistantImageEvidenceItem, result: AssistantImagePlanResponse): number {
    return this.evidencePercent(item.x, result.image.width);
  }

  evidenceTop(item: AssistantImageEvidenceItem, result: AssistantImagePlanResponse): number {
    return this.evidencePercent(item.y, result.image.height);
  }

  evidenceWidth(item: AssistantImageEvidenceItem, result: AssistantImagePlanResponse): number {
    return this.evidencePercent(item.width, result.image.width);
  }

  evidenceHeight(item: AssistantImageEvidenceItem, result: AssistantImagePlanResponse): number {
    return this.evidencePercent(item.height, result.image.height);
  }

  evidenceHasBox(item: AssistantImageEvidenceItem): boolean {
    return item.x !== null
      && item.y !== null
      && item.width !== null
      && item.height !== null;
  }

  confidenceLabel(value: number | null): string {
    if (value === null) {
      return 'sin confianza';
    }
    if (value < 0.65) {
      return `baja ${Math.round(value * 100)}%`;
    }
    return `${Math.round(value * 100)}%`;
  }

  private evidencePercent(value: number | null, total: number): number {
    if (value === null || total <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, value * 100 / total));
  }

  private async selectImage(file: File): Promise<void> {
    const allowed = new Set([
      'image/png',
      'image/jpeg',
      'image/webp',
    ]);

    if (!allowed.has(file.type)) {
      this.error.set('Usa una imagen PNG, JPEG o WEBP.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.error.set('La imagen no puede superar 10 MB.');
      return;
    }

    this.clearSelectedImage();
    this.originalImageFile = file;
    this.imageRotation.set(0);
    this.imageCropInsetPercent.set(0);
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
      this.plan.set(null);
      this.error.set(null);
      this.imageRetryAvailable.set(false);
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo preparar la imagen.',
      );
    }
  }

  analyzeImage(): void {
    const project =
      this.store.project();

    const image =
      this.selectedImage();

    if (
      !project
      || !image
      || this.planning()
      || this.voiceBusy()
      || this.imagePlanning()
    ) {
      return;
    }

    if (!this.runtimeReadyForImage()) {
      this.error.set(
        'El runtime Vision no esta listo. Inicia llama.cpp multimodal en 8094 y actualiza el estado.',
      );
      return;
    }

    const blockReason =
      this.store
        .assistantPlanningBlockReason();

    if (blockReason) {
      this.error.set(blockReason);
      return;
    }

    this.error.set(null);
    this.plan.set(null);
    this.imageResult.set(null);
    this.imageRetryAvailable.set(false);
    this.imagePlanning.set(true);

    this.messages.update(
      (messages) => [
        ...messages,
        {
          role: 'user',
          text: `Imagen: ${image.name}`,
        },
      ],
    );

    this.imageRequest = this.api
      .imagePlan(
        project.id,
        image,
        this.store.revision(),
      )
      .pipe(
        finalize(
          () =>
            this.imagePlanning.set(false),
        ),
      )
      .subscribe({
        next: (plan) => {
          this.imageRequest = null;
          this.imageResult.set(plan);

          if (plan.disposition === 'READY') {
            if (!this.acceptFreshPlan(plan)) {
              this.imageRetryAvailable.set(true);
              return;
            }
          } else {
            this.plan.set(null);
          }

          const warningSuffix =
            plan.warnings.length > 0
              ? ` Advertencias: ${plan.warnings.join(' · ')}`
              : '';

          this.messages.update(
            (messages) => [
              ...messages,
              {
                role: 'assistant',
                text: `${plan.summary}${warningSuffix}`,
              },
            ],
          );
        },
        error: (
          response: HttpErrorResponse,
        ) => {
          this.imageRequest = null;
          const message =
            this.formatError(response);

          this.error.set(message);
          this.imageRetryAvailable.set(true);
          this.messages.update(
            (messages) => [
              ...messages,
              {
                role: 'assistant',
                text:
                  `No pude analizar la imagen: ${message}`,
              },
            ],
          );
        },
      });
  }

  clearSelectedImage(): void {
    const preview =
      this.imagePreviewUrl();

    if (preview) {
      URL.revokeObjectURL(preview);
    }

    this.imagePreviewUrl.set(null);
    this.selectedImage.set(null);
    this.originalImageFile = null;
    this.imageRotation.set(0);
    this.imageCropInsetPercent.set(0);
    this.imageResult.set(null);
    this.activeEvidence.set(null);
    this.imageRetryAvailable.set(false);
  }

  imageSizeLabel(): string {
    const image =
      this.selectedImage();

    if (!image) {
      return '';
    }

    if (image.size < 1024 * 1024) {
      return `${Math.max(1, Math.round(image.size / 1024))} KB`;
    }

    return `${(image.size / (1024 * 1024)).toFixed(1)} MB`;
  }

  apply(): void {
    const plan =
      this.plan();

    if (!plan || !plan.command) {
      return;
    }

    const result =
      this.store.applyAssistantCommand(
        plan.command,
        plan.baseRevision,
      );

    if (!result.applied) {
      this.error.set(
        result.reason
        ?? 'No se pudo aplicar el plan.',
      );

      this.plan.set(null);
      return;
    }

    this.messages.update(
      (messages) => [
        ...messages,
        {
          role: 'assistant',
          text:
            'Plan aplicado como una sola operacion BATCH.',
        },
      ],
    );

    if (plan.source === 'IMAGE') {
      this.clearSelectedImage();
    }

    this.plan.set(null);
    this.prompt.setValue('');
    this.error.set(null);
  }

  discard(): void {
    const current = this.plan();
    if (current?.source === 'IMAGE') {
      this.clearSelectedImage();
    }
    this.plan.set(null);
    this.imageResult.set(null);
    this.error.set(null);
  }

  async toggleVoice(): Promise<void> {
    if (
      this.voiceState()
        === 'recording'
    ) {
      await this.finishVoice();
      return;
    }

    if (
      this.voiceBusy()
      || this.planning()
      || this.imagePlanning()
    ) {
      return;
    }

    if (!this.runtimeReadyForVoice()) {
      this.error.set(
        'La voz requiere llama.cpp y whisper.cpp listos. Actualiza el estado de runtimes.',
      );
      return;
    }

    const blockReason =
      this.store
        .assistantPlanningBlockReason();

    if (blockReason) {
      this.error.set(
        blockReason,
      );
      return;
    }

    await this.startVoice();
  }

  voiceBusy(): boolean {
    return this.voiceState()
      !== 'idle';
  }

  voiceLabel(): string {
    switch (this.voiceState()) {
      case 'recording':
        return 'Detener';
      case 'transcribing':
        return 'Procesando...';
      default:
        return 'Hablar';
    }
  }

  private async startVoice():
    Promise<void> {
    this.error.set(null);
    this.plan.set(null);

    try {
      await this.recorder.start();

      this.voiceState.set(
        'recording',
      );

      this.voiceTimer =
        window.setTimeout(
          () => {
            void this.finishVoice();
          },
          20_000,
        );
    } catch (error) {
      await this.recorder.cancel();

      this.voiceState.set(
        'idle',
      );

      const message =
        error instanceof DOMException
          && error.name === 'NotAllowedError'
            ? 'No se concedio permiso para usar el microfono.'
            : (
              error instanceof Error
                ? error.message
                : 'No pudimos iniciar el microfono.'
            );

      this.error.set(message);
    }
  }

  private async finishVoice():
    Promise<void> {
    if (
      this.voiceState()
        !== 'recording'
    ) {
      return;
    }

    this.clearVoiceTimer();

    this.voiceState.set(
      'transcribing',
    );

    try {
      const audio =
        await this.recorder.stop();

      const project =
        this.store.project();

      if (!project) {
        throw new Error(
          'No hay un proyecto abierto.',
        );
      }

      this.planning.set(true);
      this.error.set(null);
      this.plan.set(null);

      this.api
        .voice(
          project.id,
          audio,
        )
        .pipe(
          finalize(
            () => {
              this.planning.set(false);
              this.voiceState.set(
                'idle',
              );
            },
          ),
        )
        .subscribe({
          next: (plan) => {
            if (
              !this.acceptFreshPlan(
                plan,
              )
            ) {
              return;
            }

            this.messages.update(
              (messages) => [
                ...messages,
                {
                  role: 'user',
                  text:
                    `🎙 ${plan.transcript}`,
                },
                {
                  role: 'assistant',
                  text: plan.summary,
                },
              ],
            );
          },
          error: (
            response:
              HttpErrorResponse,
          ) => {
            const message =
              this.formatError(
                response,
              );

            this.error.set(message);

            this.messages.update(
              (messages) => [
                ...messages,
                {
                  role: 'assistant',
                  text:
                    `No pude preparar la voz: ${message}`,
                },
              ],
            );
          },
        });
    } catch (error) {
      await this.recorder.cancel();

      this.voiceState.set(
        'idle',
      );

      this.planning.set(false);

      const message =
        error instanceof Error
          ? error.message
          : 'No pudimos procesar la grabacion.';

      this.error.set(message);
    }
  }

  refreshRuntimeHealth(): void {
    const project =
      this.store.project();

    if (
      !project
      || this.healthLoading()
    ) {
      return;
    }

    this.healthLoading.set(true);
    this.runtimeError.set(null);

    this.api
      .health(
        project.id,
      )
      .pipe(
        finalize(
          () =>
            this.healthLoading.set(false),
        ),
      )
      .subscribe({
        next: (health) => {
          this.runtimeHealth.set(
            health,
          );
        },
        error: () => {
          this.runtimeHealth.set(
            null,
          );

          this.runtimeError.set(
            'No pudimos comprobar llama.cpp, whisper.cpp y Vision.',
          );
        },
      });
  }

  runtimeReadyForText(): boolean {
    return this.runtimeHealth()
      ?.readyForText
      ?? false;
  }

  runtimeReadyForVoice(): boolean {
    return this.runtimeHealth()
      ?.readyForVoice
      ?? false;
  }

  runtimeReadyForImage(): boolean {
    return this.runtimeHealth()
      ?.readyForImage
      ?? false;
  }

  runtimeLabel(
    runtime: AssistantRuntimeStatus,
  ): string {
    if (runtime.available) {
      return `${runtime.latencyMs} ms`;
    }

    return `${runtime.name}: ${runtime.state}`;
  }

  canApplyPlan(
    plan: AssistantPlanResponse,
  ): boolean {
    return plan.command !== null
      && this.store.revision()
      === plan.baseRevision
      && !this.store
        .assistantPlanningBlockReason();
  }

  private acceptFreshPlan(
    plan: AssistantPlanResponse,
  ): boolean {
    if (
      this.store.revision()
        !== plan.baseRevision
    ) {
      const message =
        'El proyecto cambio mientras el Asistente preparaba el plan. Vuelve a enviarlo.';

      this.plan.set(null);
      this.error.set(message);

      this.messages.update(
        (messages) => [
          ...messages,
          {
            role: 'assistant',
            text: message,
          },
        ],
      );

      return false;
    }

    const blockReason =
      this.store
        .assistantPlanningBlockReason();

    if (blockReason) {
      this.plan.set(null);
      this.error.set(
        blockReason,
      );

      return false;
    }

    this.plan.set(
      plan,
    );

    return true;
  }

  ngOnDestroy(): void {
    this.clearVoiceTimer();

    if (this.healthRefreshTimer !== null) {
      window.clearInterval(this.healthRefreshTimer);
      this.healthRefreshTimer = null;
    }

    this.imageRequest?.unsubscribe();
    this.imageRequest = null;
    this.clearSelectedImage();
    void this.recorder.cancel();
  }

  private clearVoiceTimer(): void {
    if (
      this.voiceTimer !== null
    ) {
      window.clearTimeout(
        this.voiceTimer,
      );

      this.voiceTimer = null;
    }
  }

  private formatError(
    response: HttpErrorResponse,
  ): string {
    if (response.status === 409) {
      return 'El proyecto cambio mientras se analizaba la imagen. Analiza nuevamente sobre la revision actual.';
    }

    if (response.error?.visionReason === 'OUTPUT_CONTRACT') {
      return 'El modelo visual respondio, pero su salida no cumplio el contrato UML. La imagen no fue aplicada.';
    }

    if (response.error?.visionReason === 'TRANSPORT') {
      return 'Vision no pudo completar la inferencia local. Comprueba llama.cpp en 8094 e intenta nuevamente.';
    }

    const base =
      typeof response.error?.message === 'string'
        ? response.error.message
        : 'No pudimos generar el plan. Verifica los runtimes locales.';

    const parts: string[] = [
      base,
    ];

    const stage =
      response.error?.stage;

    if (
      typeof stage === 'string'
      && stage.trim()
    ) {
      parts.push(
        `Etapa: ${stage}.`,
      );
    }

    const transcript =
      response.error?.transcript;

    if (
      typeof transcript === 'string'
      && transcript.trim()
    ) {
      parts.push(
        `Interpretado: "${transcript.trim()}".`,
      );
    }

    const attempted =
      this.describeAttemptedPlan(
        response.error?.attemptedPlan,
      );

    if (attempted) {
      parts.push(
        `El LLM intento: ${attempted}.`,
      );
    }

    const rawViolations =
      response.error?.violations;

    if (
      Array.isArray(rawViolations)
      && rawViolations.length > 0
    ) {
      const violations =
        rawViolations as ValidationViolationPayload[];

      const details =
        violations
          .map(
            (violation) => {
              const code =
                violation.code
                ?? 'VALIDATION_ERROR';

              const message =
                violation.message
                ?? 'Dato UML invalido.';

              const field =
                violation.field
                  ? ` (${violation.field})`
                  : '';

              return `${code}: ${message}${field}`;
            },
          )
          .join(' · ');

      parts.push(details);
    }

    return parts.join(' ');
  }

  private describeAttemptedPlan(
    rawPlan: unknown,
  ): string | null {
    if (
      !rawPlan
      || typeof rawPlan !== 'object'
    ) {
      return null;
    }

    const actions =
      (
        rawPlan as {
          actions?: unknown;
        }
      ).actions;

    if (
      !Array.isArray(actions)
      || actions.length === 0
    ) {
      return null;
    }

    return actions
      .slice(0, 6)
      .map(
        (rawAction) => {
          if (
            !rawAction
            || typeof rawAction !== 'object'
          ) {
            return 'ACCION_DESCONOCIDA';
          }

          const action =
            rawAction as Record<
              string,
              unknown
            >;

          const type =
            typeof action['type'] === 'string'
              ? action['type']
              : 'ACCION_DESCONOCIDA';

          const source =
            typeof action['sourceClassName'] === 'string'
              ? action['sourceClassName']
              : null;

          const target =
            typeof action['targetClassName'] === 'string'
              ? action['targetClassName']
              : null;

          if (source && target) {
            return `${type} ${source} → ${target}`;
          }

          const className =
            typeof action['className'] === 'string'
              ? action['className']
              : null;

          const attributeName =
            typeof action['attributeName'] === 'string'
              ? action['attributeName']
              : null;

          if (
            className
            && attributeName
          ) {
            return `${type} ${className}.${attributeName}`;
          }

          if (className) {
            return `${type} ${className}`;
          }

          return type;
        },
      )
      .join(', ');
  }

  actionCount(
    plan: AssistantPlanResponse,
  ): number {
    if (!plan.command) {
      return 0;
    }
    return plan.command.type === 'BATCH'
      ? plan.command.commands.length
      : 1;
  }

  actionLabel(
    action: AssistantPlanAction,
  ): string {
    switch (action.type) {
      case 'CREATE_CLASS':
        return `Crear clase ${action.className ?? ''}`.trim();

      case 'RENAME_CLASS':
        return `Renombrar ${action.className ?? ''} a ${action.newName ?? ''}`.trim();

      case 'DELETE_CLASS':
        return `Eliminar clase ${action.className ?? ''}`.trim();

      case 'ADD_ATTRIBUTES':
        return `Agregar atributos a ${action.className ?? ''}`.trim();

      case 'UPDATE_ATTRIBUTE':
        return `Modificar ${action.className ?? ''}.${action.attributeName ?? ''}`;

      case 'DELETE_ATTRIBUTE':
        return `Eliminar ${action.className ?? ''}.${action.attributeName ?? ''}`;

      case 'CREATE_RELATIONSHIP':
        return `Crear ${action.relationshipType ?? 'relacion'}: ${action.sourceClassName ?? ''} → ${action.targetClassName ?? ''}`;

      case 'UPDATE_RELATIONSHIP':
        return `Modificar relacion: ${action.sourceClassName ?? ''} → ${action.targetClassName ?? ''}`;

      case 'DELETE_RELATIONSHIP':
        return `Eliminar relacion: ${action.sourceClassName ?? ''} → ${action.targetClassName ?? ''}`;
    }
  }

  attributeLabel(
    attribute: AssistantAttributePlan,
  ): string {
    const type =
      attribute.dataType
      ?? 'STRING';

    const source =
      attribute.typeSource === 'EXPLICIT'
        ? 'explicito'
        : (
          attribute.typeSource === 'INFERRED'
            ? 'inferido'
            : 'predeterminado'
        );

    const id =
      attribute.identifier
        ? ' · ID'
        : '';

    return `${attribute.name ?? '?'}: ${type}${id} · ${source}`;
  }
}