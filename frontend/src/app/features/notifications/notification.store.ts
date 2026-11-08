import { DestroyRef, Injectable, effect, inject, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject, forkJoin, takeUntil } from 'rxjs';
import { AuthService } from '../../auth/data/auth.service';
import { NotificationApiService } from './notification-api.service';
import { mergeNotifications, type NotificationEvent, type ProjectNotification } from './notification-model';

@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly auth = inject(AuthService);
  private readonly api = inject(NotificationApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly changed = new Subject<void>();
  private client: Client | null = null;
  private generation = 0;
  private epoch = 0;
  private headRequest = 0;
  private userId = '';
  private nextPage = 1;
  readonly items = signal<ProjectNotification[]>([]);
  readonly unread = signal(0);
  readonly loading = signal(false);
  readonly hasMore = signal(false);
  readonly error = signal<string | null>(null);
  readonly updates = new Subject<NotificationEvent>();

  constructor() {
    effect(() => {
      const token = this.auth.token(), user = this.auth.user()?.id;
      this.changed.next(); this.generation++; this.epoch++; this.headRequest++;
      void this.client?.deactivate(); this.client = null;
      this.items.set([]); this.unread.set(0); this.error.set(null); this.loading.set(false); this.hasMore.set(false);
      this.userId = user ?? ''; this.nextPage = 1;
      if (!token || !user) return;
      const generation = this.generation;
      this.refresh();
      const client = new Client({
        brokerURL: `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`,
        connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 3000,
        heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
      });
      client.onConnect = () => {
        if (generation !== this.generation || this.client !== client) return;
        client.subscribe('/user/queue/notifications', frame => {
          if (generation !== this.generation) return;
          try {
            const event = JSON.parse(frame.body) as NotificationEvent;
            if (event.recipientUserId !== this.userId || !['NOTIFICATION_CREATED', 'NOTIFICATIONS_CHANGED'].includes(event.type)) return;
            if (event.notification) this.items.set(mergeNotifications(this.items(), [event.notification], this.userId));
            this.updates.next(event); this.refresh(event.type === 'NOTIFICATIONS_CHANGED');
          } catch { this.refresh(); }
        });
        this.refresh();
      };
      client.onStompError = () => { if (generation === this.generation) this.refresh(); };
      client.onWebSocketError = () => { /* Persisted notifications remain available through HTTP reconciliation. */ };
      this.client = client; client.activate();
    });
    const timer = setInterval(() => { if (this.userId && document.visibilityState !== 'hidden') this.refresh(); }, 10000);
    this.destroy.onDestroy(() => { clearInterval(timer); this.changed.next(); this.changed.complete(); this.updates.complete(); void this.client?.deactivate(); });
  }

  refresh(reset = false): void {
    if (!this.userId) return;
    if (reset) { this.epoch++; this.items.set([]); this.nextPage = 1; }
    const request = ++this.headRequest, generation = this.generation;
    this.loading.set(true);
    forkJoin({ page: this.api.list(), count: this.api.count() }).pipe(takeUntil(this.changed)).subscribe({
      next: result => {
        if (generation !== this.generation || request !== this.headRequest) return;
        this.items.set(mergeNotifications(this.items(), result.page.items, this.userId));
        this.unread.set(result.count.count); this.hasMore.set(this.items().length < result.page.totalElements);
        this.loading.set(false); this.error.set(null);
      },
      error: () => { if (generation !== this.generation || request !== this.headRequest) return; this.loading.set(false); this.error.set('No pudimos actualizar tus notificaciones.'); },
    });
  }
  more(): void {
    if (this.loading() || !this.hasMore()) return;
    const page = this.nextPage, generation = this.generation, epoch = this.epoch; this.loading.set(true);
    this.api.list(page).pipe(takeUntil(this.changed)).subscribe({
      next: result => {
        if (generation !== this.generation || epoch !== this.epoch) return;
        this.items.set(mergeNotifications(this.items(), result.items, this.userId));
        this.nextPage = page + 1; this.hasMore.set(result.page + 1 < result.totalPages); this.loading.set(false);
      },
      error: () => { if (generation !== this.generation || epoch !== this.epoch) return; this.loading.set(false); this.error.set('No pudimos cargar más notificaciones.'); },
    });
  }
  read(notification: ProjectNotification): void {
    if (notification.read || notification.recipientUserId !== this.userId) return;
    const generation = this.generation;
    this.api.read(notification.id).pipe(takeUntil(this.changed)).subscribe({
      next: updated => { if (generation !== this.generation) return; this.items.set(mergeNotifications(this.items(), [updated], this.userId)); this.refresh(); },
      error: () => { if (generation === this.generation) this.error.set('No pudimos marcar la notificación como leída.'); },
    });
  }
  readAll(): void {
    const generation = this.generation;
    this.api.readAll().pipe(takeUntil(this.changed)).subscribe({
      next: () => { if (generation === this.generation) this.refresh(true); },
      error: () => { if (generation === this.generation) this.error.set('No pudimos marcar las notificaciones como leídas.'); },
    });
  }
}
