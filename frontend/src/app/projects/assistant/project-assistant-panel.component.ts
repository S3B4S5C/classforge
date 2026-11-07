import { DatePipe } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  OnDestroy,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProjectAssistantStore } from '../../features/project-assistant/project-assistant.store';
import type { AssistantMode, AssistantReviewFinding, ProjectAiMessage } from '../../features/project-assistant/project-assistant-model';
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

@Component({
  selector: 'app-project-assistant-panel',
  imports: [
    DatePipe,
    MatButtonModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  providers: [ProjectAssistantStore],
  templateUrl: './project-assistant-panel.component.html',
  styleUrl: './project-assistant-panel.component.scss',
})
export class ProjectAssistantPanelComponent
  implements OnDestroy {
  private readonly api = inject(AssistantApiService);
  private readonly recorder = inject(BrowserWavRecorderService);
  private readonly destroyRef = inject(DestroyRef);
  private voiceTimer: number | null = null;
  private healthProjectId: string | null = null;
  private generation = 0;

  readonly store = inject(ProjectWorkspaceStore);
  readonly conversation = inject(ProjectAssistantStore);
  readonly compact = input(false);
  readonly mode = signal<AssistantMode>('ASK');
  readonly modes: { value: AssistantMode; label: string }[] = [
    { value: 'ASK', label: 'Preguntar' }, { value: 'REVIEW', label: 'Revisar' },
    { value: 'EDIT', label: 'Editar' }, { value: 'SUMMARY_CURRENT', label: 'Resumir' },
  ];
  readonly prompt = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(4000)],
  });
  private readonly voicePlanning = signal(false);
  readonly planning = computed(() => this.voicePlanning() || this.conversation.sending());
  readonly runtimeHealth = signal<AssistantRuntimeHealthResponse | null>(null);
  readonly voiceState = signal<'idle' | 'recording' | 'transcribing'>('idle');
  readonly plan = signal<AssistantPlanResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly voiceTranscript = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly messages = this.conversation.messages;

  private readonly runtimeHealthEffect = effect(() => {
    const projectId = this.store.project()?.id ?? null;
    if (projectId === this.healthProjectId) {
      return;
    }
    this.healthProjectId = projectId;
    this.generation++;
    untracked(() => {
      this.conversation.open(projectId);
      this.plan.set(null);
      this.prompt.setValue('');
      this.mode.set('ASK');
      this.error.set(null);
      this.notice.set(null);
      this.runtimeHealth.set(null);
      this.voiceTranscript.set(null);
      this.voicePlanning.set(false);
      this.voiceState.set('idle');
      this.clearVoiceTimer();
      void this.recorder.cancel();
      if (projectId) this.refreshRuntimeHealth();
    });
  });

  private readonly responseEffect = effect(() => {
    const response = this.conversation.response();
    if (response?.plan) untracked(() => this.acceptFreshPlan(response.plan!));
  });

  send(): void {
    const project = this.store.project();
    const text = this.prompt.value.trim();
    if (!project || !text || this.prompt.invalid || this.planning() || this.voiceBusy()) {
      return;
    }
    const blockReason = this.store.assistantPlanningBlockReason();
    if (blockReason) {
      this.error.set(blockReason);
      return;
    }

    this.error.set(null);
    this.notice.set(null);
    this.plan.set(null);
    this.conversation.send(text, this.mode());
    this.prompt.setValue('');
  }

  selectMode(mode: AssistantMode): void {
    this.mode.set(mode);
    if (!this.prompt.value.trim() && mode === 'REVIEW') this.prompt.setValue('Revisa el modelo UML actual y recomienda mejoras.');
    if (!this.prompt.value.trim() && mode === 'SUMMARY_CURRENT') this.prompt.setValue('Resume el estado actual del modelo UML y la conversación reciente.');
  }

  modeLabel(mode: AssistantMode): string { return this.modes.find(item => item.value === mode)?.label ?? mode; }
  isUser(message: ProjectAiMessage): boolean { return message.role.toUpperCase() === 'USER'; }
  suggest(finding: AssistantReviewFinding): void {
    if (this.planning()) return;
    this.mode.set('EDIT');
    this.prompt.setValue(`Prepara un plan para esta recomendación UML: ${finding.message}${finding.elementRef ? ` (elemento ${finding.elementRef})` : ''}`);
    this.send();
  }
  preview(message: ProjectAiMessage): void {
    if (message.plan) this.acceptFreshPlan(message.plan);
  }
  retry(): void { this.error.set(null); this.conversation.retry(); }

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
    this.notice.set('Plan aplicado al diagrama.');
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
    this.generation++;
    this.clearVoiceTimer();
    void this.recorder.cancel();
  }

  private async startVoice(): Promise<void> {
    const generation = this.generation;
    this.error.set(null);
    this.plan.set(null);
    try {
      await this.recorder.start();
      if (generation !== this.generation) { await this.recorder.cancel(); return; }
      this.voiceState.set('recording');
      this.voiceTimer = window.setTimeout(() => void this.finishVoice(), 20_000);
    } catch {
      if (generation !== this.generation) return;
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
    const generation = this.generation;
    const project = this.store.project();
    this.voiceState.set('transcribing');
    try {
      const audio = await this.recorder.stop();
      if (generation !== this.generation) return;
      if (!project) {
        throw new Error();
      }
      this.voicePlanning.set(true);
      this.error.set(null);
      this.plan.set(null);
      this.api.voice(project.id, audio).pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (generation !== this.generation) return;
          this.voicePlanning.set(false);
          this.voiceState.set('idle');
        }),
      ).subscribe({
        next: (plan) => {
          if (generation !== this.generation) return;
          if (!this.acceptFreshPlan(plan)) {
            return;
          }
          this.voiceTranscript.set(plan.transcript);
        },
        error: () => { if (generation === this.generation) this.error.set('El asistente no está disponible en este momento.'); },
      });
    } catch {
      if (generation !== this.generation) return;
      await this.recorder.cancel();
      this.voiceState.set('idle');
      this.voicePlanning.set(false);
      this.error.set('No pudimos procesar la grabación.');
    }
  }

  private refreshRuntimeHealth(): void {
    const project = this.store.project();
    if (!project) {
      return;
    }
    const generation = this.generation;
    this.api.health(project.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (health) => { if (generation === this.generation) this.runtimeHealth.set(health); },
      error: () => { if (generation === this.generation) this.runtimeHealth.set(null); },
    });
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
