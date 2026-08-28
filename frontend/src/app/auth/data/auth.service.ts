import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../model/auth';

const TOKEN_KEY = 'classforge.accessToken';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly user = signal<AuthUser | null>(null);
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  constructor() {
    if (this.tokenState()) {
      this.loadCurrentUser().subscribe({
        error: () => this.clearSession(),
      });
    }
  }

  token(): string | null {
    return this.tokenState();
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', request)
      .pipe(tap((response) => this.establishSession(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', request)
      .pipe(tap((response) => this.establishSession(response)));
  }

  loadCurrentUser(): Observable<AuthUser> {
    return this.http
      .get<AuthUser>('/api/auth/me')
      .pipe(tap((user) => this.user.set(user)));
  }

  logout(): void {
    this.clearSession();
  }

  private establishSession(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    this.tokenState.set(response.accessToken);
    this.user.set(response.user);
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenState.set(null);
    this.user.set(null);
  }
}