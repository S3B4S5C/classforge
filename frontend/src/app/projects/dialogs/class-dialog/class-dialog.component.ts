import { Component, inject } from '@angular/core';
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

export interface ClassDialogData {
  mode: 'create' | 'edit';
  currentName?: string;
  reservedNames: string[];
}

@Component({
  selector: 'app-class-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.mode === 'create' ? 'Nueva clase' : 'Editar clase' }}
    </h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        Usa un nombre compatible con codigo. Ejemplos:
        <strong>Animal</strong>, <strong>Cita</strong>,
        <strong>Propietario</strong>.
      </p>

      <form
        id="class-form"
        [formGroup]="form"
        (ngSubmit)="submit()"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nombre de la clase</mat-label>
          <input
            matInput
            maxlength="80"
            autocomplete="off"
            formControlName="name"
          />

          @if (form.controls.name.hasError('required')) {
            <mat-error>El nombre es obligatorio.</mat-error>
          } @else if (form.controls.name.hasError('pattern')) {
            <mat-error>Usa letras, numeros y _, sin espacios.</mat-error>
          } @else if (form.controls.name.hasError('duplicate')) {
            <mat-error>Ya existe una clase con ese nombre.</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button
        mat-button
        class="cf-button-secondary"
        type="button"
        (click)="dialogRef.close()"
      >
        Cancelar
      </button>

      <button
        mat-flat-button
        class="cf-button-primary"
        type="submit"
        form="class-form"
      >
        <span class="material-symbols-rounded" aria-hidden="true">
          {{ data.mode === 'create' ? 'add' : 'check' }}
        </span>
        {{ data.mode === 'create' ? 'Crear clase' : 'Guardar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(430px, 78vw);
    }

    .dialog-copy {
      max-width: 520px;
      margin: 0 0 1.25rem;
      color: var(--cf-muted);
      line-height: 1.6;
    }

    form,
    mat-form-field {
      width: 100%;
    }

    button {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }

    @media (max-width: 520px) {
      mat-dialog-content {
        min-width: 0;
      }
    }
  `,
})
export class ClassDialogComponent {
  readonly data = inject<ClassDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<ClassDialogComponent, string>);

  readonly form = new FormGroup({
    name: new FormControl(
      this.data.currentName ?? '',
      {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.maxLength(80),
          Validators.pattern(/^[A-Za-z_][A-Za-z0-9_]*$/),
          this.duplicateNameValidator(),
        ],
      },
    ),
  });

  submit(): void {
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    this.dialogRef.close(
      this.form.controls.name.value.trim(),
    );
  }

  private duplicateNameValidator(): ValidatorFn {
    const current = this.data.currentName?.toLowerCase() ?? null;
    const reserved = new Set(
      this.data.reservedNames.map((name) => name.toLowerCase()),
    );

    return (
      control: AbstractControl<string>,
    ): ValidationErrors | null => {
      const value = control.value.trim().toLowerCase();

      if (!value || value === current) {
        return null;
      }

      return reserved.has(value) ? { duplicate: true } : null;
    };
  }
}