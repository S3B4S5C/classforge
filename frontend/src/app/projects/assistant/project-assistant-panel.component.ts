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
} from './assistant-model';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

@Component({
  selector: 'app-project-assistant-panel',
  imports: [
    MatButtonModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './project-assistant-panel.component.html',
  styleUrl: './project-assistant-panel.component.scss',
})
export class ProjectAssistantPanelComponent
  implements OnDestroy {
  private readonly api = inject(AssistantApiService);
  private readonly recorder = inject(BrowserWavRecorderService);
  private voiceTimer: number | null = null;
  private healthProjectId: string | null = null;

  readonly store = inject(ProjectWorkspaceStore);
  readonly prompt = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });
  readonly planning = signal(false);
  readonly runtimeHealth = signal<AssistantRuntimeHealthResponse | null>(null);
  readonly voiceState = signal<'idle' | 'recording' | 'transcribing'>('idle');
  readonly plan = signal<AssistantPlanResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);

  private readonly runtimeHealthEffect = effect(() => {
    const projectId = this.store.project()?.id ?? null;
    if (!projectId || projectId === this.healthProjectId) {
      return;
    }
    this.healthProjectId = projectId;
    queueMicrotask(() => this.refreshRuntimeHealth());
  });

  send(): void {
    const project = this.store.project();
    const text = this.prompt.value.trim();
    if (!project || !text || this.prompt.invalid || this.planning() || this.voiceBusy()) {
      return;
    }
    if (!this.runtimeReadyForText()) {
      this.error.set('El asistente no está disponible en este momento.');
      return;
    }
    const blockReason = this.store.assistantPlanningBlockReason();
    if (blockReason) {
      this.error.set(blockReason);
      return;
    }

    this.messages.update((messages) => [...messages, { role: 'user', text }]);
    this.error.set(null);
    this.plan.set(null);
    this.planning.set(true);
    this.api.plan(project.id, text).pipe(
      finalize(() => this.planning.set(false)),
    ).subscribe({
      next: (plan) => {
        if (!this.acceptFreshPlan(plan)) {
          return;
        }
        this.messages.update((messages) => [
          ...messages,
          { role: 'assistant', text: plan.summary },
        ]);
      },
      error: () => {
        const message = 'El asistente no está disponible en este momento.';
        this.error.set(message);
        this.messages.update((messages) => [
          ...messages,
          { role: 'assistant', text: message },
        ]);
      },
    });
  }

  apply(): void {
    const plan = this.plan();
    if (!plan || !plan.command) {
      return;
    }
    const result = this.store.applyAssistantCommand(plan.command, plan.baseRevision);
    if (!result.applied) {
      this.error.set(result.reason ?? 'No se pudo aplicar el plan.');
      this.plan.set(null);
      return;
    }
    this.messages.update((messages) => [
      ...messages,
      { role: 'assistant', text: 'Plan aplicado al diagrama.' },
    ]);
    this.plan.set(null);
    this.prompt.setValue('');
    this.error.set(null);
  }

  discard(): void {
    this.plan.set(null);
    this.error.set(null);
  }

  async toggleVoice(): Promise<void> {
    if (this.voiceState() === 'recording') {
      await this.finishVoice();
      return;
    }
    if (this.voiceBusy() || this.planning()) {
      return;
    }
    if (!this.runtimeReadyForVoice()) {
      this.error.set('El asistente no está disponible en este momento.');
      return;
    }
    const blockReason = this.store.assistantPlanningBlockReason();
    if (blockReason) {
      this.error.set(blockReason);
      return;
    }
    await this.startVoice();
  }

  voiceBusy(): boolean {
    return this.voiceState() !== 'idle';
  }

  voiceLabel(): string {
    switch (this.voiceState()) {
      case 'recording': return 'Detener';
      case 'transcribing': return 'Procesando...';
      default: return 'Hablar';
    }
  }

  canApplyPlan(plan: AssistantPlanResponse): boolean {
    return !!plan.command
      && this.store.revision() === plan.baseRevision
      && !this.store.assistantPlanningBlockReason();
  }

  actionCount(plan: AssistantPlanResponse): number {
    return plan.command?.type === 'BATCH' ? plan.command.commands.length : 0;
  }

  actionLabel(action: AssistantPlanAction): string {
    switch (action.type) {
      case 'CREATE_CLASS': return `Crear clase ${action.className ?? ''}`.trim();
      case 'RENAME_CLASS': return `Renombrar ${action.className ?? ''} a ${action.newName ?? ''}`.trim();
      case 'DELETE_CLASS': return `Eliminar clase ${action.className ?? ''}`.trim();
      case 'ADD_ATTRIBUTES': return `Agregar atributos a ${action.className ?? ''}`.trim();
      case 'UPDATE_ATTRIBUTE': return `Modificar ${action.className ?? ''}.${action.attributeName ?? ''}`;
      case 'DELETE_ATTRIBUTE': return `Eliminar ${action.className ?? ''}.${action.attributeName ?? ''}`;
      case 'CREATE_RELATIONSHIP': return `Crear ${action.relationshipType ?? 'relacion'}: ${action.sourceClassName ?? ''} -> ${action.targetClassName ?? ''}`;
      case 'UPDATE_RELATIONSHIP': return `Modificar relacion: ${action.sourceClassName ?? ''} -> ${action.targetClassName ?? ''}`;
      case 'DELETE_RELATIONSHIP': return `Eliminar relacion: ${action.sourceClassName ?? ''} -> ${action.targetClassName ?? ''}`;
    }
  }

  attributeLabel(attribute: AssistantAttributePlan): string {
    return `${attribute.name ?? '?'}: ${attribute.dataType ?? 'STRING'}${attribute.identifier ? ' · ID' : ''}`;
  }

  ngOnDestroy(): void {
    this.clearVoiceTimer();
    void this.recorder.cancel();
  }

  private async startVoice(): Promise<void> {
    this.error.set(null);
    this.plan.set(null);
    try {
      await this.recorder.start();
      this.voiceState.set('recording');
      this.voiceTimer = window.setTimeout(() => void this.finishVoice(), 20_000);
    } catch {
      await this.recorder.cancel();
      this.voiceState.set('idle');
      this.error.set('No pudimos iniciar el micrófono.');
    }
  }

  private async finishVoice(): Promise<void> {
    if (this.voiceState() !== 'recording') {
      return;
    }
    this.clearVoiceTimer();
    this.voiceState.set('transcribing');
    try {
      const audio = await this.recorder.stop();
      const project = this.store.project();
      if (!project) {
        throw new Error();
      }
      this.planning.set(true);
      this.error.set(null);
      this.plan.set(null);
      this.api.voice(project.id, audio).pipe(
        finalize(() => {
          this.planning.set(false);
          this.voiceState.set('idle');
        }),
      ).subscribe({
        next: (plan) => {
          if (!this.acceptFreshPlan(plan)) {
            return;
          }
          this.messages.update((messages) => [
            ...messages,
            { role: 'user', text: `Voz: ${plan.transcript}` },
            { role: 'assistant', text: plan.summary },
          ]);
        },
        error: () => this.error.set('El asistente no está disponible en este momento.'),
      });
    } catch {
      await this.recorder.cancel();
      this.voiceState.set('idle');
      this.planning.set(false);
      this.error.set('No pudimos procesar la grabación.');
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

  private runtimeReadyForText(): boolean {
    return this.runtimeHealth()?.readyForText ?? false;
  }

  private runtimeReadyForVoice(): boolean {
    return this.runtimeHealth()?.readyForVoice ?? false;
  }

  private acceptFreshPlan(plan: AssistantPlanResponse): boolean {
    if (this.store.revision() !== plan.baseRevision) {
      this.plan.set(null);
      this.error.set('El proyecto cambió mientras se preparaba el plan. Vuelve a intentarlo.');
      return false;
    }
    const blockReason = this.store.assistantPlanningBlockReason();
    if (blockReason) {
      this.plan.set(null);
      this.error.set(blockReason);
      return false;
    }
    this.plan.set(plan);
    return true;
  }

  private clearVoiceTimer(): void {
    if (this.voiceTimer !== null) {
      window.clearTimeout(this.voiceTimer);
      this.voiceTimer = null;
    }
  }
}
