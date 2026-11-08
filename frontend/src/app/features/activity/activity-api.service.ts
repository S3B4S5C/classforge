import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { ActivityFilters, Paged, ProjectActivity, ProjectContinuity, ProjectSession } from './activity-model';

@Injectable({ providedIn: 'root' })
export class ActivityApiService {
  private readonly http = inject(HttpClient);
  activities(project: string, filters: ActivityFilters = {}, page = 0, size = 20) {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '') params = params.set(key, value);
    return this.http.get<Paged<ProjectActivity>>(`/api/projects/${project}/activities`, { params });
  }
  detail(project: string, id: string) { return this.http.get<ProjectActivity>(`/api/projects/${project}/activities/${id}`); }
  sessions(project: string, page = 0, status = '') {
    let params = new HttpParams().set('page', page).set('size', 10);
    if (status) params = params.set('status', status);
    return this.http.get<Paged<ProjectSession>>(`/api/projects/${project}/sessions`, { params });
  }
  session(project: string, id: string) { return this.http.get<ProjectSession>(`/api/projects/${project}/sessions/${id}`); }
  closeSession(project: string, id: string) { return this.http.post<ProjectSession>(`/api/projects/${project}/sessions/${id}/close`, {}); }
  continuity(project: string, size = 5) { return this.http.get<ProjectContinuity>(`/api/projects/${project}/since-last-visit`, { params: { size } }); }
  seen(project: string, revision: number) { return this.http.post(`/api/projects/${project}/visits`, { revision }); }
}
