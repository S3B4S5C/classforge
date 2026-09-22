package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class AngularProjectFilesRenderer {

    String packageJson(String artifactName) {
        return """
                {
                  "name": "%s-web",
                  "version": "0.1.0",
                  "private": true,
                  "scripts": {
                    "start": "ng serve",
                    "build": "ng build"
                  },
                  "engines": {
                    "node": ">=22.22.3"
                  },
                  "dependencies": {
                    "@angular/common": "^22.1.0",
                    "@angular/core": "^22.1.0",
                    "@angular/forms": "^22.1.0",
                    "@angular/platform-browser": "^22.1.0",
                    "@angular/router": "^22.1.0",
                    "rxjs": "~7.8.2",
                    "tslib": "^2.8.1",
                    "zone.js": "~0.15.1"
                  },
                  "devDependencies": {
                    "@angular/build": "^22.1.0",
                    "@angular/cli": "^22.1.0",
                    "@angular/compiler": "^22.1.0",
                    "@angular/compiler-cli": "^22.1.0",
                    "typescript": "~6.0.2"
                  }
                }
                """.formatted(artifactName);
    }

    String angularJson(String artifactName) {
        return """
                {
                  "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
                  "version": 1,
                  "projects": {
                    "%1$s-web": {
                      "projectType": "application",
                      "root": "",
                      "sourceRoot": "src",
                      "prefix": "app",
                      "architect": {
                        "build": {
                          "builder": "@angular/build:application",
                          "options": {
                            "browser": "src/main.ts",
                            "polyfills": ["zone.js"],
                            "tsConfig": "tsconfig.app.json",
                            "assets": [],
                            "styles": ["src/styles.css"]
                          },
                          "configurations": {
                            "production": {
                              "budgets": [
                                {"type": "initial", "maximumWarning": "750kB", "maximumError": "1.25MB"},
                                {"type": "anyComponentStyle", "maximumWarning": "18kB", "maximumError": "24kB"}
                              ],
                              "outputHashing": "all"
                            },
                            "development": {
                              "optimization": false,
                              "extractLicenses": false,
                              "sourceMap": true
                            }
                          },
                          "defaultConfiguration": "production"
                        },
                        "serve": {
                          "builder": "@angular/build:dev-server",
                          "options": {"proxyConfig": "proxy.conf.json"},
                          "configurations": {
                            "development": {"buildTarget": "%1$s-web:build:development"},
                            "production": {"buildTarget": "%1$s-web:build:production"}
                          },
                          "defaultConfiguration": "development"
                        }
                      }
                    }
                  },
                  "cli": {"analytics": false}
                }
                """.formatted(artifactName);
    }

    String tsconfig() {
        return """
                {
                  "compileOnSave": false,
                  "compilerOptions": {
                    "outDir": "./dist/out-tsc",
                    "forceConsistentCasingInFileNames": true,
                    "strict": true,
                    "noImplicitOverride": true,
                    "noPropertyAccessFromIndexSignature": true,
                    "noImplicitReturns": true,
                    "noFallthroughCasesInSwitch": true,
                    "sourceMap": true,
                    "declaration": false,
                    "experimentalDecorators": true,
                    "moduleResolution": "bundler",
                    "importHelpers": true,
                    "target": "ES2022",
                    "module": "ES2022",
                    "useDefineForClassFields": false,
                    "lib": ["ES2022", "dom"]
                  },
                  "angularCompilerOptions": {
                    "enableI18nLegacyMessageIdFormat": false,
                    "strictInjectionParameters": true,
                    "strictInputAccessModifiers": true,
                    "strictTemplates": true
                  }
                }
                """;
    }

    String tsconfigApp() {
        return """
                {
                  "extends": "./tsconfig.json",
                  "compilerOptions": {
                    "outDir": "./out-tsc/app",
                    "types": []
                  },
                  "files": ["src/main.ts"],
                  "include": ["src/**/*.d.ts"]
                }
                """;
    }

    String proxy() {
        return """
                {
                  "/api": {
                    "target": "http://localhost:8080",
                    "secure": false,
                    "changeOrigin": true
                  }
                }
                """;
    }

    String indexHtml(String artifactName) {
        return """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <title>%s</title>
                  <base href="/">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                </head>
                <body>
                  <app-root></app-root>
                </body>
                </html>
                """.formatted(escapeHtml(title(artifactName)));
    }

    String mainTs(boolean auth) {
        String interceptorImport = auth
                ? "import { authInterceptor } from './app/core/auth/auth.interceptor';\n"
                : "";
        String httpProvider = auth
                ? "provideHttpClient(withInterceptors([authInterceptor]))"
                : "provideHttpClient()";
        String httpImport = auth
                ? "import { provideHttpClient, withInterceptors } from '@angular/common/http';"
                : "import { provideHttpClient } from '@angular/common/http';";
        return """
                import { provideZoneChangeDetection } from '@angular/core';
                import { bootstrapApplication } from '@angular/platform-browser';
                %s
                import { provideRouter } from '@angular/router';
                import { AppComponent } from './app/app.component';
                import { routes } from './app/app.routes';
                %s

                bootstrapApplication(AppComponent, {
                  providers: [
                    provideZoneChangeDetection(),
                    provideRouter(routes),
                    %s,
                  ],
                }).catch((error) => console.error(error));
                """.formatted(httpImport, interceptorImport, httpProvider);
    }

    String styles(String color) {
        String contrast = contrast(color);
        return """
                :root {
                  --app-primary: __COLOR__;
                  --app-on-primary: __CONTRAST__;
                  --app-bg: #f5f7fb;
                  --app-surface: #ffffff;
                  --app-border: #dbe2ea;
                  --app-text: #172033;
                  --app-muted: #667085;
                  --app-danger: #b42318;
                  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: var(--app-text);
                  background: var(--app-bg);
                }
                * { box-sizing: border-box; }
                body { margin: 0; background: var(--app-bg); }
                a { color: inherit; }
                button, input, select { font: inherit; }
                button { cursor: pointer; }
                .app-shell { min-height: 100vh; }
                .app-header {
                  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
                  padding: .85rem 1.25rem; background: var(--app-primary); color: var(--app-on-primary);
                  position: sticky; top: 0; z-index: 20;
                }
                .app-brand { font-weight: 800; letter-spacing: .01em; text-decoration: none; }
                .app-nav { display: flex; flex-wrap: wrap; gap: .35rem; align-items: center; }
                .app-nav a, .app-nav button {
                  border: 0; background: transparent; color: inherit; padding: .45rem .65rem;
                  border-radius: .55rem; text-decoration: none;
                }
                .app-nav a:hover, .app-nav a.active, .app-nav button:hover { background: rgba(255,255,255,.18); }
                .page { max-width: 1280px; margin: 0 auto; padding: 1.25rem; }
                .page-header { display: flex; gap: 1rem; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
                .page-header h1 { margin: 0; font-size: 1.55rem; }
                .card {
                  background: var(--app-surface); border: 1px solid var(--app-border);
                  border-radius: .85rem; padding: 1rem; box-shadow: 0 1px 2px rgba(16,24,40,.04);
                }
                fieldset.form-grid { border: 0; padding: 0; margin: 0; min-width: 0; }
                button:disabled, input:disabled, select:disabled { cursor: not-allowed; opacity: .65; }
                :focus-visible { outline: 2px solid var(--app-primary); outline-offset: 2px; }
                .ng-invalid.ng-touched { border-color: #b91c1c; }
                select[multiple] { min-height: 8rem; }
                .dashboard-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(210px,1fr)); gap: 1rem; }
                .metric { font-size: 2rem; font-weight: 800; color: var(--app-primary); }
                .muted { color: var(--app-muted); }
                .toolbar { display: flex; flex-wrap: wrap; gap: .65rem; margin-bottom: 1rem; }
                .field { display: grid; gap: .3rem; }
                .field label { font-size: .82rem; font-weight: 650; color: var(--app-muted); }
                input, select, textarea {
                  width: 100%; border: 1px solid var(--app-border); border-radius: .55rem;
                  padding: .58rem .68rem; background: white; color: var(--app-text);
                }
                input:focus, select:focus, textarea:focus { outline: 2px solid color-mix(in srgb, var(--app-primary) 24%, transparent); border-color: var(--app-primary); }
                .form-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(240px,1fr)); gap: .9rem; }
                .form-actions { display: flex; gap: .6rem; margin-top: 1rem; }
                .btn {
                  display: inline-flex; align-items: center; justify-content: center; gap: .35rem;
                  border-radius: .55rem; border: 1px solid var(--app-border); padding: .55rem .8rem;
                  background: white; color: var(--app-text); text-decoration: none;
                }
                .btn-primary { background: var(--app-primary); color: var(--app-on-primary); border-color: var(--app-primary); }
                .btn-danger { color: var(--app-danger); }
                .table-wrap { overflow: auto; background: white; border: 1px solid var(--app-border); border-radius: .8rem; }
                table { border-collapse: collapse; width: 100%; min-width: 720px; }
                th, td { padding: .68rem .75rem; text-align: left; border-bottom: 1px solid var(--app-border); vertical-align: top; }
                th { background: #f8fafc; font-size: .8rem; color: var(--app-muted); }
                tr:last-child td { border-bottom: 0; }
                .actions { display: flex; gap: .4rem; flex-wrap: wrap; }
                .pager { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-top: .8rem; }
                .detail-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(220px,1fr)); gap: .75rem; }
                .detail-item { padding: .75rem; border: 1px solid var(--app-border); border-radius: .65rem; }
                .detail-item strong { display: block; font-size: .78rem; color: var(--app-muted); margin-bottom: .25rem; }
                .auth-page { min-height: 100vh; display: grid; place-items: center; padding: 1.25rem; background: linear-gradient(135deg, color-mix(in srgb, var(--app-primary) 10%, white), var(--app-bg)); }
                .auth-card { width: min(460px,100%); }
                .error { color: var(--app-danger); margin-top: .6rem; }
                @media (max-width: 720px) {
                  .app-header { align-items: flex-start; flex-direction: column; }
                  .page-header { align-items: flex-start; flex-direction: column; }
                }
                """.replace("__COLOR__", color).replace("__CONTRAST__", contrast);
    }

    String appComponent(DomainManifestPlan manifest) {
        String nav = manifest.entities().stream()
                .map(entity -> "          <a routerLink=\"/entities/%s\" routerLinkActive=\"active\">%s</a>"
                        .formatted(entity.tableName(), escapeHtml(entity.displayName())))
                .collect(Collectors.joining("\n"));
        String authImports = manifest.authentication().enabled()
                ? "import { AuthService } from './core/auth/auth.service';\n"
                : "";
        String authInject = manifest.authentication().enabled()
                ? "  readonly auth = inject(AuthService);\n"
                : "";
        String logout = manifest.authentication().enabled()
                ? "          <button type=\"button\" (click)=\"logout()\">Salir</button>"
                : "";
        String logoutMethod = manifest.authentication().enabled()
                ? """
                  logout(): void {
                    this.auth.logout();
                    this.router.navigateByUrl('/login');
                  }
                  """
                : "";
        String injectImport = manifest.authentication().enabled() ? ", inject" : "";
        String routerInject = manifest.authentication().enabled()
                ? "import { Router } from '@angular/router';\n"
                : "";
        String routerField = manifest.authentication().enabled()
                ? "  private readonly router = inject(Router);\n"
                : "";
        String header = """
                      <header class="app-header">
                        <a class="app-brand" routerLink="/dashboard">Sistema generado</a>
                        <nav class="app-nav" aria-label="Navegacion principal">
                          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
                          <a routerLink="/assistant" routerLinkActive="active">Asistente</a>
                %s
                %s
                        </nav>
                      </header>
                """.formatted(nav, logout);
        if (manifest.authentication().enabled()) {
            header = """
                      @if (auth.authenticated()) {
                %s
                      }
                """.formatted(indent(header.strip(), 2));
        }
        return """
                import { Component%s } from '@angular/core';
                import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
                %s%s
                @Component({
                  selector: 'app-root',
                  standalone: true,
                  imports: [RouterOutlet, RouterLink, RouterLinkActive],
                  template: `
                    <div class="app-shell">
                %s
                      <router-outlet />
                    </div>
                  `,
                })
                export class AppComponent {
                %s%s
                %s}
                """.formatted(injectImport, authImports, routerInject, indent(header.strip(), 6), authInject, routerField, indent(logoutMethod, 2));
    }

    String routes(DomainManifestPlan manifest) {
        List<String> imports = new ArrayList<>();
        imports.add("import { Routes } from '@angular/router';");
        imports.add("import { DashboardComponent } from './dashboard/dashboard.component';");
        imports.add("import { AssistantComponent } from './assistant/assistant.component';");
        if (manifest.authentication().enabled()) {
            imports.add("import { authGuard } from './core/auth/auth.guard';");
            imports.add("import { LoginComponent } from './auth/login.component';");
            imports.add("import { BootstrapComponent } from './auth/bootstrap.component';");
        }
        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String base = "./entities/" + kebab(entity.codeName()) + "/";
            String classBase = entity.codeName();
            String prefix = kebab(entity.codeName());
            imports.add("import { %sListComponent } from '%s%s-list.component';".formatted(classBase, base, prefix));
            imports.add("import { %sDetailComponent } from '%s%s-detail.component';".formatted(classBase, base, prefix));
            imports.add("import { %sFormComponent } from '%s%s-form.component';".formatted(classBase, base, prefix));
        }
        String guard = manifest.authentication().enabled() ? ", canActivate: [authGuard]" : "";
        List<String> routes = new ArrayList<>();
        if (manifest.authentication().enabled()) {
            routes.add("  { path: 'login', component: LoginComponent },");
            routes.add("  { path: 'bootstrap', component: BootstrapComponent },");
        }
        routes.add("  { path: 'dashboard', component: DashboardComponent" + guard + " },");
        routes.add("  { path: 'assistant', component: AssistantComponent" + guard + " },");
        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String path = "entities/" + entity.tableName();
            String cn = entity.codeName();
            routes.add("  { path: '%s', component: %sListComponent%s },".formatted(path, cn, guard));
            routes.add("  { path: '%s/new', component: %sFormComponent%s },".formatted(path, cn, guard));
            routes.add("  { path: '%s/view', component: %sDetailComponent%s },".formatted(path, cn, guard));
            routes.add("  { path: '%s/edit', component: %sFormComponent%s },".formatted(path, cn, guard));
        }
        routes.add("  { path: '', pathMatch: 'full', redirectTo: '" + (manifest.authentication().enabled() ? "dashboard" : "dashboard") + "' },");
        routes.add("  { path: '**', redirectTo: 'dashboard' },");
        return String.join("\n", imports) + "\n\nexport const routes: Routes = [\n" + String.join("\n", routes) + "\n];\n";
    }

    String pageResponse() {
        return """
                export interface PageResponse<T> {
                  content: T[];
                  page: number;
                  size: number;
                  totalElements: number;
                  totalPages: number;
                }
                """;
    }

    String referenceDataService() {
        return """
                import { HttpClient, HttpParams } from '@angular/common/http';
                import { inject, Injectable } from '@angular/core';
                import { EMPTY, Observable, expand, reduce } from 'rxjs';
                import { PageResponse } from './page-response';

                @Injectable({ providedIn: 'root' })
                export class ReferenceDataService {
                  private readonly http = inject(HttpClient);

                  list(endpoint: string): Observable<PageResponse<Record<string, unknown>>> {
                    const fetch = (page: number) => this.http.get<PageResponse<Record<string, unknown>>>(endpoint, {
                      params: new HttpParams().set('page', page).set('size', 200),
                    });
                    return fetch(0).pipe(
                      expand((page) => page.page + 1 < page.totalPages ? fetch(page.page + 1) : EMPTY),
                      reduce((all, page) => ({ ...page, content: [...all.content, ...page.content] }),
                        { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 } as PageResponse<Record<string, unknown>>),
                    );
                  }

                  encodeKey(value: unknown, fields: string[]): string {
                    if (fields.length === 1) return JSON.stringify(value ?? null);
                    const source = (value ?? {}) as Record<string, unknown>;
                    return JSON.stringify(Object.fromEntries(fields.map((field) => [field, source[field] ?? null])));
                  }

                  optionKey(row: Record<string, unknown>, fields: string[]): string {
                    return this.encodeKey(fields.length === 1 ? row[fields[0]] : row, fields);
                  }

                  withSelected(rows: Record<string, unknown>[], value: string | string[] | null, fields: string[]): Record<string, unknown>[] {
                    const choices = new Map(rows.map((row) => [this.optionKey(row, fields), row]));
                    for (const encoded of Array.isArray(value) ? value : value ? [value] : []) {
                      if (choices.has(encoded)) continue;
                      const key = JSON.parse(encoded);
                      choices.set(encoded, fields.length === 1 ? { [fields[0]]: key } : key);
                    }
                    return [...choices.values()];
                  }

                  optionLabel(row: Record<string, unknown>, idFields: string[], labelFields: string[] = []): string {
                    for (const field of labelFields) {
                      const value = row[field];
                      if (typeof value === 'string' && value.trim().length > 0) return value.trim();
                      if (typeof value === 'number' && Number.isFinite(value)) return String(value);
                    }
                    return idFields.map((field) => String(row[field] ?? '')).filter(Boolean).join(' / ');
                  }
                }
                """;
    }

    String dashboard(DomainManifestPlan manifest) {
        String entities = manifest.entities().stream()
                .map(entity -> "    { name: '%s', endpoint: '%s', route: '/entities/%s', count: 0, loading: true, error: false },"
                        .formatted(ts(entity.displayName()), entity.endpoint(), entity.tableName()))
                .collect(Collectors.joining("\n"));
        return """
                import { HttpClient } from '@angular/common/http';
                import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
                import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
                import { RouterLink } from '@angular/router';

                interface DashboardEntity {
                  name: string;
                  endpoint: string;
                  route: string;
                  count: number;
                  loading: boolean;
                  error: boolean;
                }

                @Component({
                  standalone: true,
                  imports: [RouterLink],
                  template: `
                    <main class="page">
                      <div class="page-header">
                        <div>
                          <h1>Dashboard</h1>
                          <p class="muted">Resumen operativo del sistema generado.</p>
                        </div>
                      </div>
                      <section class="dashboard-grid">
                        @for (entity of entities; track entity.endpoint) {
                          <article class="card">
                            <div class="muted">{{ entity.name }}</div>
                            <div class="metric">{{ entity.loading ? '…' : entity.error ? '—' : entity.count }}</div>
                            @if (entity.error) { <p class="error">No se pudo cargar el total.</p> }
                            <a class="btn" [routerLink]="entity.route">Abrir registros</a>
                          </article>
                        }
                      </section>
                    </main>
                  `,
                })
                export class DashboardComponent implements OnInit {
                  private readonly changes = inject(ChangeDetectorRef);
                  private readonly destroyRef = inject(DestroyRef);
                  private readonly http = inject(HttpClient);
                  readonly entities: DashboardEntity[] = [
                %s
                  ];

                  ngOnInit(): void {
                    for (const entity of this.entities) {
                      this.http.get<{ count: number }>(`${entity.endpoint}/count`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                        next: (result) => { entity.count = result.count; entity.loading = false; this.changes.markForCheck(); },
                        error: () => { entity.loading = false; entity.error = true; this.changes.markForCheck(); },
                      });
                    }
                  }
                }
                """.formatted(entities);
    }

    String readme(DomainManifestPlan manifest, String artifactName, String color) {
        return """
                # %s web

                Frontend Angular generado por ClassForge CU-17 + CU-19.

                - Angular: %s
                - API proxy de desarrollo: `http://localhost:8080`
                - Color primario: `%s`
                - Modo: `%s`
                - Componentes especificos por entidad: list, detail y form
                - Dashboard: conteo por entidad + accesos directos
                - Assistant CU-19: chat + voz WAV PCM contra el Spring generado; Whisper/Qwen permanecen en el backend local
                %s

                ## Ejecutar

                ```bash
                npm install
                npm start
                ```

                ## Build

                ```bash
                npm run build
                ```

                La semantica proviene de `domain-manifest.json`; los endpoints coinciden con `openapi.yaml`.
                """.formatted(
                artifactName, AngularFrontendRenderer.ANGULAR_VERSION, color, manifest.generationMode(),
                manifest.authentication().enabled()
                        ? "- Auth: login, bootstrap inicial, Bearer JWT, route guard e interceptor"
                        : "- Auth: no aplica en SIMPLE_CRUD"
        );
    }

    String kebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    String title(String artifactName) {
        return artifactName.replace('-', ' ');
    }

    String normalizeColor(String color) {
        String value = color == null ? "#2563EB" : color.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^#[0-9A-F]{6}$")) throw new IllegalArgumentException("primary color must be #RRGGBB");
        return value;
    }

    String contrast(String color) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance > 0.62 ? "#111827" : "#FFFFFF";
    }

    String indent(String value, int spaces) {
        if (value == null || value.isBlank()) return "";
        String prefix = " ".repeat(spaces);
        return value.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

    String ts(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
