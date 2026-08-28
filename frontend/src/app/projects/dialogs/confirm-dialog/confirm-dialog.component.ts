import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  icon?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>

    <mat-dialog-content>
      <div class="confirm-copy">
        <span
          class="material-symbols-rounded confirm-icon"
          aria-hidden="true"
        >
          {{ data.icon ?? 'warning' }}
        </span>
        <p>{{ data.message }}</p>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button
        mat-button
        class="cf-button-secondary"
        type="button"
        (click)="dialogRef.close(false)"
      >
        Cancelar
      </button>

      <button
        mat-flat-button
        class="cf-button-primary"
        type="button"
        (click)="dialogRef.close(true)"
      >
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm-copy {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 0.8rem;
      align-items: start;
      max-width: 520px;
      color: var(--cf-muted);
      line-height: 1.65;
    }

    .confirm-copy p {
      margin: 0;
    }

    .confirm-icon {
      color: #b42318;
      font-size: 1.8rem;
    }
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
}