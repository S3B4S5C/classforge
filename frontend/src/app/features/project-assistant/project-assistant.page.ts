import { SinceLastVisitCardComponent } from '../activity/since-last-visit-card.component';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { catchError, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { ProjectAssistantPanelComponent } from '../../projects/assistant/project-assistant-panel.component';
import { ProjectInvitationApiService } from '../../projects/data/project-invitation-api.service';
import type { ProjectCollaborators } from '../../projects/model/project-invitation';
import { ProjectPresenceService } from '../../projects/presence/project-presence.service';
import { ProjectWorkspaceStore } from '../../projects/state/project-workspace.store';

@Component({
  selector: 'app-project-assistant-page',
  imports: [RouterLink, MatButtonModule, ProjectAssistantPanelComponent, SinceLastVisitCardComponent],
  providers: [ProjectWorkspaceStore],
  templateUrl: './project-assistant.page.html',
  styleUrl: './project-assistant.page.scss',
})
export class ProjectAssistantPage {
  private readonly route = inject(ActivatedRoute);
  private readonly invitations = inject(ProjectInvitationApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly store = inject(ProjectWorkspaceStore);
  readonly presence = inject(ProjectPresenceService);
  readonly members = signal<ProjectCollaborators | null>(null);
  readonly membersError = signal(false);

  constructor() {
    this.route.paramMap.pipe(
      map(params => params.get('id')),
      distinctUntilChanged(),
      tap(id => {
        this.presence.detachProject();
        this.members.set(null);
        this.membersError.set(false);
        if (id) this.store.load(id);
      }),
      switchMap(id => id ? this.invitations.collaborators(id).pipe(catchError(() => {
        this.membersError.set(true);
        return of(null);
      })) : of(null)),
      takeUntilDestroyed(),
    ).subscribe(members => this.members.set(members));

    effect(() => {
      const projectId = this.store.project()?.id;
      if (projectId) this.presence.attachProject(projectId);
    });
    this.destroyRef.onDestroy(() => this.presence.detachProject());
  }

  online(userId: string): boolean {
    return this.presence.participants().some(participant => participant.actor.id === userId);
  }
  reload(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.store.load(id);
  }
}
