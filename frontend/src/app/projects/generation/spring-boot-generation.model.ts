export interface SpringBootGenerationRequest {
  baseRevision: number;
  artifactName: string;
  basePackage: string;
  useFirstAttributeAsIdentifier: boolean;
}

export interface SpringBootGenerationDiagnostic {
  code: string;
  elementId: string | null;
  path: string | null;
  message: string;
}

export interface SpringBootGenerationPrimaryKeyFallback {
  className: string;
  attributeName: string;
}

export interface SpringBootGenerationErrorPayload {
  error: string;
  message: string;
  diagnostics?: SpringBootGenerationDiagnostic[];
  primaryKeyFallbacks?: SpringBootGenerationPrimaryKeyFallback[];
  requestedRevision?: number;
  currentRevision?: number;
}

export interface SpringBootGenerationDownload {
  content: Blob;
  fileName: string;
}

export class SpringBootGenerationApiError extends Error {
  constructor(
    readonly status: number,
    readonly payload: SpringBootGenerationErrorPayload | null,
  ) {
    super(
      payload?.message
        ?? 'No se pudo generar el proyecto Spring Boot.',
    );
    this.name = 'SpringBootGenerationApiError';
  }

  get code(): string | null {
    return this.payload?.error ?? null;
  }
}
