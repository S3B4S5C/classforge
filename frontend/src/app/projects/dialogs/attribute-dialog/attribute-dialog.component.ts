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
import { MatCheckboxModule } from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import {
  UmlAttribute,
  UmlDataType,
  UmlVisibility,
} from '../../model/project';

export interface AttributeDialogData {
  mode: 'create' | 'edit';
  className: string;
  attribute?: UmlAttribute;
  reservedNames: string[];
}

export interface AttributeDialogResult {
  name: string;
  dataType: UmlDataType;
  customTypeName: string | null;
  visibility: UmlVisibility;
  nullable: boolean;
  identifier: boolean;
}

@Component({
  selector: 'app-attribute-dialog',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.mode === 'create' ? 'Nuevo atributo' : 'Editar atributo' }}
    </h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        Clase <strong>{{ data.className }}</strong>
      </p>

      <form
        id="attribute-form"
        class="attribute-form"
        [formGroup]="form"
        (ngSubmit)="submit()"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nombre</mat-label>
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
            <mat-error>La clase ya tiene un atributo con ese nombre.</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Tipo</mat-label>
          <mat-select formControlName="dataType">
            @for (option of dataTypes; track option.value) {
              <mat-option [value]="option.value">
                {{ option.label }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (form.controls.dataType.value === 'CUSTOM') {
          <mat-form-field appearance="outline">
            <mat-label>Tipo personalizado</mat-label>
            <input
              matInput
              maxlength="120"
              autocomplete="off"
              formControlName="customTypeName"
              placeholder="Ej. Money"
            />

            @if (form.controls.customTypeName.hasError('required')) {
              <mat-error>Indica el nombre del tipo personalizado.</mat-error>
            } @else if (form.controls.customTypeName.hasError('pattern')) {
              <mat-error>Usa un nombre compatible con codigo.</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Visibilidad UML</mat-label>
          <mat-select formControlName="visibility">
            @for (option of visibilityOptions; track option.value) {
              <mat-option [value]="option.value">
                {{ option.symbol }} {{ option.label }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="flags">
          <mat-checkbox formControlName="identifier">
            Identificador
          </mat-checkbox>

          <mat-checkbox
            formControlName="nullable"
            [disabled]="form.controls.identifier.value"
          >
            Nullable
          </mat-checkbox>
        </div>
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
        form="attribute-form"
      >
        <span class="material-symbols-rounded" aria-hidden="true">
          {{ data.mode === 'create' ? 'add' : 'check' }}
        </span>
        {{ data.mode === 'create' ? 'Anadir' : 'Guardar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(520px, 82vw);
    }

    .dialog-copy {
      margin: 0 0 1rem;
      color: var(--cf-muted);
    }

    .attribute-form {
      display: grid;
      gap: 0.2rem;
    }

    mat-form-field {
      width: 100%;
    }

    .flags {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      margin-bottom: 0.6rem;
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

      .flags {
        display: grid;
        gap: 0.35rem;
      }
    }
  `,
})
export class AttributeDialogComponent {
  readonly data = inject<AttributeDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<AttributeDialogComponent, AttributeDialogResult>,
  );

  readonly dataTypes: { value: UmlDataType; label: string }[] = [
    { value: 'STRING', label: 'String' },
    { value: 'INTEGER', label: 'Integer' },
    { value: 'LONG', label: 'Long' },
    { value: 'DECIMAL', label: 'Decimal' },
    { value: 'BOOLEAN', label: 'Boolean' },
    { value: 'DATE', label: 'Date' },
    { value: 'DATETIME', label: 'DateTime' },
    { value: 'UUID', label: 'UUID' },
    { value: 'CUSTOM', label: 'Personalizado' },
  ];

  readonly visibilityOptions: {
    value: UmlVisibility;
    symbol: string;
    label: string;
  }[] = [
    { value: 'PUBLIC', symbol: '+', label: 'Public' },
    { value: 'PRIVATE', symbol: '-', label: 'Private' },
    { value: 'PROTECTED', symbol: '#', label: 'Protected' },
    { value: 'PACKAGE', symbol: '~', label: 'Package' },
  ];

  readonly form = new FormGroup({
    name: new FormControl(
      this.data.attribute?.name ?? '',
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
    dataType: new FormControl<UmlDataType>(
      this.data.attribute?.dataType ?? 'STRING',
      {
        nonNullable: true,
        validators: [Validators.required],
      },
    ),
    customTypeName: new FormControl(
      this.data.attribute?.customTypeName ?? '',
      { nonNullable: true },
    ),
    visibility: new FormControl<UmlVisibility>(
      this.data.attribute?.visibility ?? 'PRIVATE',
      {
        nonNullable: true,
        validators: [Validators.required],
      },
    ),
    nullable: new FormControl(
      this.data.attribute?.nullable ?? true,
      { nonNullable: true },
    ),
    identifier: new FormControl(
      this.data.attribute?.identifier ?? false,
      { nonNullable: true },
    ),
  });

  constructor() {
    this.form.controls.identifier.valueChanges.subscribe((identifier) => {
      if (identifier) {
        this.form.controls.nullable.setValue(false, { emitEvent: false });
      }
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    const raw = this.form.getRawValue();

    if (raw.dataType === 'CUSTOM') {
      this.form.controls.customTypeName.setValidators([
        Validators.required,
        Validators.maxLength(120),
        Validators.pattern(/^[A-Za-z_][A-Za-z0-9_]*$/),
      ]);
    } else {
      this.form.controls.customTypeName.clearValidators();
    }

    this.form.controls.customTypeName.updateValueAndValidity({
      emitEvent: false,
    });

    if (this.form.invalid) {
      return;
    }

    this.dialogRef.close({
      name: raw.name.trim(),
      dataType: raw.dataType,
      customTypeName:
        raw.dataType === 'CUSTOM'
          ? raw.customTypeName.trim()
          : null,
      visibility: raw.visibility,
      nullable: raw.identifier ? false : raw.nullable,
      identifier: raw.identifier,
    });
  }

  private duplicateNameValidator(): ValidatorFn {
    const current = this.data.attribute?.name.toLowerCase() ?? null;
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