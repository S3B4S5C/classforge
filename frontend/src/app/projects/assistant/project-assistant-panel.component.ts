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
  AssistantAttributePlan,
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

  private voiceTimer:
    number | null = null;

  private healthProjectId:
    string | null = null;

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

  readonly error =
    signal<string | null>(
      null,
    );

  readonly messages =
    signal<ChatMessage[]>([]);

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

  apply(): void {
    const plan =
      this.plan();

    if (!plan) {
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

    this.plan.set(null);
    this.prompt.setValue('');
    this.error.set(null);
  }

  discard(): void {
    this.plan.set(null);
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
            'No pudimos comprobar llama.cpp y whisper.cpp.',
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
    return this.store.revision()
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