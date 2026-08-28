import { Component, inject, signal } from '@angular/core';
import { HealthService } from './core/api/health.service';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly healthService = inject(HealthService);

  protected readonly backendStatus = signal('Comprobando...');

  constructor() {
    this.healthService.getHealth().subscribe({
      next: (response) => {
        this.backendStatus.set(`${response.application}: ${response.status}`);
      },
      error: () => {
        this.backendStatus.set('Backend no disponible');
      },
    });
  }
}
