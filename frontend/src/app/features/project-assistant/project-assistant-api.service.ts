import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { AssistantMessageRequest, AssistantMessageResponse, AssistantMessagesPage } from './project-assistant-model';

@Injectable({ providedIn: 'root' })
export class ProjectAssistantApiService {
  private readonly http = inject(HttpClient);
  history(projectId: string, before?: number) {
    const params: Record<string, string> = { limit: '50' };
    if (before !== undefined) params['before'] = String(before);
    return this.http.get<AssistantMessagesPage>(`/api/projects/${projectId}/assistant/messages`, { params });
  }
  send(projectId: string, request: AssistantMessageRequest) {
    return this.http.post<AssistantMessageResponse>(`/api/projects/${projectId}/assistant/messages`, request);
  }
}
