package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class AngularAuthFilesRenderer {

    String authService() {
        return """
                import { HttpClient } from '@angular/common/http';
                import { computed, inject, Injectable, signal } from '@angular/core';
                import { Observable, tap } from 'rxjs';

                export interface LoginResponse {
                  accessToken: string;
                  tokenType: string;
                  expiresInSeconds: number;
                }

                @Injectable({ providedIn: 'root' })
                export class AuthService {
                  private readonly http = inject(HttpClient);
                  private readonly token = signal<string | null>(localStorage.getItem('jwt'));
                  readonly authenticated = computed(() => !!this.token());

                  login(username: string, password: string): Observable<LoginResponse> {
                    return this.http.post<LoginResponse>('/api/auth/login', { username, password })
                      .pipe(tap((response) => this.store(response.accessToken)));
                  }

                  bootstrap(request: Record<string, unknown>): Observable<LoginResponse> {
                    return this.http.post<LoginResponse>('/api/auth/bootstrap', request)
                      .pipe(tap((response) => this.store(response.accessToken)));
                  }

                  accessToken(): string | null { return this.token(); }

                  logout(): void {
                    localStorage.removeItem('jwt');
                    this.token.set(null);
                  }

                  private store(token: string): void {
                    localStorage.setItem('jwt', token);
                    this.token.set(token);
                  }
                }
                """;
    }

    String authInterceptor() {
        return """
                import { HttpInterceptorFn } from '@angular/common/http';
                import { inject } from '@angular/core';
                import { Router } from '@angular/router';
                import { catchError, throwError } from 'rxjs';
                import { AuthService } from './auth.service';

                export const authInterceptor: HttpInterceptorFn = (request, next) => {
                  const auth = inject(AuthService);
                  const router = inject(Router);
                  const token = auth.accessToken();
                  const publicAuth = request.url.endsWith('/api/auth/login') || request.url.endsWith('/api/auth/bootstrap');
                  const authorized = token && !publicAuth
                    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
                    : request;

                  return next(authorized).pipe(
                    catchError((error) => {
                      if (error?.status === 401 && !publicAuth) {
                        auth.logout();
                        router.navigateByUrl('/login');
                      }
                      return throwError(() => error);
                    }),
                  );
                };
                """;
    }

    String authGuard() {
        return """
                import { inject } from '@angular/core';
                import { CanActivateFn, Router } from '@angular/router';
                import { AuthService } from './auth.service';

                export const authGuard: CanActivateFn = () => {
                  const auth = inject(AuthService);
                  const router = inject(Router);
                  return auth.authenticated() ? true : router.createUrlTree(['/login']);
                };
                """;
    }

    String loginComponent() {
        return """
                import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
                import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
                import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
                import { Router, RouterLink } from '@angular/router';
                import { AuthService } from '../core/auth/auth.service';

                @Component({
                  standalone: true,
                  imports: [ReactiveFormsModule, RouterLink],
                  template: `
                    <main class="auth-page">
                      <section class="card auth-card">
                        <h1>Iniciar sesion</h1>
                        <p class="muted">Usa una cuenta del sistema generado.</p>
                        <form [formGroup]="form" (ngSubmit)="submit()">
                          <label class="field">
                            <span>Usuario</span>
                            <input formControlName="username" autocomplete="username" />
                          </label>
                          <label class="field">
                            <span>Contrasena</span>
                            <input type="password" formControlName="password" autocomplete="current-password" />
                          </label>
                          @if (error) { <div class="error">{{ error }}</div> }
                          <div class="form-actions">
                            <button class="btn btn-primary" type="submit" [disabled]="form.invalid || loading">Entrar</button>
                            <a class="btn" routerLink="/bootstrap">Primera cuenta</a>
                          </div>
                        </form>
                      </section>
                    </main>
                  `,
                })
                export class LoginComponent {
                  private readonly changes = inject(ChangeDetectorRef);
                  private readonly destroyRef = inject(DestroyRef);
                  private readonly auth = inject(AuthService);
                  private readonly router = inject(Router);
                  loading = false;
                  error = '';
                  readonly form = new FormGroup({
                    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
                    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
                  });

                  submit(): void {
                    if (this.loading || this.form.invalid) return;
                    this.loading = true;
                    this.error = '';
                    this.auth.login(this.form.controls.username.value, this.form.controls.password.value).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                      next: () => this.router.navigateByUrl('/dashboard'),
                      error: (failure) => { this.loading = false; this.error = failure?.error?.message ?? 'No se pudo iniciar sesion.'; this.changes.markForCheck(); },
                    });
                  }
                }
                """;
    }
}
