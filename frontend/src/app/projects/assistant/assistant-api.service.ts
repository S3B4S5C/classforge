import {
  HttpClient,
} from '@angular/common/http';
import {
  Injectable,
  inject,
} from '@angular/core';

import {
  AssistantPlanResponse,
  AssistantRuntimeHealthResponse,
} from './assistant-model';

@Injectable({
  providedIn: 'root',
})
export class AssistantApiService {
  private readonly http =
    inject(HttpClient);

  plan(
    projectId: string,
    text: string,
  ) {
    return this.http.post<AssistantPlanResponse>(
      `/api/projects/${projectId}/assistant/plan`,
      { text },
    );
  }

  voice(
    projectId: string,
    audio: Blob,
  ) {
    const form =
      new FormData();

    form.append(
      'audio',
      audio,
      'classforge-voice.wav',
    );

    return this.http.post<AssistantPlanResponse>(
      `/api/projects/${projectId}/assistant/voice`,
      form,
    );
  }

  health(
    projectId: string,
  ) {
    return this.http.get<AssistantRuntimeHealthResponse>(
      `/api/projects/${projectId}/assistant/health`,
    );
  }
}