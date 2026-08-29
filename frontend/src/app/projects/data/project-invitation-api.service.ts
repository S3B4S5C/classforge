import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ProjectCollaborators,
  ProjectInvitation,
} from '../model/project-invitation';

@Injectable({
  providedIn: 'root',
})
export class ProjectInvitationApiService {
  private readonly http = inject(HttpClient);

  pending(): Observable<ProjectInvitation[]> {
    return this.http.get<ProjectInvitation[]>(
      '/api/project-invitations',
    );
  }

  accept(invitationId: string): Observable<ProjectInvitation> {
    return this.http.post<ProjectInvitation>(
      `/api/project-invitations/${invitationId}/accept`,
      null,
    );
  }

  decline(invitationId: string): Observable<ProjectInvitation> {
    return this.http.post<ProjectInvitation>(
      `/api/project-invitations/${invitationId}/decline`,
      null,
    );
  }

  collaborators(projectId: string): Observable<ProjectCollaborators> {
    return this.http.get<ProjectCollaborators>(
      `/api/projects/${projectId}/collaborators`,
    );
  }

  invite(
    projectId: string,
    email: string,
  ): Observable<ProjectInvitation> {
    return this.http.post<ProjectInvitation>(
      `/api/projects/${projectId}/invitations`,
      { email },
    );
  }

  cancel(
    projectId: string,
    invitationId: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/projects/${projectId}/invitations/${invitationId}`,
    );
  }
}
