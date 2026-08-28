import { Component, inject, signal } from '@angular/core';
import { HealthService } from './core/api/health.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly healthService = inject(HealthService);

  readonly backendStatus = signal('Comprobando backend...');

  constructor() {
    console.log('[ClassForge] App iniciada');
    console.log('[ClassForge] Consultando /api/health');

    this.healthService.getHealth().subscribe({
      next: response => {
        console.log('[ClassForge] Backend respondió:', response);
        this.backendStatus.set(
          `${response.application}: ${response.status}`
        );
      },
      error: error => {
        console.error('[ClassForge] Error consultando backend:', error);
        this.backendStatus.set('Backend no disponible');
      }
    });
  }
}