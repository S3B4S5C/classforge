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
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import {
  finalize,
} from 'rxjs';

import {
  SpringBootGenerationApiService,
} from '../../data/spring-boot-generation-api.service';
import {
  SpringBootGenerationMode,
} from '../../generation/spring-boot-generation.model';
import {
  SPRING_ARTIFACT_NAME_PATTERN,
  defaultSpringArtifactName,
  defaultSpringBasePackage,
  isValidSpringBasePackage,
  SpringBootGenerationErrorCopy,
  springBootGenerationErrorCopy,
} from '../../generation/spring-boot-generation.utils';
import {
  UmlAttribute,
  UmlClass,
} from '../../model/project';

export interface SpringBootExportDialogData {
  projectId: string;
  projectName: string;
  baseRevision: number;
  classes: UmlClass[];
}

@Component({
  selector: 'app-spring-boot-export-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      Generar sistema Spring Boot
    </h2>

    <mat-dialog-content>

      <form
        id="spring-boot-export-form"
        class="generation-form"
        [formGroup]="form"
        (ngSubmit)="generate()"
      >
        <section class="mode-section" aria-labelledby="generation-mode-title">
          <strong id="generation-mode-title">Modo de generacion</strong>
          <mat-radio-group
            formControlName="mode"
            class="mode-options"
            (change)="modeChanged()"
          >
            <mat-radio-button value="SIMPLE_CRUD">
              <span class="mode-title">CRUD simple</span>
              <span class="mode-description">
                API CRUD, busqueda, filtros, ordenamiento, paginacion,
                conteo y relaciones. Sin login ni JWT.
              </span>
            </mat-radio-button>

            <mat-radio-button value="AUTH_INFORMATION_SYSTEM">
              <span class="mode-title">Sistema de Informacion con Auth</span>
              <span class="mode-description">
                El mismo CRUD mas Spring Security, BCrypt, login y JWT.
              </span>
            </mat-radio-button>
          </mat-radio-group>
        </section>

        @if (authMode()) {
          <div class="auth-config">
            <p class="auth-copy">
              Selecciona la entidad que representara las cuentas y, dentro de
              ella, los atributos STRING usados para usuario y contrasena.
            </p>

            <mat-form-field appearance="outline">
              <mat-label>Tabla / entidad de autenticacion</mat-label>
              <mat-select
                formControlName="authClassId"
                (selectionChange)="authClassChanged()"
              >
                @for (umlClass of data.classes; track umlClass.id) {
                  <mat-option [value]="umlClass.id">
                    {{ umlClass.name }}
                  </mat-option>
                }
              </mat-select>
              <mat-hint>Debe corresponder a una clase UML real.</mat-hint>
              @if (form.controls.authClassId.hasError('required')) {
                <mat-error>Selecciona la entidad de autenticacion.</mat-error>
              }
            </mat-form-field>

            <div class="credential-grid">
              <mat-form-field appearance="outline">
                <mat-label>Atributo de usuario / login</mat-label>
                <mat-select formControlName="usernameAttributeId">
                  @for (attribute of authAttributes(); track attribute.id) {
                    <mat-option
                      [value]="attribute.id"
                      [disabled]="attribute.id === form.controls.passwordAttributeId.value"
                    >
                      {{ attribute.name }}
                    </mat-option>
                  }
                </mat-select>
                @if (form.controls.usernameAttributeId.hasError('required')) {
                  <mat-error>Selecciona el atributo de usuario.</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Atributo de contrasena</mat-label>
                <mat-select formControlName="passwordAttributeId">
                  @for (attribute of authAttributes(); track attribute.id) {
                    <mat-option
                      [value]="attribute.id"
                      [disabled]="attribute.id === form.controls.usernameAttributeId.value"
                    >
                      {{ attribute.name }}
                    </mat-option>
                  }
                </mat-select>
                @if (form.controls.passwordAttributeId.hasError('required')) {
                  <mat-error>Selecciona el atributo de contrasena.</mat-error>
                }
              </mat-form-field>
            </div>

            @if (authAttributes().length < 2) {
              <div class="auth-warning" role="alert">
                La entidad elegida necesita al menos dos atributos STRING
                distintos para usuario y contrasena.
              </div>
            }
          </div>
        }

        <mat-form-field appearance="outline">
          <mat-label>Nombre del artefacto</mat-label>
          <input
            matInput
            autocomplete="off"
            maxlength="63"
            formControlName="artifactName"
            placeholder="biblioteca-api"
          />
          <mat-hint>Minusculas, numeros y guiones.</mat-hint>
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
          <mat-hint>Ej. com.example.biblioteca</mat-hint>
          @if (form.controls.basePackage.hasError('required')) {
            <mat-error>El package base es obligatorio.</mat-error>
          } @else if (form.controls.basePackage.hasError('javaPackage')) {
            <mat-error>
              Usa segmentos Java validos en minusculas y evita palabras reservadas.
            </mat-error>
          }
        </mat-form-field>

        <section class="color-config" aria-labelledby="primary-color-title">
          <div>
            <strong id="primary-color-title">Color primario del frontend</strong>
            <p>Se aplica a navegacion, acciones principales y acentos de las interfaces Angular y Flutter generadas.</p>
          </div>
          <label class="color-picker">
            <input type="color" formControlName="primaryColor" aria-label="Color primario" />
            <code>{{ form.controls.primaryColor.value }}</code>
          </label>
        </section>
      </form>

      @if (errorCopy(); as generationError) {
        <div
          class="generation-error"
          [class.is-stale]="generationError.staleRevision"
          [class.is-warning]="generationError.primaryKeyFallbackAvailable"
          role="alert"
        >
          <span class="material-symbols-rounded" aria-hidden="true">
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
        <span class="material-symbols-rounded" aria-hidden="true">inventory_2</span>
        <p>
          El ZIP incluye Gradle Wrapper, entidades/repositories JPA, DTOs,
          services y controllers, ademas de openapi.yaml, una coleccion Postman y
          domain-manifest.json con la semantica del dominio, un frontend Angular web y
          una app Flutter mobile para Android, ambos con dashboard y componentes
          especificos por entidad. En Auth tambien incluye Security, BCrypt, bootstrap
          inicial, login, JWT y proteccion de navegacion.
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
      width: min(640px, 84vw);
    }

    .dialog-copy,
    .auth-copy {
      color: var(--cf-muted);
      line-height: 1.55;
    }

    .dialog-copy {
      margin: 0 0 1rem;
    }

    .generation-form {
      display: grid;
      gap: 0.65rem;
    }

    .mode-section {
      display: grid;
      gap: 0.65rem;
      padding: 0.85rem;
      border: 1px solid #e2e8f0;
      border-radius: 0.8rem;
    }

    .mode-options {
      display: grid;
      gap: 0.55rem;
    }

    .mode-options mat-radio-button {
      display: block;
    }

    .mode-title,
    .mode-description {
      display: block;
    }

    .mode-title {
      font-weight: 650;
    }

    .mode-description {
      margin-top: 0.15rem;
      color: var(--cf-muted);
      font-size: 0.82rem;
      line-height: 1.4;
    }

    .auth-config {
      display: grid;
      gap: 0.2rem;
      padding: 0.85rem;
      border-radius: 0.8rem;
      background: #f8fafc;
    }

    .auth-copy {
      margin: 0 0 0.65rem;
    }

    .credential-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.65rem;
    }

    .auth-warning {
      margin-bottom: 0.55rem;
      padding: 0.65rem 0.75rem;
      border-radius: 0.65rem;
      background: #fff7ed;
      color: #9a3412;
      font-size: 0.82rem;
    }

    .color-config {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.85rem;
      border: 1px solid #e2e8f0;
      border-radius: 0.8rem;
    }

    .color-config p {
      margin: 0.2rem 0 0;
      color: var(--cf-muted);
      font-size: 0.82rem;
      line-height: 1.4;
    }

    .color-picker {
      display: flex;
      align-items: center;
      gap: 0.55rem;
    }

    .color-picker input {
      width: 2.75rem;
      height: 2.25rem;
      padding: 0.1rem;
      border: 1px solid #cbd5e1;
      border-radius: 0.5rem;
      background: white;
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
      margin-top: 0.6rem;
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

    @media (max-width: 640px) {
      mat-dialog-content {
        width: auto;
        min-width: 0;
      }

      .credential-grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class SpringBootExportDialogComponent {
  readonly data = inject<SpringBootExportDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<SpringBootExportDialogComponent>);
  private readonly generationApi = inject(SpringBootGenerationApiService);

  readonly generating = signal(false);
  readonly errorCopy = signal<SpringBootGenerationErrorCopy | null>(null);

  private readonly defaultArtifact = defaultSpringArtifactName(this.data.projectName);

  readonly form = new FormGroup({
    mode: new FormControl<SpringBootGenerationMode>('SIMPLE_CRUD', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    authClassId: new FormControl<string | null>(null),
    usernameAttributeId: new FormControl<string | null>(null),
    passwordAttributeId: new FormControl<string | null>(null),
    artifactName: new FormControl(this.defaultArtifact, {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(SPRING_ARTIFACT_NAME_PATTERN),
      ],
    }),
    basePackage: new FormControl(defaultSpringBasePackage(this.defaultArtifact), {
      nonNullable: true,
      validators: [Validators.required, this.javaPackageValidator()],
    }),
    primaryColor: new FormControl('#2563EB', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)],
    }),
  });

  authMode(): boolean {
    return this.form.controls.mode.value === 'AUTH_INFORMATION_SYSTEM';
  }

  authAttributes(): UmlAttribute[] {
    const selectedClass = this.data.classes.find(
      (umlClass) => umlClass.id === this.form.controls.authClassId.value,
    );
    return (selectedClass?.attributes ?? [])
      .filter((attribute) => attribute.dataType === 'STRING');
  }

  modeChanged(): void {
    if (!this.authMode()) {
      this.form.controls.authClassId.setValue(null);
      this.form.controls.usernameAttributeId.setValue(null);
      this.form.controls.passwordAttributeId.setValue(null);
    }
    this.updateAuthValidators();
  }

  authClassChanged(): void {
    this.form.controls.usernameAttributeId.setValue(null);
    this.form.controls.passwordAttributeId.setValue(null);
  }

  generate(useFirstAttributeAsIdentifier = false): void {
    this.updateAuthValidators();
    this.form.markAllAsTouched();

    if (this.form.invalid || this.generating()) {
      return;
    }

    this.generating.set(true);
    this.errorCopy.set(null);
    const value = this.form.getRawValue();

    this.generationApi
      .generate(this.data.projectId, {
        baseRevision: this.data.baseRevision,
        artifactName: value.artifactName,
        basePackage: value.basePackage,
        useFirstAttributeAsIdentifier,
        mode: value.mode,
        authClassId: value.mode === 'AUTH_INFORMATION_SYSTEM' ? value.authClassId : null,
        usernameAttributeId: value.mode === 'AUTH_INFORMATION_SYSTEM' ? value.usernameAttributeId : null,
        passwordAttributeId: value.mode === 'AUTH_INFORMATION_SYSTEM' ? value.passwordAttributeId : null,
        primaryColor: value.primaryColor.toUpperCase(),
      })
      .pipe(finalize(() => this.generating.set(false)))
      .subscribe({
        next: (download) => {
          this.download(download.content, download.fileName);
          this.dialogRef.close(true);
        },
        error: (error: unknown) => {
          console.error('Spring Boot generation failed', error);
          this.errorCopy.set(springBootGenerationErrorCopy(error));
        },
      });
  }

  private updateAuthValidators(): void {
    const validators = this.authMode() ? [Validators.required] : [];
    for (const control of [
      this.form.controls.authClassId,
      this.form.controls.usernameAttributeId,
      this.form.controls.passwordAttributeId,
    ]) {
      control.setValidators(validators);
      control.updateValueAndValidity({ emitEvent: false });
    }
  }

  private javaPackageValidator(): ValidatorFn {
    return (control: AbstractControl<string>): ValidationErrors | null =>
      isValidSpringBasePackage(control.value) ? null : { javaPackage: true };
  }

  private download(content: Blob, fileName: string): void {
    const url = URL.createObjectURL(content);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
