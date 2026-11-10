import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { PushDeviceService } from '../../features/notifications/push-device.service';

import { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../model/auth';

const TOKEN_KEY = 'classforge.accessToken';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly push = inject(PushDeviceService);
  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly user = signal<AuthUser | null>(null);
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  constructor() {
    if (this.tokenState()) {
      const expectedToken = this.tokenState();
      this.loadCurrentUser().subscribe({
        error: () => { if(this.tokenState() === expectedToken && localStorage.getItem(TOKEN_KEY) === expectedToken) this.clearSession(); },
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
    const expectedToken = this.tokenState();
    return this.http
      .get<AuthUser>('/api/auth/me')
      .pipe(tap((user) => {
        if (this.tokenState() !== expectedToken || localStorage.getItem(TOKEN_KEY) !== expectedToken) return;
        this.user.set(user); this.push.setUserId(user.id);
      }));
  }

  async logout(): Promise<void> {
    const expectedToken = this.tokenState();
    await this.push.disable();
    if (localStorage.getItem(TOKEN_KEY) === expectedToken) this.clearSession();
  }

  private establishSession(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    this.tokenState.set(response.accessToken);
    this.user.set(response.user);
    this.push.setUserId(response.user.id);
  }

  private clearSession(): void {
    this.push.setUserId(null);
    localStorage.removeItem(TOKEN_KEY);
    this.tokenState.set(null);
    this.user.set(null);
  }
}
