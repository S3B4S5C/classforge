import {
  Injectable,
} from '@angular/core';
import {
  Client,
  IMessage,
} from '@stomp/stompjs';
import {
  Subject,
} from 'rxjs';

import {
  CollaborationConnectionEvent,
  ProjectOperation,
  ProjectOperationApplied,
  ProjectOperationRejected,
} from './collaboration-protocol';

@Injectable({
  providedIn: 'root',
})
export class ProjectCollaborationService {
  private client: Client | null = null;
  private projectId: string | null = null;
  private intentionallyDisconnected = false;

  readonly connectionEvents$ =
    new Subject<CollaborationConnectionEvent>();

  readonly appliedOperations$ =
    new Subject<ProjectOperationApplied>();

  readonly rejectedOperations$ =
    new Subject<ProjectOperationRejected>();

  connect(
    projectId: string,
    accessToken: string,
  ): void {
    if (
      this.client?.active
      && this.projectId === projectId
    ) {
      return;
    }

    this.disconnect();

    this.projectId = projectId;
    this.intentionallyDisconnected = false;

    const client =
      new Client({
        brokerURL:
          this.webSocketUrl(),
        connectHeaders: {
          Authorization:
            `Bearer ${accessToken}`,
        },
        reconnectDelay: 3000,
        connectionTimeout: 8000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: () => undefined,
      });

    client.onConnect = () => {
      if (
        !this.projectId
        || this.projectId !== projectId
      ) {
        return;
      }

      client.subscribe(
        `/topic/projects/${projectId}/operations`,
        (message) =>
          this.handleApplied(message),
      );

      client.subscribe(
        `/user/queue/projects/${projectId}/operations`,
        (message) =>
          this.handleRejected(message),
      );

      this.connectionEvents$.next({
        type: 'CONNECTED',
      });
    };

    client.onStompError = (frame) => {
      this.connectionEvents$.next({
        type: 'ERROR',
        message:
          frame.headers['message']
          ?? 'El servidor STOMP rechazo la conexion.',
      });
    };

    client.onWebSocketError = () => {
      this.connectionEvents$.next({
        type: 'ERROR',
        message:
          'No pudimos establecer el canal WebSocket.',
      });
    };

    client.onWebSocketClose = () => {
      if (this.intentionallyDisconnected) {
        return;
      }

      this.connectionEvents$.next({
        type: 'DISCONNECTED',
      });
    };

    this.client = client;

    this.connectionEvents$.next({
      type: 'DISCONNECTED',
    });

    client.activate();
  }

  disconnect(): void {
    const current =
      this.client;

    if (!current) {
      return;
    }

    this.intentionallyDisconnected = true;
    this.client = null;
    this.projectId = null;

    void current.deactivate();
  }

  isConnected(): boolean {
    return this.client?.connected
      ?? false;
  }

  publish(
    operation: ProjectOperation,
  ): boolean {
    const client =
      this.client;

    if (
      !client
      || !client.connected
    ) {
      return false;
    }

    try {
      client.publish({
        destination:
          `/app/projects/${operation.projectId}/operations`,
        body: JSON.stringify(operation),
      });

      return true;
    } catch {
      return false;
    }
  }

  private handleApplied(
    message: IMessage,
  ): void {
    const parsed =
      this.parseJson<ProjectOperationApplied>(
        message.body,
      );

    if (
      !parsed
      || parsed.type
        !== 'OPERATION_APPLIED'
    ) {
      this.connectionEvents$.next({
        type: 'ERROR',
        message:
          'El servidor envio una operacion colaborativa invalida.',
      });

      return;
    }

    this.appliedOperations$.next(
      parsed,
    );
  }

  private handleRejected(
    message: IMessage,
  ): void {
    const parsed =
      this.parseJson<ProjectOperationRejected>(
        message.body,
      );

    if (
      !parsed
      || parsed.type
        !== 'OPERATION_REJECTED'
    ) {
      this.connectionEvents$.next({
        type: 'ERROR',
        message:
          'El servidor envio un rechazo colaborativo invalido.',
      });

      return;
    }

    this.rejectedOperations$.next(
      parsed,
    );
  }

  private parseJson<T>(
    value: string,
  ): T | null {
    try {
      return JSON.parse(value) as T;
    } catch {
      return null;
    }
  }

  private webSocketUrl(): string {
    const protocol =
      window.location.protocol === 'https:'
        ? 'wss:'
        : 'ws:';

    return `${protocol}//${window.location.host}/ws`;
  }
}