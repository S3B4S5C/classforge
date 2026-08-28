import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateProjectRequest,
  Project,
  SaveProjectDocumentRequest,
  UpdateProjectRequest,
} from '../model/project';

@Injectable({
  providedIn: 'root',
})
export class ProjectApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/projects';

  list(): Observable<Project[]> {
    return this.http.get<Project[]>(this.baseUrl);
  }

  get(projectId: string): Observable<Project> {
    return this.http.get<Project>(
      `${this.baseUrl}/${projectId}`,
    );
  }

  create(request: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(
      this.baseUrl,
      request,
    );
  }

  rename(
    projectId: string,
    request: UpdateProjectRequest,
  ): Observable<Project> {
    return this.http.patch<Project>(
      `${this.baseUrl}/${projectId}`,
      request,
    );
  }

  saveDocument(
    projectId: string,
    request: SaveProjectDocumentRequest,
  ): Observable<Project> {
    return this.http.put<Project>(
      `${this.baseUrl}/${projectId}/document`,
      request,
    );
  }
}