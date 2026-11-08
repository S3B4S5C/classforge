import { DatePipe } from '@angular/common';
import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivityApiService } from './activity-api.service';
import type { ProjectContinuity } from './activity-model';

@Component({
  selector: 'app-since-last-visit',
  imports: [DatePipe, MatButtonModule, RouterLink],
  template: `
    <section class="continuity" aria-label="Desde tu última visita">
      <div class="heading"><div><span class="eyebrow">CONTINUIDAD DEL PROYECTO</span><h2>Desde tu última visita</h2></div>
        <button mat-button type="button" [disabled]="loading()" (click)="reload()" aria-label="Actualizar novedades">Actualizar</button></div>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      @if (data(); as state) {
        <p class="summary"><strong>{{ state.newActivityCount }}</strong> {{ state.newActivityCount === 1 ? 'operación nueva' : 'operaciones nuevas' }}
          <span>· revisión {{ state.lastSeenRevision }} → {{ state.currentRevision }}</span></p>
        @if (state.firstVisit) { <p class="muted">Esta es tu primera visita registrada. Puedes confirmar hasta qué revisión revisaste.</p> }
        @if (state.lastSeenAt) { <p class="muted">Última revisión vista: {{ state.lastSeenAt | date:'medium' }}</p> }
        <div class="participants">@for (member of state.participants; track member.userId) { <span>{{ member.displayName }} · {{ member.changes }}</span> }</div>
        @if (state.newActivityCount) { <ul>@for (item of state.activities; track item.id) { <li><strong>{{ item.actorDisplayName }}</strong> {{ item.description }}</li> }</ul> }
        <div class="actions"><a mat-button [routerLink]="['/projects', projectId(), 'activity']">Ver actividad completa</a>
          <button mat-button type="button" [disabled]="saving() || loading() || (!state.firstVisit && state.lastSeenRevision === state.currentRevision)" (click)="acknowledge()">
            {{ saving() ? 'Guardando…' : 'Marcar esta revisión como vista' }}</button></div>
      } @else if (loading()) { <p role="status">Recuperando novedades…</p> }
    </section>`,
  styles: [`:host{display:block}.continuity{border:1px solid #c7d2fe;border-radius:16px;padding:1.2rem;background:#f5f7ff;color:#182230;margin:1rem 0;overflow-wrap:anywhere}.heading{display:flex;gap:1rem;justify-content:space-between;align-items:center}h2{margin:.3rem 0;font-size:1.1rem}.eyebrow{font-size:.65rem;letter-spacing:.1em;color:#4f46e5;font-weight:700}.summary{margin:.8rem 0}.summary strong{font-size:1.5rem}.muted,.summary span{color:#667085;font-size:.8rem}.participants{display:flex;flex-wrap:wrap;gap:.4rem}.participants span{background:white;border-radius:20px;padding:.35rem .6rem;font-size:.75rem}ul{padding-left:1.1rem;font-size:.82rem;line-height:1.8}.actions{display:flex;flex-wrap:wrap;gap:.3rem;margin-top:.7rem}@media(max-width:600px){.continuity{padding:.85rem}.heading{align-items:flex-start;gap:.2rem}.actions>*{max-width:100%;white-space:normal}}`],
})
export class SinceLastVisitCardComponent {
  readonly projectId = input.required<string>();
  private readonly api = inject(ActivityApiService);
  private readonly destroyRef = inject(DestroyRef);
  private generation = 0;
  private request = 0;
  readonly data = signal<ProjectContinuity | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  constructor() { effect(() => { this.projectId(); this.generation++; this.data.set(null); this.saving.set(false); this.reload(); }); }
  reload(): void {
    const project = this.projectId(); const generation = this.generation; const request = ++this.request;
    this.loading.set(true); this.error.set(null);
    this.api.continuity(project).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { if (generation !== this.generation || request !== this.request) return; this.data.set(data); this.loading.set(false); },
      error: () => { if (generation !== this.generation || request !== this.request) return; this.loading.set(false); this.error.set('No pudimos recuperar las novedades. Vuelve a intentarlo.'); },
    });
  }
  acknowledge(): void {
    const data = this.data(); if (!data || this.saving()) return;
    const generation = this.generation; this.saving.set(true); this.error.set(null);
    this.api.seen(this.projectId(), data.currentRevision).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { if (generation !== this.generation) return; this.saving.set(false); this.reload(); },
      error: () => { if (generation !== this.generation) return; this.saving.set(false); this.error.set('No pudimos registrar la revisión vista.'); },
    });
  }
}
