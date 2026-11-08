import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, distinctUntilChanged, forkJoin, map, of } from 'rxjs';
import { ProjectApiService } from '../../projects/data/project-api.service';
import { ProjectInvitationApiService } from '../../projects/data/project-invitation-api.service';
import type { Project } from '../../projects/model/project';
import type { ProjectCollaborators } from '../../projects/model/project-invitation';
import { NotificationStore } from '../notifications/notification.store';
import { ActivityApiService } from './activity-api.service';
import { ActivityFeed } from './activity-feed';
import { type ActivityFilters, type ProjectActivity, type ProjectSession, operationLabels, sourceLabels } from './activity-model';
import { SinceLastVisitCardComponent } from './since-last-visit-card.component';

@Component({
  selector: 'app-activity-page', imports: [DatePipe, FormsModule, MatButtonModule, RouterLink, SinceLastVisitCardComponent],
  templateUrl: './activity.page.html', styleUrl: './activity.page.scss',
})
export class ActivityPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projects = inject(ProjectApiService);
  private readonly invitations = inject(ProjectInvitationApiService);
  private readonly api = inject(ActivityApiService);
  private readonly notifications = inject(NotificationStore);
  private readonly destroy = inject(DestroyRef);
  private readonly feed = new ActivityFeed();
  private projectId = '';
  private generation = 0;
  private sessionPage = 0;
  private sessionRequest = 0;
  private detailRequest = 0;
  private headRequest = 0;
  readonly project = signal<Project | null>(null);
  readonly team = signal<ProjectCollaborators | null>(null);
  readonly loading = signal(false);
  readonly activityLoading = signal(false);
  readonly sessionsLoading = signal(false);
  readonly closing = signal(false);
  readonly error = signal<string | null>(null);
  readonly activityError = signal<string | null>(null);
  readonly sessionError = signal<string | null>(null);
  readonly detailsError = signal<string | null>(null);
  readonly items = signal<ProjectActivity[]>([]);
  readonly total = signal(0);
  readonly hasMore = signal(false);
  readonly sessions = signal<ProjectSession[]>([]);
  readonly sessionsMore = signal(false);
  readonly selectedSession = signal<ProjectSession | null>(null);
  readonly selectedActivity = signal<ProjectActivity | null>(null);
  readonly operationOptions = Object.entries(operationLabels);
  readonly operationLabels = operationLabels;
  readonly sourceLabels = sourceLabels;
  actor = ''; operation = ''; from = ''; to = ''; after: number | null = null; before: number | null = null; sessionFilter = ''; sessionStatus = '';

  constructor() {
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(([params, query]) => ({ id: params.get('id') ?? '', session: query.get('sessionId') ?? '' })),
      distinctUntilChanged((a, b) => a.id === b.id && a.session === b.session), takeUntilDestroyed(),
    ).subscribe(({ id, session }) => this.open(id, session));
    this.notifications.updates.pipe(takeUntilDestroyed()).subscribe(event => {
      if (event.notification?.projectId === this.projectId) { this.refreshHead(); this.loadSessions(); }
    });
    const timer = setInterval(() => { if (this.project() && !this.activityLoading() && document.visibilityState !== 'hidden') this.refreshHead(); }, 10000);
    this.destroy.onDestroy(() => { this.generation++; this.feed.reset(''); clearInterval(timer); });
  }
  private open(id: string, session = ''): void {
    const generation = ++this.generation; this.projectId = id;
    this.project.set(null); this.team.set(null); this.items.set([]); this.sessions.set([]); this.total.set(0); this.hasMore.set(false);
    this.selectedSession.set(null); this.selectedActivity.set(null); this.detailsError.set(null); this.closing.set(false);
    this.actor = ''; this.operation = ''; this.from = ''; this.to = ''; this.after = null; this.before = null; this.sessionFilter = session; this.sessionStatus = '';
    this.feed.reset(id); this.loading.set(true); this.error.set(null);
    forkJoin({ project: this.projects.get(id), team: this.invitations.collaborators(id).pipe(catchError(() => of(null))) })
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: result => {
          if (generation !== this.generation) return;
          this.project.set(result.project); this.team.set(result.team); this.loading.set(false);
          this.applyFilters(); this.loadSessions();
          if (session) this.viewSession(session);
        },
        error: () => { if (generation !== this.generation) return; this.loading.set(false); this.error.set('No pudimos abrir la actividad. Comprueba tu acceso al proyecto.'); },
      });
  }
  retry(): void { this.open(this.projectId, this.sessionFilter); }
  applyFilters(): void {
    const filters: ActivityFilters = {};
    if (this.actor) filters.actorId = this.actor;
    if (this.operation) filters.operationType = this.operation;
    if (this.sessionFilter) filters.sessionId = this.sessionFilter;
    if (this.from) filters.from = new Date(this.from).toISOString();
    if (this.to) filters.to = new Date(this.to).toISOString();
    if (this.after !== null && this.after !== undefined) filters.afterRevision = Number(this.after);
    if (this.before !== null && this.before !== undefined) filters.beforeRevision = Number(this.before);
    if ((filters.from && filters.to && filters.from > filters.to) ||
      (filters.afterRevision !== undefined && filters.beforeRevision !== undefined && filters.afterRevision >= filters.beforeRevision)) {
      this.activityError.set('Revisa el orden de las fechas y las revisiones.'); return;
    }
    this.feed.reset(this.projectId, filters); this.items.set([]); this.total.set(0); this.hasMore.set(false); this.loadActivities();
  }
  clearFilters(): void {
    this.actor = ''; this.operation = ''; this.from = ''; this.to = ''; this.after = null; this.before = null; this.sessionFilter = '';
    this.applyFilters();
  }
  loadActivities(older = false): void {
    const token = this.feed.token(); this.activityLoading.set(true); this.activityError.set(null);
    this.api.activities(this.projectId, older ? this.feed.olderFilters() : this.feed.filters).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: page => { if (!this.feed.merge(token, page, older)) return; this.publishFeed(); this.activityLoading.set(false); },
      error: () => { if (!this.feed.accepts(token)) return; this.activityLoading.set(false); this.activityError.set('No pudimos cargar la actividad. Revisa los filtros o vuelve a intentarlo.'); },
    });
  }
  refreshHead(): void {
    if (!this.project() || this.activityLoading()) return;
    const token = this.feed.token(), request = ++this.headRequest;
    this.api.activities(this.projectId, this.feed.filters).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: page => { if (request === this.headRequest && this.feed.refresh(token, page)) this.publishFeed(); },
      error: () => { if (this.feed.accepts(token)) this.activityError.set('No pudimos actualizar la actividad.'); },
    });
  }
  private publishFeed(): void { this.items.set([...this.feed.items]); this.total.set(this.feed.total); this.hasMore.set(this.feed.hasMore); }
  loadSessions(older = false): void {
    const request = ++this.sessionRequest, generation = this.generation, page = older ? this.sessionPage + 1 : 0;
    this.sessionsLoading.set(true); this.sessionError.set(null);
    this.api.sessions(this.projectId, page, this.sessionStatus).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => {
        if (generation !== this.generation || request !== this.sessionRequest) return;
        const records = new Map((older ? this.sessions() : []).map(item => [item.id, item]));
        result.items.forEach(item => records.set(item.id, item)); this.sessions.set([...records.values()]);
        this.sessionPage = page; this.sessionsMore.set(page + 1 < result.totalPages); this.sessionsLoading.set(false);
        const selected = this.selectedSession(); if (selected && records.has(selected.id)) this.selectedSession.set(records.get(selected.id)!);
      },
      error: () => { if (generation !== this.generation || request !== this.sessionRequest) return; this.sessionsLoading.set(false); this.sessionError.set('No pudimos recuperar las sesiones.'); },
    });
  }
  viewSession(id: string): void {
    const generation = this.generation, request = ++this.detailRequest; this.detailsError.set(null);
    this.api.session(this.projectId, id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: session => { if (generation === this.generation && request === this.detailRequest) this.selectedSession.set(session); },
      error: () => { if (generation === this.generation && request === this.detailRequest) this.detailsError.set('No pudimos abrir la sesión.'); },
    });
  }
  filterSession(session: ProjectSession): void { this.sessionFilter = session.id; this.applyFilters(); }
  closeSession(session: ProjectSession): void {
    if (this.closing()) return;
    const generation = this.generation; this.closing.set(true); this.detailsError.set(null);
    this.api.closeSession(this.projectId, session.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => { if (generation !== this.generation) return; this.selectedSession.set(result); this.closing.set(false); this.loadSessions(); },
      error: () => { if (generation !== this.generation) return; this.closing.set(false); this.detailsError.set('No pudimos cerrar la sesión.'); },
    });
  }
  viewActivity(activity: ProjectActivity): void {
    this.selectedActivity.set(activity);
  }
  payload(activity: ProjectActivity): string { try { return JSON.stringify(JSON.parse(activity.payloadJson), null, 2); } catch { return activity.payloadJson; } }
}
