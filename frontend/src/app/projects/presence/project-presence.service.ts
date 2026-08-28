import {
  DestroyRef,
  Injectable,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  takeUntilDestroyed,
} from '@angular/core/rxjs-interop';
import {
  Subject,
  auditTime,
} from 'rxjs';

import {
  ProjectCollaborationService,
} from '../collaboration/project-collaboration.service';
import {
  UmlCanvasCursorEvent,
  UmlCanvasSelection,
} from '../diagram/uml-canvas/uml-canvas.component';
import {
  PresenceEventRequest,
  PresenceParticipant,
  PresenceSelection,
  ProjectPresenceEvent,
  ProjectPresenceSnapshot,
} from './presence-protocol';

@Injectable({
  providedIn: 'root',
})
export class ProjectPresenceService {
  private readonly collaboration =
    inject(ProjectCollaborationService);

  private readonly destroyRef =
    inject(DestroyRef);

  private readonly projectIdState =
    signal<string | null>(null);

  private readonly participantsState =
    signal<PresenceParticipant[]>([]);

  private readonly cursorInput =
    new Subject<UmlCanvasCursorEvent>();

  readonly participants =
    this.participantsState.asReadonly();

  readonly clientId =
    this.collaboration.clientId;

  readonly remoteParticipants =
    computed(
      () =>
        this.participantsState()
          .filter(
            (participant) =>
              participant.clientId
                !== this.clientId,
          ),
    );

  readonly participantCount =
    computed(
      () =>
        this.participantsState()
          .length,
    );

  constructor() {
    this.collaboration
      .connectionEvents$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (event) => {
          if (event.type === 'CONNECTED') {
            this.join();
            return;
          }

          if (
            event.type === 'DISCONNECTED'
            || event.type === 'ERROR'
          ) {
            this.participantsState.set(
              [],
            );
          }
        },
      );

    this.collaboration
      .presenceSnapshots$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (snapshot) =>
          this.applySnapshot(
            snapshot,
          ),
      );

    this.collaboration
      .presenceEvents$
      .pipe(
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (event) =>
          this.applyEvent(
            event,
          ),
      );

    this.cursorInput
      .pipe(
        auditTime(67),
        takeUntilDestroyed(
          this.destroyRef,
        ),
      )
      .subscribe(
        (cursor) =>
          this.publish({
            type: 'USER_MOVED_CURSOR',
            selectedElement: null,
            cursor,
          }),
      );
  }

  attachProject(
    projectId: string,
  ): void {
    if (
      this.projectIdState()
        === projectId
    ) {
      return;
    }

    this.detachProject();
    this.projectIdState.set(
      projectId,
    );

    if (
      this.collaboration
        .isConnected()
    ) {
      this.join();
    }
  }

  detachProject(): void {
    const projectId =
      this.projectIdState();

    if (
      projectId
      && this.collaboration
        .isConnected()
    ) {
      this.publish({
        type: 'USER_LEFT',
        selectedElement: null,
        cursor: null,
      });
    }

    this.projectIdState.set(
      null,
    );

    this.participantsState.set(
      [],
    );
  }

  selectElement(
    selection: UmlCanvasSelection,
  ): void {
    const selectedElement:
      PresenceSelection | null =
      selection
        ? {
            elementId:
              selection.id,
            elementType:
              selection.kind === 'class'
                ? 'CLASS'
                : 'RELATIONSHIP',
          }
        : null;

    this.publish({
      type: 'USER_SELECTED_ELEMENT',
      selectedElement,
      cursor: null,
    });
  }

  moveCursor(
    cursor: UmlCanvasCursorEvent,
  ): void {
    this.cursorInput.next(
      cursor,
    );
  }

  participantsOnElement(
    elementId: string,
  ): PresenceParticipant[] {
    return this.remoteParticipants()
      .filter(
        (participant) =>
          participant.selectedElement
            ?.elementId
          === elementId,
      );
  }

  displayLabel(
    participant: PresenceParticipant,
  ): string {
    if (
      participant.clientId
        === this.clientId
    ) {
      return `${participant.actor.displayName} · Tú`;
    }

    return participant.actor.displayName;
  }

  private join(): void {
    this.publish({
      type: 'USER_JOINED',
      selectedElement: null,
      cursor: null,
    });
  }

  private publish(
    input: Pick<
      PresenceEventRequest,
      'type'
      | 'selectedElement'
      | 'cursor'
    >,
  ): void {
    const projectId =
      this.projectIdState();

    if (
      !projectId
      || !this.collaboration
        .isConnected()
    ) {
      return;
    }

    this.collaboration
      .publishPresence(
        projectId,
        {
          eventId:
            crypto.randomUUID(),
          clientId:
            this.clientId,
          ...input,
        },
      );
  }

  private applySnapshot(
    snapshot: ProjectPresenceSnapshot,
  ): void {
    if (
      snapshot.projectId
        !== this.projectIdState()
    ) {
      return;
    }

    this.participantsState.set(
      this.sorted(
        snapshot.participants,
      ),
    );
  }

  private applyEvent(
    event: ProjectPresenceEvent,
  ): void {
    if (
      event.projectId
        !== this.projectIdState()
    ) {
      return;
    }

    const current =
      this.participantsState();

    if (
      event.type === 'USER_LEFT'
    ) {
      this.participantsState.set(
        current.filter(
          (participant) =>
            participant.clientId
              !== event.participant
                .clientId,
        ),
      );

      return;
    }

    const withoutParticipant =
      current.filter(
        (participant) =>
          participant.clientId
            !== event.participant
              .clientId,
      );

    this.participantsState.set(
      this.sorted([
        ...withoutParticipant,
        event.participant,
      ]),
    );
  }

  private sorted(
    participants:
      PresenceParticipant[],
  ): PresenceParticipant[] {
    return [...participants]
      .sort(
        (left, right) =>
          left.actor.displayName.localeCompare(
            right.actor.displayName,
            'es',
            {
              sensitivity: 'base',
            },
          )
          || left.clientId.localeCompare(
            right.clientId,
          ),
      );
  }
}