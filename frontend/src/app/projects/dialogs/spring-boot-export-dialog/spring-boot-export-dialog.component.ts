import {
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  finalize,
} from 'rxjs';

import {
  SpringBootGenerationApiService,
} from '../../data/spring-boot-generation-api.service';
import {
  SPRING_ARTIFACT_NAME_PATTERN,
  defaultSpringArtifactName,
  defaultSpringBasePackage,
  isValidSpringBasePackage,
  SpringBootGenerationErrorCopy,
  springBootGenerationErrorCopy,
} from '../../generation/spring-boot-generation.utils';

export interface SpringBootExportDialogData {
  projectId: string;
  projectName: string;
  baseRevision: number;
}

@Component({
  selector: 'app-spring-boot-export-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      Generar Spring Boot
    </h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        ClassForge generara un backend Spring Boot/JPA desde la revision
        sincronizada actual. La revision se envia automaticamente.
      </p>

      <form
        id="spring-boot-export-form"
        class="generation-form"
        [formGroup]="form"
        (ngSubmit)="generate()"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nombre del artefacto</mat-label>
          <input
            matInput
            autocomplete="off"
            maxlength="63"
            formControlName="artifactName"
            placeholder="biblioteca-api"
          />
          <mat-hint>
            Minusculas, numeros y guiones.
          </mat-hint>

          @if (form.controls.artifactName.hasError('required')) {
            <mat-error>El nombre del artefacto es obligatorio.</mat-error>
          } @else if (form.controls.artifactName.hasError('pattern')) {
            <mat-error>
              Debe iniciar con una letra minuscula y usar solo a-z, 0-9 o -.
            </mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Package base</mat-label>
          <input
            matInput
            autocomplete="off"
            formControlName="basePackage"
            placeholder="com.example.biblioteca"
          />
          <mat-hint>
            Ej. com.example.biblioteca
          </mat-hint>

          @if (form.controls.basePackage.hasError('required')) {
            <mat-error>El package base es obligatorio.</mat-error>
          } @else if (form.controls.basePackage.hasError('javaPackage')) {
            <mat-error>
              Usa segmentos Java validos en minusculas y evita palabras reservadas.
            </mat-error>
          }
        </mat-form-field>
      </form>

      @if (errorCopy(); as generationError) {
        <div
          class="generation-error"
          [class.is-stale]="generationError.staleRevision"
          [class.is-warning]="generationError.primaryKeyFallbackAvailable"
          role="alert"
        >
          <span
            class="material-symbols-rounded"
            aria-hidden="true"
          >
            {{ generationError.staleRevision
              ? 'sync_problem'
              : generationError.primaryKeyFallbackAvailable
                ? 'warning'
                : 'error' }}
          </span>

          <div>
            <strong>{{ generationError.title }}</strong>
            <p>{{ generationError.message }}</p>

            @if (generationError.observations.length > 0) {
              <ul class="generation-observations">
                @for (observation of generationError.observations; track observation) {
                  <li>{{ observation }}</li>
                }
              </ul>
            }

            @if (generationError.primaryKeyFallbackAvailable) {
              <button
                mat-stroked-button
                class="primary-key-fallback-action"
                type="button"
                [disabled]="generating()"
                (click)="generate(true)"
              >
                Continuar usando el primer atributo como clave primaria
              </button>
            }
          </div>
        </div>
      }

      <div class="generation-scope" aria-label="Contenido de la exportacion">
        <span class="material-symbols-rounded" aria-hidden="true">
          inventory_2
        </span>
        <p>
          El ZIP incluye el proyecto backend Spring Boot con su configuracion
          y Gradle Wrapper, listo para descargar.
        </p>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button
        mat-button
        class="cf-button-secondary"
        type="button"
        [disabled]="generating()"
        (click)="dialogRef.close()"
      >
        Cancelar
      </button>

      <button
        mat-flat-button
        class="cf-button-primary"
        type="submit"
        form="spring-boot-export-form"
        [disabled]="generating()"
      >
        <span
          class="material-symbols-rounded"
          [class.is-spinning]="generating()"
          aria-hidden="true"
        >
          {{ generating() ? 'progress_activity' : 'download' }}
        </span>
        {{ generating() ? 'Generando...' : 'Generar y descargar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: min(560px, 82vw);
    }

    .dialog-copy {
      margin: 0 0 1.1rem;
      color: var(--cf-muted);
      line-height: 1.6;
    }

    .generation-form {
      display: grid;
      gap: 0.35rem;
    }

    mat-form-field {
      width: 100%;
    }

    .generation-error,
    .generation-scope {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 0.7rem;
      align-items: start;
      border-radius: 0.75rem;
      padding: 0.8rem 0.9rem;
    }

    .generation-error {
      margin-top: 0.35rem;
      border: 1px solid #fecaca;
      background: #fef2f2;
      color: #991b1b;
    }

    .generation-error.is-stale,
    .generation-error.is-warning {
      border-color: #fed7aa;
      background: #fff7ed;
      color: #9a3412;
    }

    .generation-observations {
      margin: 0.65rem 0 0;
      padding-left: 1.1rem;
      display: grid;
      gap: 0.25rem;
    }

    .primary-key-fallback-action {
      margin-top: 0.75rem;
    }

    .generation-error strong,
    .generation-error p,
    .generation-scope p {
      margin: 0;
    }

    .generation-error p,
    .generation-scope p {
      margin-top: 0.18rem;
      line-height: 1.5;
    }

    .generation-scope {
      margin-top: 0.85rem;
      background: #f8fafc;
      color: var(--cf-muted);
      font-size: 0.82rem;
    }

    button {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }

    @media (max-width: 560px) {
      mat-dialog-content {
        width: auto;
        min-width: 0;
      }
    }
  `,
})
export class SpringBootExportDialogComponent {
  readonly data =
    inject<SpringBootExportDialogData>(
      MAT_DIALOG_DATA,
    );

  readonly dialogRef = inject(
    MatDialogRef<SpringBootExportDialogComponent>,
  );

  private readonly generationApi =
    inject(SpringBootGenerationApiService);

  readonly generating = signal(false);

  readonly errorCopy =
    signal<SpringBootGenerationErrorCopy | null>(
      null,
    );

  private readonly defaultArtifact =
    defaultSpringArtifactName(
      this.data.projectName,
    );

  readonly form = new FormGroup({
    artifactName: new FormControl(
      this.defaultArtifact,
      {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.pattern(
            SPRING_ARTIFACT_NAME_PATTERN,
          ),
        ],
      },
    ),
    basePackage: new FormControl(
      defaultSpringBasePackage(
        this.defaultArtifact,
      ),
      {
        nonNullable: true,
        validators: [
          Validators.required,
          this.javaPackageValidator(),
        ],
      },
    ),
  });

  generate(
    useFirstAttributeAsIdentifier = false,
  ): void {
    this.form.markAllAsTouched();

    if (
      this.form.invalid
      || this.generating()
    ) {
      return;
    }

    this.generating.set(true);
    this.errorCopy.set(null);

    const value = this.form.getRawValue();

    this.generationApi
      .generate(
        this.data.projectId,
        {
          baseRevision:
            this.data.baseRevision,
          artifactName:
            value.artifactName,
          basePackage:
            value.basePackage,
          useFirstAttributeAsIdentifier,
        },
      )
      .pipe(
        finalize(() =>
          this.generating.set(false),
        ),
      )
      .subscribe({
        next: (download) => {
          this.download(
            download.content,
            download.fileName,
          );

          this.dialogRef.close(true);
        },
        error: (error: unknown) => {
          console.error(
            'Spring Boot generation failed',
            error,
          );

          this.errorCopy.set(
            springBootGenerationErrorCopy(
              error,
            ),
          );
        },
      });
  }

  private javaPackageValidator(): ValidatorFn {
    return (
      control: AbstractControl<string>,
    ): ValidationErrors | null =>
      isValidSpringBasePackage(
        control.value,
      )
        ? null
        : { javaPackage: true };
  }

  private download(
    content: Blob,
    fileName: string,
  ): void {
    const url = URL.createObjectURL(content);
    const anchor = document.createElement('a');

    anchor.href = url;
    anchor.download = fileName;
    anchor.style.display = 'none';

    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    window.setTimeout(
      () => URL.revokeObjectURL(url),
      0,
    );
  }
}