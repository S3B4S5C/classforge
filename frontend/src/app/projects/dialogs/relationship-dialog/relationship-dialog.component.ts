import { Component, inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

import {
  Multiplicity,
  UmlClass,
  UmlRelationship,
  UmlRelationshipType,
} from '../../model/project';

export interface RelationshipDialogData {
  mode: 'create' | 'edit';
  classes: UmlClass[];
  relationship?: UmlRelationship;
  sourceClassId?: string;
  targetClassId?: string;
}

export interface RelationshipDialogResult {
  sourceClassId: string;
  targetClassId: string;
  type: UmlRelationshipType;
  sourceMultiplicity: Multiplicity | null;
  targetMultiplicity: Multiplicity | null;
  associationClassId: string | null;
}

type MultiplicityOption =
  | '1'
  | '0..1'
  | '0..*'
  | '1..*';

@Component({
  selector: 'app-relationship-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{
        data.mode === 'create'
          ? 'Nueva relacion'
          : 'Editar relacion'
      }}
    </h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        {{
          form.controls.type.value
            === 'GENERALIZATION'
            ? 'En generalizacion, el origen es la subclase y el destino es la superclase.'
            : form.controls.type.value === 'AGGREGATION'
              || form.controls.type.value === 'COMPOSITION'
              ? 'En agregacion y composicion, el origen es el lado del rombo (el todo o propietario).'
              : 'Define los extremos y multiplicidades de la asociacion.'
        }}
      </p>

      <form
        id="relationship-form"
        class="relationship-form"
        [formGroup]="form"
        (ngSubmit)="submit()"
      >
        <mat-form-field appearance="outline">
          <mat-label>
            {{
              form.controls.type.value
                === 'GENERALIZATION'
                ? 'Subclase'
                : 'Origen'
            }}
          </mat-label>

          <mat-select
            formControlName="sourceClassId"
          >
            @for (
              umlClass of data.classes;
              track umlClass.id
            ) {
              <mat-option
                [value]="umlClass.id"
              >
                {{ umlClass.name }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>
            {{
              form.controls.type.value
                === 'GENERALIZATION'
                ? 'Superclase'
                : 'Destino'
            }}
          </mat-label>

          <mat-select
            formControlName="targetClassId"
          >
            @for (
              umlClass of data.classes;
              track umlClass.id
            ) {
              <mat-option
                [value]="umlClass.id"
              >
                {{ umlClass.name }}
              </mat-option>
            }
          </mat-select>

          @if (
            form.controls.targetClassId
              .hasError('selfGeneralization')
          ) {
            <mat-error>
              Una clase no puede heredar de si misma.
            </mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Tipo UML</mat-label>

          <mat-select formControlName="type">
            @for (
              option of typeOptions;
              track option.value
            ) {
              <mat-option
                [value]="option.value"
              >
                {{ option.label }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (
          form.controls.type.value
            !== 'GENERALIZATION'
        ) {
          <div class="multiplicity-grid">
            <mat-form-field appearance="outline">
              <mat-label>
                Multiplicidad origen
              </mat-label>

              <mat-select
                formControlName="sourceMultiplicity"
              >
                @for (
                  option of multiplicityOptions;
                  track option
                ) {
                  <mat-option [value]="option">
                    {{ option }}
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>
                Multiplicidad destino
              </mat-label>

              <mat-select
                formControlName="targetMultiplicity"
              >
                @for (
                  option of multiplicityOptions;
                  track option
                ) {
                  <mat-option [value]="option">
                    {{ option }}
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
        }

        @if (form.controls.type.value === 'ASSOCIATION') {
          <mat-form-field appearance="outline">
            <mat-label>Clase de asociacion (opcional)</mat-label>
            <mat-select formControlName="associationClassId">
              <mat-option value="">Sin clase intermedia</mat-option>
              @for (umlClass of data.classes; track umlClass.id) {
                @if (umlClass.id !== form.controls.sourceClassId.value
                  && umlClass.id !== form.controls.targetClassId.value) {
                  <mat-option [value]="umlClass.id">{{ umlClass.name }}</mat-option>
                }
              }
            </mat-select>
          </mat-form-field>
          @if (form.controls.associationClassId.value) {
            <p class="dialog-copy">
              Esta clase pertenece a la asociacion y contiene sus atributos.
              Ambos extremos deben tener multiplicidad 0..* o 1..*.
              Crea primero la clase intermedia con el boton Nueva clase.
            </p>
          }
        }
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
        form="relationship-form"
      >
        <span
          class="material-symbols-rounded"
          aria-hidden="true"
        >
          {{
            data.mode === 'create'
              ? 'add_link'
              : 'check'
          }}
        </span>

        {{
          data.mode === 'create'
            ? 'Crear relacion'
            : 'Guardar'
        }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(560px, 82vw);
    }

    .dialog-copy {
      max-width: 580px;
      margin: 0 0 1rem;
      color: var(--cf-muted);
      line-height: 1.6;
    }

    .relationship-form {
      display: grid;
      gap: 0.2rem;
    }

    .multiplicity-grid {
      display: grid;
      grid-template-columns:
        repeat(2, minmax(0, 1fr));
      gap: 0.75rem;
    }

    mat-form-field {
      width: 100%;
    }

    button {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }

    @media (max-width: 560px) {
      mat-dialog-content {
        min-width: 0;
      }

      .multiplicity-grid {
        grid-template-columns: 1fr;
        gap: 0;
      }
    }
  `,
})
export class RelationshipDialogComponent {
  readonly data =
    inject<RelationshipDialogData>(
      MAT_DIALOG_DATA,
    );

  readonly dialogRef = inject(
    MatDialogRef<
      RelationshipDialogComponent,
      RelationshipDialogResult
    >,
  );

  readonly typeOptions: {
    value: UmlRelationshipType;
    label: string;
  }[] = [
    {
      value: 'ASSOCIATION',
      label: 'Asociacion',
    },
    {
      value: 'AGGREGATION',
      label: 'Agregacion',
    },
    {
      value: 'COMPOSITION',
      label: 'Composicion',
    },
    {
      value: 'GENERALIZATION',
      label: 'Generalizacion',
    },
  ];

  readonly multiplicityOptions:
    MultiplicityOption[] = [
      '1',
      '0..1',
      '0..*',
      '1..*',
    ];

  readonly form = new FormGroup({
    sourceClassId: new FormControl(
      this.data.relationship?.sourceClassId
        ?? this.data.sourceClassId
        ?? '',
      {
        nonNullable: true,
        validators: [Validators.required],
      },
    ),
    targetClassId: new FormControl(
      this.data.relationship?.targetClassId
        ?? this.data.targetClassId
        ?? '',
      {
        nonNullable: true,
        validators: [Validators.required],
      },
    ),
    type: new FormControl<UmlRelationshipType>(
      this.data.relationship?.type
        ?? 'ASSOCIATION',
      {
        nonNullable: true,
        validators: [Validators.required],
      },
    ),
    sourceMultiplicity:
      new FormControl<MultiplicityOption>(
        this.toOption(
          this.data.relationship
            ?.sourceMultiplicity,
        ),
        {
          nonNullable: true,
          validators: [Validators.required],
        },
      ),
    associationClassId: new FormControl(
      this.data.relationship?.associationClassId ?? '',
      { nonNullable: true },
    ),
    targetMultiplicity:
      new FormControl<MultiplicityOption>(
        this.toOption(
          this.data.relationship
            ?.targetMultiplicity,
          '0..*',
        ),
        {
          nonNullable: true,
          validators: [Validators.required],
        },
      ),
  });

  constructor() {
    this.form.valueChanges.subscribe(
      () => this.validateGeneralization(),
    );

    this.validateGeneralization();
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.validateGeneralization();

    if (this.form.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    if (raw.associationClassId && raw.type === 'ASSOCIATION'
      && (!['0..*', '1..*'].includes(raw.sourceMultiplicity)
        || !['0..*', '1..*'].includes(raw.targetMultiplicity))) {
      this.form.controls.associationClassId.setErrors({ manyToManyRequired: true });
      return;
    }
    if (raw.associationClassId &&
      (raw.associationClassId === raw.sourceClassId
        || raw.associationClassId === raw.targetClassId)) {
      this.form.controls.associationClassId.setErrors({ invalidEndpoint: true });
      return;
    }

    const isGeneralization =
      raw.type === 'GENERALIZATION';

    this.dialogRef.close({
      sourceClassId: raw.sourceClassId,
      targetClassId: raw.targetClassId,
      type: raw.type,
      associationClassId: raw.type === 'ASSOCIATION'
        ? (raw.associationClassId || null)
        : null,
      sourceMultiplicity:
        isGeneralization
          ? null
          : this.parseMultiplicity(
              raw.sourceMultiplicity,
            ),
      targetMultiplicity:
        isGeneralization
          ? null
          : this.parseMultiplicity(
              raw.targetMultiplicity,
            ),
    });
  }

  private validateGeneralization(): void {
    const source =
      this.form.controls.sourceClassId.value;

    const target =
      this.form.controls.targetClassId.value;

    const isGeneralization =
      this.form.controls.type.value
        === 'GENERALIZATION';

    const currentErrors = {
      ...(
        this.form.controls
          .targetClassId.errors ?? {}
      ),
    };

    delete currentErrors['selfGeneralization'];

    if (
      isGeneralization
      && source
      && target
      && source === target
    ) {
      currentErrors['selfGeneralization'] =
        true;
    }

    this.form.controls.targetClassId
      .setErrors(
        Object.keys(currentErrors).length
          > 0
          ? currentErrors
          : null,
        {
          emitEvent: false,
        },
      );
  }

  private parseMultiplicity(
    option: MultiplicityOption,
  ): Multiplicity {
    switch (option) {
      case '0..1':
        return {
          lower: 0,
          upper: 1,
        };
      case '0..*':
        return {
          lower: 0,
          upper: null,
        };
      case '1..*':
        return {
          lower: 1,
          upper: null,
        };
      default:
        return {
          lower: 1,
          upper: 1,
        };
    }
  }

  private toOption(
    multiplicity:
      | Multiplicity
      | null
      | undefined,
    fallback: MultiplicityOption = '1',
  ): MultiplicityOption {
    if (!multiplicity) {
      return fallback;
    }

    if (
      multiplicity.lower === 0
      && multiplicity.upper === 1
    ) {
      return '0..1';
    }

    if (
      multiplicity.lower === 0
      && multiplicity.upper === null
    ) {
      return '0..*';
    }

    if (
      multiplicity.lower === 1
      && multiplicity.upper === null
    ) {
      return '1..*';
    }

    return '1';
  }
}