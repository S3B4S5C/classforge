import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { finalize } from 'rxjs';

import {
  XmiImportPreview,
  XmiInterchangeApiService,
} from './xmi-interchange-api.service';

export interface XmiInterchangeDialogData {
  projectId: string;
  projectName: string;
}

@Component({
  selector: 'app-xmi-interchange-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Enterprise Architect · XMI 2.1</h2>

    <mat-dialog-content>
      <p class="copy">
        Importa o exporta el subconjunto UML de ClassForge: clases, atributos,
        tipos, asociaciones, agregaciones, composiciones, generalizaciones y
        multiplicidades. Los paquetes XMI se aplanan al modelo canonico.
      </p>

      <section class="xmi-section">
        <div>
          <strong>Importar desde Enterprise Architect</strong>
          <p>El archivo se analiza y valida primero. El proyecto no cambia hasta que confirmes el preview.</p>
        </div>

        <label class="file-picker">
          <span class="material-symbols-rounded" aria-hidden="true">upload_file</span>
          <span>{{ selectedFile()?.name ?? 'Seleccionar .xmi o .xml' }}</span>
          <input
            type="file"
            accept=".xmi,.xml,application/xml,text/xml"
            [disabled]="busy()"
            (change)="fileSelected($event)"
          />
        </label>

        <button
          mat-stroked-button
          type="button"
          [disabled]="!selectedFile() || busy()"
          (click)="previewImport()"
        >
          {{ previewing() ? 'Analizando...' : 'Analizar XMI' }}
        </button>
      </section>

      @if (preview(); as currentPreview) {
        <section class="preview" aria-live="polite">
          <strong>Preview listo</strong>
          <div class="metrics">
            <span>{{ currentPreview.classCount }} clases</span>
            <span>{{ currentPreview.attributeCount }} atributos</span>
            <span>{{ currentPreview.relationshipCount }} relaciones</span>
            <span>{{ currentPreview.packageCount }} paquetes</span>
          </div>

          @if (currentPreview.diagnostics.length > 0) {
            <ul>
              @for (diagnostic of currentPreview.diagnostics; track diagnostic.code + diagnostic.message) {
                <li>
                  <strong>{{ diagnostic.severity }}</strong>
                  {{ diagnostic.message }}
                </li>
              }
            </ul>
          }

          <button
            mat-flat-button
            class="cf-button-primary"
            type="button"
            [disabled]="busy()"
            (click)="applyImport()"
          >
            {{ applying() ? 'Aplicando...' : 'Reemplazar UML con este XMI' }}
          </button>
        </section>
      }

      <section class="xmi-section export-section">
        <div>
          <strong>Exportar para Enterprise Architect</strong>
          <p>Genera XMI 2.1 determinista usando el modelo guardado actualmente.</p>
        </div>
        <button
          mat-stroked-button
          type="button"
          [disabled]="busy()"
          (click)="exportXmi()"
        >
          {{ exporting() ? 'Exportando...' : 'Descargar XMI 2.1' }}
        </button>
      </section>

      @if (message()) {
        <div class="message" [class.error]="error()" role="status">
          {{ message() }}
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy()" (click)="dialogRef.close()">
        Cerrar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content { width: min(680px, 86vw); display: grid; gap: 1rem; }
    .copy, .xmi-section p { color: var(--cf-muted); line-height: 1.5; margin: .35rem 0 0; }
    .xmi-section, .preview { border: 1px solid var(--cf-border); border-radius: 14px; padding: 1rem; display: grid; gap: .85rem; }
    .file-picker { border: 1px dashed var(--cf-border); border-radius: 12px; padding: .8rem; display: flex; align-items: center; gap: .55rem; cursor: pointer; }
    .file-picker input { display: none; }
    .metrics { display: flex; flex-wrap: wrap; gap: .5rem; }
    .metrics span { background: var(--cf-secondary-bg); border-radius: 999px; padding: .35rem .65rem; }
    ul { margin: 0; padding-left: 1.2rem; color: var(--cf-muted); }
    .message { border-radius: 10px; padding: .75rem; background: var(--cf-secondary-bg); }
    .message.error { color: #b42318; }
  `,
})
export class XmiInterchangeDialogComponent {
  readonly data = inject<XmiInterchangeDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<XmiInterchangeDialogComponent>);
  private readonly api = inject(XmiInterchangeApiService);

  readonly selectedFile = signal<File | null>(null);
  readonly preview = signal<XmiImportPreview | null>(null);
  readonly previewing = signal(false);
  readonly applying = signal(false);
  readonly exporting = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  readonly busy = () => this.previewing() || this.applying() || this.exporting();

  fileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.preview.set(null);
    this.message.set(null);
    this.error.set(false);
  }

  previewImport(): void {
    const file = this.selectedFile();
    if (!file || this.busy()) {
      return;
    }
    this.previewing.set(true);
    this.preview.set(null);
    this.message.set(null);
    this.error.set(false);
    this.api.previewImport(this.data.projectId, file)
      .pipe(finalize(() => this.previewing.set(false)))
      .subscribe({
        next: (preview) => this.preview.set(preview),
        error: (failure: HttpErrorResponse) => this.fail(failure, 'No pudimos analizar el XMI.'),
      });
  }

  applyImport(): void {
    const preview = this.preview();
    if (!preview || this.busy()) {
      return;
    }
    this.applying.set(true);
    this.message.set(null);
    this.error.set(false);
    this.api.applyImport(this.data.projectId, preview.previewToken)
      .pipe(finalize(() => this.applying.set(false)))
      .subscribe({
        next: () => this.dialogRef.close({ applied: true }),
        error: (failure: HttpErrorResponse) => this.fail(
          failure,
          failure.status === 409
            ? 'El proyecto cambio desde el preview. Analiza el XMI nuevamente.'
            : 'No pudimos aplicar el XMI.',
        ),
      });
  }

  exportXmi(): void {
    if (this.busy()) {
      return;
    }
    this.exporting.set(true);
    this.message.set(null);
    this.error.set(false);
    this.api.export(this.data.projectId)
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: (response) => {
          this.download(response);
          this.message.set('XMI 2.1 generado correctamente.');
        },
        error: (failure: HttpErrorResponse) => this.fail(failure, 'No pudimos exportar el XMI.'),
      });
  }

  private download(response: HttpResponse<Blob>): void {
    const body = response.body;
    if (!body) {
      throw new Error('Empty XMI response');
    }
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = /filename\*?=(?:UTF-8''|\")?([^\";]+)/i.exec(disposition);
    const filename = match ? decodeURIComponent(match[1].replace(/\"/g, '').trim()) : 'classforge-enterprise-architect.xmi';
    const url = URL.createObjectURL(body);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private fail(failure: HttpErrorResponse, fallback: string): void {
    const serverMessage = typeof failure.error === 'object' && failure.error?.message
      ? String(failure.error.message)
      : null;
    this.error.set(true);
    this.message.set(serverMessage ?? fallback);
  }
}
