import {
  HttpClient,
  HttpErrorResponse,
} from '@angular/common/http';
import {
  inject,
  Injectable,
} from '@angular/core';
import {
  catchError,
  from,
  map,
  mergeMap,
  Observable,
  throwError,
} from 'rxjs';

import {
  SpringBootGenerationApiError,
  SpringBootGenerationDownload,
  SpringBootGenerationErrorPayload,
  SpringBootGenerationRequest,
} from '../generation/spring-boot-generation.model';
import {
  extractSpringDownloadFileName,
} from '../generation/spring-boot-generation.utils';

@Injectable({
  providedIn: 'root',
})
export class SpringBootGenerationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/projects';

  generate(
    projectId: string,
    request: SpringBootGenerationRequest,
  ): Observable<SpringBootGenerationDownload> {
    return this.http
      .post(
        `${this.baseUrl}/${projectId}/generation/spring-boot`,
        request,
        {
          observe: 'response',
          responseType: 'blob',
        },
      )
      .pipe(
        map((response) => {
          const content =
            response.body ?? new Blob();

          const fileName =
            extractSpringDownloadFileName(
              response.headers.get(
                'Content-Disposition',
              ),
              `${request.artifactName}-backend.zip`,
            );

          return {
            content,
            fileName,
          };
        }),
        catchError((error: unknown) =>
          this.normalizeError(error),
        ),
      );
  }

  private normalizeError(
    error: unknown,
  ): Observable<never> {
    if (!(error instanceof HttpErrorResponse)) {
      return throwError(
        () =>
          new SpringBootGenerationApiError(
            0,
            null,
          ),
      );
    }

    if (error.error instanceof Blob) {
      return from(
        this.parseBlobError(error.error),
      ).pipe(
        mergeMap((payload) =>
          throwError(
            () =>
              new SpringBootGenerationApiError(
                error.status,
                payload,
              ),
          ),
        ),
      );
    }

    return throwError(
      () =>
        new SpringBootGenerationApiError(
          error.status,
          this.asPayload(error.error),
        ),
    );
  }

  private async parseBlobError(
    blob: Blob,
  ): Promise<SpringBootGenerationErrorPayload | null> {
    try {
      const text = await blob.text();
      return this.asPayload(
        JSON.parse(text),
      );
    } catch {
      return null;
    }
  }

  private asPayload(
    value: unknown,
  ): SpringBootGenerationErrorPayload | null {
    if (
      !value
      || typeof value !== 'object'
      || !('error' in value)
      || typeof value.error !== 'string'
    ) {
      return null;
    }

    const candidate =
      value as Partial<SpringBootGenerationErrorPayload>;
    const errorCode =
      (value as { error: string }).error;

    return {
      error: errorCode,
      message:
        typeof candidate.message === 'string'
          ? candidate.message
          : 'No se pudo generar el proyecto Spring Boot.',
      diagnostics:
        Array.isArray(candidate.diagnostics)
          ? candidate.diagnostics
          : undefined,
      primaryKeyFallbacks:
        Array.isArray(candidate.primaryKeyFallbacks)
          ? candidate.primaryKeyFallbacks
          : undefined,
      requestedRevision:
        typeof candidate.requestedRevision === 'number'
          ? candidate.requestedRevision
          : undefined,
      currentRevision:
        typeof candidate.currentRevision === 'number'
          ? candidate.currentRevision
          : undefined,
    };
  }
}