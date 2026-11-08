import { DatePipe } from '@angular/common';
import { Component, HostListener, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { NotificationStore } from './notification.store';
import type { ProjectNotification } from './notification-model';

@Component({
  selector: 'app-notification-bell', imports: [DatePipe, MatButtonModule, RouterLink],
  templateUrl: './notification-bell.component.html', styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent {
  readonly store = inject(NotificationStore);
  readonly expanded = signal(false);
  toggle(): void { this.expanded.update(value => !value); if (this.expanded()) this.store.refresh(); }
  open(notification: ProjectNotification): void { this.store.read(notification); this.expanded.set(false); }
  @HostListener('document:keydown.escape') escape(): void { this.expanded.set(false); }
}
