import { HttpInterceptorFn } from '@angular/common/http';

const TOKEN_KEY = 'classforge.accessToken';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = localStorage.getItem(TOKEN_KEY);

  if (!token || request.url.includes('/api/auth/login') || request.url.includes('/api/auth/register')) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};