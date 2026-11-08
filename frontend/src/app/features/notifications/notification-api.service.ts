import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Paged } from '../activity/activity-model';
import type { ProjectNotification } from './notification-model';
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  list(page = 0) { return this.http.get<Paged<ProjectNotification>>('/api/notifications', { params: { page, size: 20 } }); }
  count() { return this.http.get<{ count: number }>('/api/notifications/unread-count'); }
  read(id: string) { return this.http.post<ProjectNotification>(`/api/notifications/${id}/read`, {}); }
  readAll() { return this.http.post<{ updated: number }>('/api/notifications/read-all', {}); }
}
