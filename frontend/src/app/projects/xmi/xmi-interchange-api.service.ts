import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Project } from '../model/project';

export type XmiDiagnosticSeverity = 'WARNING' | 'INFO';

export interface XmiDiagnostic {
  severity: XmiDiagnosticSeverity;
  code: string;
  message: string;
}

export interface XmiImportPreview {
  previewToken: string;
  baseRevision: number;
  expiresAt: string;
  packageCount: number;
  classCount: number;
  attributeCount: number;
  relationshipCount: number;
  diagnostics: XmiDiagnostic[];
}

@Injectable({ providedIn: 'root' })
export class XmiInterchangeApiService {
  private readonly http = inject(HttpClient);

  previewImport(projectId: string, file: File): Observable<XmiImportPreview> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<XmiImportPreview>(
      `/api/projects/${projectId}/xmi/import/preview`,
      body,
    );
  }

  applyImport(projectId: string, previewToken: string): Observable<Project> {
    return this.http.post<Project>(
      `/api/projects/${projectId}/xmi/import/apply`,
      { previewToken },
    );
  }

  export(projectId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(
      `/api/projects/${projectId}/xmi/export`,
      {
        observe: 'response',
        responseType: 'blob',
      },
    );
  }
}
