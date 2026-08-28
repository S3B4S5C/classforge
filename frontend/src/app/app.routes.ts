import { Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./landing/landing.page').then((module) => module.LandingPage),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./auth/pages/login/login.page').then((module) => module.LoginPage),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./auth/pages/register/register.page').then((module) => module.RegisterPage),
  },
  {
    path: 'projects',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./projects/pages/project-list/project-list.page').then(
        (module) => module.ProjectListPage,
      ),
  },
  {
    path: 'projects/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./projects/pages/project-workspace/project-workspace.page').then(
        (module) => module.ProjectWorkspacePage,
      ),
  },
  {
    path: '**',
    redirectTo: '',
  },
];