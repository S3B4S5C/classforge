import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';

import { ProjectInvitationApiService } from '../../data/project-invitation-api.service';
import { ProjectAccessRole } from '../../model/project';
import { ProjectCollaborators } from '../../model/project-invitation';
import { ProjectPresenceService } from '../../presence/project-presence.service';

export interface CollaboratorsDialogData {
  projectId: string;
  projectName: string;
  accessRole: ProjectAccessRole;
}

@Component({
  selector: 'app-collaborators-dialog',
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <span class="dialog-title-row">
        <span class="material-symbols-rounded" aria-hidden="true">
          group
        </span>
        Colaboradores
      </span>
    </h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        {{ data.projectName }} ·
        {{ data.accessRole === 'OWNER' ? 'Propietario' : 'Compartido · Editor' }}
      </p>

      @if (loading()) {
        <div class="dialog-feedback" aria-live="polite">
          <mat-spinner diameter="32"></mat-spinner>
          <span>Cargando colaboradores...</span>
        </div>
      } @else if (errorMessage()) {
        <div class="dialog-feedback is-error" role="alert">
          <span class="material-symbols-rounded" aria-hidden="true">
            error
          </span>
          <span>{{ errorMessage() }}</span>
          <button
            mat-button
            class="cf-button-secondary"
            type="button"
            (click)="load()"
          >
            Reintentar
          </button>
        </div>
      } @else if (collaborators(); as collaboratorsData) {
        <section class="people-section" aria-labelledby="owner-title">
          <div class="section-title-row">
            <div>
              <p class="section-kicker">PROPIETARIO</p>
              <h3 id="owner-title">Responsable del proyecto</h3>
            </div>
          </div>

          <div class="person-row">
            <span class="material-symbols-rounded person-icon" aria-hidden="true">
              person
            </span>
            <div class="person-copy">
              <strong>{{ collaboratorsData.owner.displayName }}</strong>
              <small>{{ collaboratorsData.owner.email }}</small>
            </div>
            <span class="role-badge">OWNER</span>
          </div>
        </section>

        <section class="people-section" aria-labelledby="editors-title">
          <div class="section-title-row">
            <div>
              <p class="section-kicker">EDITORES</p>
              <h3 id="editors-title">Miembros con edición</h3>
            </div>
            <span class="count-badge">{{ collaboratorsData.editors.length }}</span>
          </div>

          @if (collaboratorsData.editors.length === 0) {
            <p class="empty-copy">Todavia no hay editores persistentes.</p>
          } @else {
            <div class="people-list">
              @for (editor of collaboratorsData.editors; track editor.userId) {
                <div class="person-row">
                  <span class="material-symbols-rounded person-icon" aria-hidden="true">
                    person
                  </span>
                  <div class="person-copy">
                    <strong>{{ editor.displayName }}</strong>
                    <small>{{ editor.email }}</small>
                  </div>
                  <span class="role-badge is-editor">EDITOR</span>
                </div>
              }
            </div>
          }
        </section>

        @if (isOwner()) {
          <section class="people-section management-section" aria-labelledby="invite-title">
            <div class="section-title-row">
              <div>
                <p class="section-kicker">INVITACIONES</p>
                <h3 id="invite-title">Invitar por correo</h3>
              </div>
            </div>

            <form
              class="invite-form"
              [formGroup]="inviteForm"
              (ngSubmit)="invite()"
            >
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>correo@example.com</mat-label>
                <input
                  matInput
                  type="email"
                  maxlength="160"
                  autocomplete="email"
                  formControlName="email"
                />
                @if (emailControl.hasError('required') && emailControl.touched) {
                  <mat-error>El correo es obligatorio.</mat-error>
                } @else if (emailControl.hasError('email') && emailControl.touched) {
                  <mat-error>Usa un correo valido.</mat-error>
                }
              </mat-form-field>

              <button
                mat-flat-button
                class="cf-button-primary"
                type="submit"
                [disabled]="sendingInvite()"
              >
                <span class="material-symbols-rounded" aria-hidden="true">
                  person_add
                </span>
                {{ sendingInvite() ? 'Invitando...' : 'Invitar' }}
              </button>
            </form>

            @if (actionSuccess()) {
              <p class="action-feedback is-success" role="status">
                <span class="material-symbols-rounded" aria-hidden="true">
                  check_circle
                </span>
                <span>{{ actionSuccess() }}</span>
              </p>
            }

            @if (actionError()) {
              <p class="action-feedback is-error" role="alert">
                <span class="material-symbols-rounded" aria-hidden="true">
                  error
                </span>
                <span>{{ actionError() }}</span>
              </p>
            }

            @if (collaboratorsData.pendingInvitations.length > 0) {
              <div class="pending-list">
                @for (
                  invitation of collaboratorsData.pendingInvitations;
                  track invitation.id
                ) {
                  <div class="pending-row">
                    <span class="material-symbols-rounded" aria-hidden="true">
                      schedule
                    </span>
                    <div>
                      <strong>{{ invitation.invitedEmail }}</strong>
                      <small>
                        Pendiente desde {{ invitation.createdAt | date:'short' }}
                      </small>
                    </div>
                    <button
                      mat-button
                      class="cf-button-secondary compact-action"
                      type="button"
                      [disabled]="cancellingInvitationId() === invitation.id"
                      (click)="cancelInvitation(invitation.id)"
                    >
                      {{ cancellingInvitationId() === invitation.id ? 'Cancelando...' : 'Cancelar' }}
                    </button>
                  </div>
                }
              </div>
            }
          </section>
        }

        <section class="people-section presence-section" aria-labelledby="presence-title">
          <div class="section-title-row">
            <div>
              <p class="section-kicker">PRESENCIA</p>
              <h3 id="presence-title">Conectados ahora</h3>
            </div>
            <span class="count-badge is-live">{{ presence.participantCount() }}</span>
          </div>

          @if (presence.participants().length === 0) {
            <p class="empty-copy">Esperando presencia STOMP...</p>
          } @else {
            <div class="people-list">
              @for (participant of presence.participants(); track participant.clientId) {
                <div class="person-row compact-person-row">
                  <span class="material-symbols-rounded person-icon" aria-hidden="true">
                    radio_button_checked
                  </span>
                  <div class="person-copy">
                    <strong>{{ presence.displayLabel(participant) }}</strong>
                    <small>
                      @if (participant.selectedElement) {
                        {{ participant.selectedElement.elementType === 'CLASS' ? 'Clase' : 'Relacion' }} seleccionada
                      } @else {
                        En el proyecto
                      }
                    </small>
                  </div>
                </div>
              }
            </div>
          }
        </section>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button
        mat-button
        class="cf-button-secondary"
        type="button"
        (click)="dialogRef.close()"
      >
        Cerrar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: min(680px, 82vw);
      max-height: min(74vh, 760px);
    }

    .dialog-title-row,
    .section-title-row,
    .person-row,
    .invite-form,
    .pending-row {
      display: flex;
      align-items: center;
    }

    .dialog-title-row {
      gap: 0.5rem;
    }

    .dialog-copy {
      margin: 0 0 1rem;
      color: var(--cf-muted);
      line-height: 1.55;
    }

    .dialog-feedback {
      min-height: 150px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-wrap: wrap;
      gap: 0.7rem;
      color: var(--cf-muted);
    }

    .dialog-feedback.is-error,
    .action-feedback.is-error {
      color: #b42318;
    }

    .action-feedback.is-success {
      color: #166534;
    }

    .people-section {
      display: grid;
      gap: 0.75rem;
      padding: 1rem 0;
      border-top: 1px solid var(--cf-border);
    }

    .people-section:first-of-type {
      border-top: 0;
    }

    .section-title-row {
      justify-content: space-between;
      gap: 1rem;
    }

    .section-kicker {
      margin: 0;
      color: var(--cf-muted);
      font-size: 0.68rem;
      font-weight: 800;
      letter-spacing: 0.12em;
    }

    h3 {
      margin: 0.15rem 0 0;
      font-size: 1rem;
    }

    .people-list,
    .pending-list {
      display: grid;
      gap: 0.55rem;
    }

    .person-row,
    .pending-row {
      min-width: 0;
      gap: 0.65rem;
      padding: 0.7rem 0.75rem;
      border: 1px solid var(--cf-border);
      border-radius: 0.7rem;
      background: #fff;
    }

    .person-icon,
    .pending-row > .material-symbols-rounded {
      color: #4f46e5;
    }

    .person-copy,
    .pending-row > div {
      min-width: 0;
      display: grid;
      gap: 0.1rem;
      flex: 1 1 auto;
    }

    .person-copy strong,
    .person-copy small,
    .pending-row strong,
    .pending-row small {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .person-copy small,
    .pending-row small,
    .empty-copy {
      color: var(--cf-muted);
      font-size: 0.78rem;
    }

    .empty-copy,
    .action-feedback {
      margin: 0;
      line-height: 1.55;
    }

    .action-feedback {
      display: flex;
      align-items: flex-start;
      gap: 0.45rem;
      padding: 0.65rem 0.75rem;
      border: 1px solid currentColor;
      border-radius: 0.65rem;
      background: rgb(255 255 255 / 72%);
      font-size: 0.82rem;
    }

    .action-feedback .material-symbols-rounded {
      flex: 0 0 auto;
      font-size: 1.05rem;
    }

    .role-badge,
    .count-badge {
      flex: 0 0 auto;
      padding: 0.2rem 0.5rem;
      border: 1px solid #c7d2fe;
      border-radius: 999px;
      background: #eef2ff;
      color: #3730a3;
      font-size: 0.66rem;
      font-weight: 800;
    }

    .role-badge.is-editor {
      border-color: var(--cf-border);
      background: #f9fafb;
      color: #475467;
    }

    .count-badge.is-live {
      border-color: #bbf7d0;
      background: #f0fdf4;
      color: #166534;
    }

    .invite-form {
      align-items: flex-start;
      gap: 0.65rem;
    }

    .invite-form mat-form-field {
      flex: 1 1 auto;
    }

    .invite-form button {
      flex: 0 0 auto;
      min-height: 56px;
    }

    .pending-row .compact-action {
      flex: 0 0 auto;
      min-height: 36px;
    }

    .compact-person-row {
      padding-block: 0.55rem;
    }

    @media (max-width: 620px) {
      mat-dialog-content {
        width: auto;
      }

      .invite-form,
      .pending-row {
        align-items: stretch;
        flex-direction: column;
      }

      .invite-form button,
      .pending-row .compact-action {
        width: 100%;
      }
    }
  `,
})
export class CollaboratorsDialogComponent {
  readonly data = inject<CollaboratorsDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<CollaboratorsDialogComponent>);
  readonly presence = inject(ProjectPresenceService);

  private readonly api = inject(ProjectInvitationApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly collaborators = signal<ProjectCollaborators | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actionSuccess = signal<string | null>(null);
  readonly sendingInvite = signal(false);
  readonly cancellingInvitationId = signal<string | null>(null);

  readonly isOwner = computed(
    () => this.data.accessRole === 'OWNER',
  );

  readonly emailControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.email,
      Validators.maxLength(160),
    ],
  });

  readonly inviteForm = new FormGroup({
    email: this.emailControl,
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.api
      .collaborators(this.data.projectId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (collaborators) => this.collaborators.set(collaborators),
        error: () => {
          this.errorMessage.set(
            'No pudimos cargar los colaboradores del proyecto.',
          );
        },
      });
  }

  invite(): void {
    this.emailControl.markAsTouched();

    if (!this.isOwner() || this.emailControl.invalid || this.sendingInvite()) {
      return;
    }

    const email = this.emailControl.value.trim();

    if (!email) {
      this.emailControl.setErrors({ required: true });
      return;
    }

    this.sendingInvite.set(true);
    this.actionError.set(null);
    this.actionSuccess.set(null);

    this.api
      .invite(this.data.projectId, email)
      .pipe(
        finalize(() => this.sendingInvite.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (invitation) => {
          this.emailControl.reset('');
          this.actionSuccess.set(
            `Invitacion creada para ${invitation.invitedEmail}. La otra cuenta la vera en su bandeja en unos segundos.`,
          );
          this.collaborators.update((current) =>
            current
              ? {
                  ...current,
                  pendingInvitations: [
                    ...current.pendingInvitations,
                    invitation,
                  ],
                }
              : current,
          );
        },
        error: (error: HttpErrorResponse) => {
          this.actionError.set(
            this.invitationFailureMessage(error),
          );
        },
      });
  }

  cancelInvitation(invitationId: string): void {
    if (!this.isOwner() || this.cancellingInvitationId()) {
      return;
    }

    this.cancellingInvitationId.set(invitationId);
    this.actionError.set(null);
    this.actionSuccess.set(null);

    this.api
      .cancel(this.data.projectId, invitationId)
      .pipe(
        finalize(() => this.cancellingInvitationId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.actionSuccess.set('Invitacion pendiente cancelada.');
          this.collaborators.update((current) =>
            current
              ? {
                  ...current,
                  pendingInvitations: current.pendingInvitations.filter(
                    (invitation) => invitation.id !== invitationId,
                  ),
                }
              : current,
          );
        },
        error: (error: HttpErrorResponse) => {
          this.actionError.set(
            this.invitationFailureMessage(
              error,
              'No pudimos cancelar la invitacion pendiente.',
            ),
          );
        },
      });
  }

  private invitationFailureMessage(
    error: HttpErrorResponse,
    fallback = 'No pudimos crear la invitacion.',
  ): string {
    const backendMessage = error.error?.message;

    if (
      typeof backendMessage === 'string'
      && backendMessage.trim()
    ) {
      return backendMessage.trim();
    }

    if (error.status === 0) {
      return 'No pudimos contactar con ClassForge Backend. La invitacion no fue confirmada.';
    }

    return fallback;
  }
}
