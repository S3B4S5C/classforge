package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AngularFrontendRenderer {
    public static final String ANGULAR_VERSION = "22.1.0";
    private static final String ROOT = "frontend/";

    public List<GeneratedFile> render(
            DomainManifestPlan manifest,
            String artifactName,
            String primaryColor
    ) {
        Objects.requireNonNull(manifest, "manifest is required");
        String color = normalizeColor(primaryColor);
        List<GeneratedFile> files = new ArrayList<>();

        add(files, "package.json", packageJson(artifactName));
        add(files, "angular.json", angularJson(artifactName));
        add(files, "tsconfig.json", tsconfig());
        add(files, "tsconfig.app.json", tsconfigApp());
        add(files, "proxy.conf.json", proxy());
        add(files, "README.md", readme(manifest, artifactName, color));
        add(files, "src/index.html", indexHtml(artifactName));
        add(files, "src/main.ts", mainTs(manifest.authentication().enabled()));
        add(files, "src/styles.css", styles(color));
        add(files, "src/app/app.component.ts", appComponent(manifest));
        add(files, "src/app/app.routes.ts", routes(manifest));
        add(files, "src/app/core/api/page-response.ts", pageResponse());
        add(files, "src/app/core/api/reference-data.service.ts", referenceDataService());
        add(files, "src/app/dashboard/dashboard.component.ts", dashboard(manifest));

        if (manifest.authentication().enabled()) {
            DomainManifestPlan.Entity authEntity = manifest.entities().stream()
                    .filter(entity -> entity.id().equals(manifest.authentication().entityId()))
                    .findFirst()
                    .orElseThrow();
            add(files, "src/app/core/auth/auth.service.ts", authService());
            add(files, "src/app/core/auth/auth.interceptor.ts", authInterceptor());
            add(files, "src/app/core/auth/auth.guard.ts", authGuard());
            add(files, "src/app/auth/login.component.ts", loginComponent());
            add(files, "src/app/auth/bootstrap.component.ts", bootstrapComponent(authEntity, manifest));
        }

        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String dir = "src/app/entities/" + kebab(entity.codeName()) + "/";
            add(files, dir + kebab(entity.codeName()) + ".models.ts", entityModels(entity));
            add(files, dir + kebab(entity.codeName()) + ".api.ts", entityApi(entity));
            add(files, dir + kebab(entity.codeName()) + "-list.component.ts", entityList(entity));
            add(files, dir + kebab(entity.codeName()) + "-detail.component.ts", entityDetail(entity));
            add(files, dir + kebab(entity.codeName()) + "-form.component.ts", entityForm(entity, manifest));
        }

        return List.copyOf(files);
    }

    private void add(List<GeneratedFile> files, String path, String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n")) normalized += "\n";
        files.add(new GeneratedFile(
                ROOT + path,
                GeneratedFileType.TEXT,
                normalized.getBytes(StandardCharsets.UTF_8)
        ));
    }

    private String packageJson(String artifactName) {
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

    private String angularJson(String artifactName) {
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

    private String tsconfig() {
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

    private String tsconfigApp() {
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

    private String proxy() {
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

    private String indexHtml(String artifactName) {
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

    private String mainTs(boolean auth) {
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
                import { bootstrapApplication } from '@angular/platform-browser';
                %s
                import { provideRouter } from '@angular/router';
                import { AppComponent } from './app/app.component';
                import { routes } from './app/app.routes';
                %s

                bootstrapApplication(AppComponent, {
                  providers: [
                    provideRouter(routes),
                    %s,
                  ],
                }).catch((error) => console.error(error));
                """.formatted(httpImport, interceptorImport, httpProvider);
    }

    private String styles(String color) {
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

    private String appComponent(DomainManifestPlan manifest) {
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
                ? """
                          @if (auth.authenticated()) {
                            <button type="button" (click)="logout()">Salir</button>
                          }
                  """
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
                      <header class="app-header">
                        <a class="app-brand" routerLink="/dashboard">Sistema generado</a>
                        <nav class="app-nav" aria-label="Navegacion principal">
                          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
                %s
                %s
                        </nav>
                      </header>
                      <router-outlet />
                    </div>
                  `,
                })
                export class AppComponent {
                %s%s
                %s}
                """.formatted(injectImport, authImports, routerInject, nav, indent(logout, 10), authInject, routerField, indent(logoutMethod, 2));
    }

    private String routes(DomainManifestPlan manifest) {
        List<String> imports = new ArrayList<>();
        imports.add("import { Routes } from '@angular/router';");
        imports.add("import { DashboardComponent } from './dashboard/dashboard.component';");
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

    private String pageResponse() {
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

    private String referenceDataService() {
        return """
                import { HttpClient, HttpParams } from '@angular/common/http';
                import { inject, Injectable } from '@angular/core';
                import { Observable } from 'rxjs';
                import { PageResponse } from './page-response';

                @Injectable({ providedIn: 'root' })
                export class ReferenceDataService {
                  private readonly http = inject(HttpClient);

                  list(endpoint: string): Observable<PageResponse<Record<string, unknown>>> {
                    const params = new HttpParams().set('page', 0).set('size', 200);
                    return this.http.get<PageResponse<Record<string, unknown>>>(endpoint, { params });
                  }

                  optionKey(row: Record<string, unknown>, fields: string[]): string {
                    if (fields.length === 1) return JSON.stringify(row[fields[0]]);
                    const key: Record<string, unknown> = {};
                    for (const field of fields) key[field] = row[field];
                    return JSON.stringify(key);
                  }

                  optionLabel(row: Record<string, unknown>, idFields: string[]): string {
                    const preferred = Object.entries(row).find(([key, value]) =>
                      !idFields.includes(key)
                      && typeof value === 'string'
                      && value.trim().length > 0
                    );
                    if (preferred) return preferred[1] as string;
                    return idFields.map((field) => String(row[field] ?? '')).join(' / ');
                  }
                }
                """;
    }

    private String dashboard(DomainManifestPlan manifest) {
        String entities = manifest.entities().stream()
                .map(entity -> "    { name: '%s', endpoint: '%s', route: '/entities/%s', count: 0, loading: true },"
                        .formatted(ts(entity.displayName()), entity.endpoint(), entity.tableName()))
                .collect(Collectors.joining("\n"));
        return """
                import { HttpClient } from '@angular/common/http';
                import { Component, inject, OnInit } from '@angular/core';
                import { RouterLink } from '@angular/router';

                interface DashboardEntity {
                  name: string;
                  endpoint: string;
                  route: string;
                  count: number;
                  loading: boolean;
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
                            <div class="metric">{{ entity.loading ? '…' : entity.count }}</div>
                            <a class="btn" [routerLink]="entity.route">Abrir registros</a>
                          </article>
                        }
                      </section>
                    </main>
                  `,
                })
                export class DashboardComponent implements OnInit {
                  private readonly http = inject(HttpClient);
                  readonly entities: DashboardEntity[] = [
                %s
                  ];

                  ngOnInit(): void {
                    for (const entity of this.entities) {
                      this.http.get<{ count: number }>(`${entity.endpoint}/count`).subscribe({
                        next: (result) => { entity.count = result.count; entity.loading = false; },
                        error: () => { entity.loading = false; },
                      });
                    }
                  }
                }
                """.formatted(entities);
    }

    private String entityModels(DomainManifestPlan.Entity entity) {
        String requestFields = requestFields(entity);
        String responseFields = responseFields(entity);
        String idFields = entity.identifier().fields().stream()
                .map(field -> "  %s: %s;".formatted(field.name(), tsType(field.type())))
                .collect(Collectors.joining("\n"));
        return """
                export interface %1$sRequest {
                %2$s
                }

                export interface %1$sResponse {
                %3$s
                }

                export interface %1$sId {
                %4$s
                }
                """.formatted(entity.codeName(), requestFields, responseFields, idFields);
    }

    private String requestFields(DomainManifestPlan.Entity entity) {
        List<String> lines = new ArrayList<>();
        for (DomainManifestPlan.Attribute attribute : entity.attributes()) {
            if (!attribute.createWritable() && !attribute.updateWritable()) continue;
            lines.add("  %s%s: %s;".formatted(
                    attribute.apiName(),
                    attribute.nullable() || !attribute.validation().requiredOnCreate() ? "?" : "",
                    tsType(attribute.type()) + (attribute.nullable() ? " | null" : "")
            ));
        }
        for (DomainManifestPlan.Relation relation : entity.relations()) {
            lines.add("  %s%s: %s;".formatted(
                    relation.requestField(),
                    Boolean.TRUE.equals(relation.optional()) ? "?" : "",
                    relationType(relation) + (Boolean.TRUE.equals(relation.optional()) ? " | null" : "")
            ));
        }
        return String.join("\n", lines);
    }

    private String responseFields(DomainManifestPlan.Entity entity) {
        List<String> lines = new ArrayList<>();
        for (DomainManifestPlan.Attribute attribute : entity.attributes()) {
            if (!attribute.readable()) continue;
            lines.add("  %s: %s;".formatted(
                    attribute.apiName(),
                    tsType(attribute.type()) + (attribute.nullable() ? " | null" : "")
            ));
        }
        for (DomainManifestPlan.Relation relation : entity.relations()) {
            lines.add("  %s: %s;".formatted(
                    relation.requestField(),
                    relationType(relation) + (Boolean.TRUE.equals(relation.optional()) ? " | null" : "")
            ));
        }
        return String.join("\n", lines);
    }

    private String relationType(DomainManifestPlan.Relation relation) {
        String base;
        if (relation.targetIdentifier().fields().size() == 1) {
            base = tsType(relation.targetIdentifier().fields().get(0).type());
        } else {
            base = "{ " + relation.targetIdentifier().fields().stream()
                    .map(field -> field.name() + ": " + tsType(field.type()))
                    .collect(Collectors.joining("; ")) + " }";
        }
        return "MANY_TO_MANY".equals(relation.kind()) ? base + "[]" : base;
    }

    private String entityApi(DomainManifestPlan.Entity entity) {
        String cn = entity.codeName();
        String kebab = kebab(cn);
        String idParams = entity.identifier().fields().stream()
                .map(field -> "    params = params.set('%s', String(key.%s));".formatted(field.name(), field.name()))
                .collect(Collectors.joining("\n"));
        String keyFrom = entity.identifier().fields().stream()
                .map(field -> "      %s: row.%s as %s,".formatted(field.name(), field.name(), tsType(field.type())))
                .collect(Collectors.joining("\n"));
        boolean simple = "SIMPLE".equals(entity.identifier().kind());
        String getUrl = simple
                ? "`${this.endpoint}/${encodeURIComponent(String(key.%s))}`".formatted(entity.identifier().fields().get(0).name())
                : "`${this.endpoint}/by-id`";
        String getOptions = simple ? "" : ", { params: this.idParams(key) }";
        String updateUrl = getUrl;
        String updateOptions = simple ? "" : ", { params: this.idParams(key) }";
        String deleteOptions = simple ? "" : ", { params: this.idParams(key) }";

        return """
                import { HttpClient, HttpParams } from '@angular/common/http';
                import { inject, Injectable } from '@angular/core';
                import { Observable } from 'rxjs';
                import { PageResponse } from '../../core/api/page-response';
                import { %1$sId, %1$sRequest, %1$sResponse } from './%2$s.models';

                @Injectable({ providedIn: 'root' })
                export class %1$sApi {
                  private readonly http = inject(HttpClient);
                  readonly endpoint = '%3$s';

                  list(options: {
                    q?: string;
                    sort?: string;
                    direction?: string;
                    page?: number;
                    size?: number;
                    filters?: Record<string, string>;
                  } = {}): Observable<PageResponse<%1$sResponse>> {
                    let params = new HttpParams()
                      .set('page', options.page ?? 0)
                      .set('size', options.size ?? 20);
                    if (options.q) params = params.set('q', options.q);
                    if (options.sort) params = params.set('sort', options.sort);
                    if (options.direction) params = params.set('direction', options.direction);
                    for (const [key, value] of Object.entries(options.filters ?? {})) {
                      if (value !== '') params = params.set(`filter.${key}`, value);
                    }
                    return this.http.get<PageResponse<%1$sResponse>>(this.endpoint, { params });
                  }

                  count(): Observable<{ count: number }> {
                    return this.http.get<{ count: number }>(`${this.endpoint}/count`);
                  }

                  get(key: %1$sId): Observable<%1$sResponse> {
                    return this.http.get<%1$sResponse>(%4$s%5$s);
                  }

                  create(request: %1$sRequest): Observable<%1$sResponse> {
                    return this.http.post<%1$sResponse>(this.endpoint, request);
                  }

                  update(key: %1$sId, request: %1$sRequest): Observable<%1$sResponse> {
                    return this.http.put<%1$sResponse>(%6$s, request%7$s);
                  }

                  delete(key: %1$sId): Observable<void> {
                    return this.http.delete<void>(%6$s%8$s);
                  }

                  keyFromRow(row: %1$sResponse): %1$sId {
                    return {
                %9$s
                    };
                  }

                  keyQuery(row: %1$sResponse): Record<string, string> {
                    const key = this.keyFromRow(row);
                    return Object.fromEntries(
                      Object.entries(key).map(([name, value]) => [name, String(value)])
                    );
                  }

                  keyFromQuery(params: Record<string, string | null>): %1$sId {
                    return {
                %10$s
                    };
                  }

                  private idParams(key: %1$sId): HttpParams {
                    let params = new HttpParams();
                %11$s
                    return params;
                  }
                }
                """.formatted(
                cn, kebab, entity.endpoint(), getUrl, getOptions,
                updateUrl, updateOptions, deleteOptions, keyFrom,
                queryToId(entity), idParams
        );
    }

    private String queryToId(DomainManifestPlan.Entity entity) {
        return entity.identifier().fields().stream()
                .map(field -> "      %s: %s,".formatted(
                        field.name(),
                        parseExpression("params['" + field.name() + "']", field.type())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String entityList(DomainManifestPlan.Entity entity) {
        String cn = entity.codeName();
        String kebab = kebab(cn);
        List<DomainManifestPlan.Attribute> columns = entity.attributes().stream()
                .filter(DomainManifestPlan.Attribute::readable)
                .limit(6)
                .toList();
        String headers = columns.stream()
                .map(field -> "              <th>%s</th>".formatted(escapeHtml(field.logicalName())))
                .collect(Collectors.joining("\n"));
        String cells = columns.stream()
                .map(field -> "              <td>{{ format(row.%s) }}</td>".formatted(field.apiName()))
                .collect(Collectors.joining("\n"));
        String filtersHtml = entity.attributes().stream()
                .filter(DomainManifestPlan.Attribute::filterable)
                .limit(5)
                .map(field -> """
                          <div class="field">
                            <label>%s</label>
                            <input [value]="filters['%s']" (input)="setFilter('%s', $any($event.target).value)" />
                          </div>
                        """.formatted(escapeHtml(field.logicalName()), field.apiName(), field.apiName()))
                .collect(Collectors.joining("\n"));
        String sortOptions = entity.attributes().stream()
                .filter(DomainManifestPlan.Attribute::sortable)
                .map(field -> "              <option value=\"%s\">%s</option>".formatted(field.apiName(), escapeHtml(field.logicalName())))
                .collect(Collectors.joining("\n"));
        return """
                import { Component, inject, OnInit } from '@angular/core';
                import { FormsModule } from '@angular/forms';
                import { Router, RouterLink } from '@angular/router';
                import { %1$sApi } from './%2$s.api';
                import { %1$sResponse } from './%2$s.models';

                @Component({
                  standalone: true,
                  imports: [FormsModule, RouterLink],
                  template: `
                    <main class="page">
                      <div class="page-header">
                        <div>
                          <h1>%3$s</h1>
                          <p class="muted">{{ total }} registro(s)</p>
                        </div>
                        <a class="btn btn-primary" routerLink="/entities/%4$s/new">Crear</a>
                      </div>

                      <section class="card toolbar">
                        <div class="field">
                          <label>Buscar</label>
                          <input [(ngModel)]="q" (keyup.enter)="load(0)" placeholder="Texto libre" />
                        </div>
                %5$s
                        <div class="field">
                          <label>Ordenar por</label>
                          <select [(ngModel)]="sort">
                            <option value="">Sin orden</option>
                %6$s
                          </select>
                        </div>
                        <div class="field">
                          <label>Direccion</label>
                          <select [(ngModel)]="direction">
                            <option value="asc">Ascendente</option>
                            <option value="desc">Descendente</option>
                          </select>
                        </div>
                        <button class="btn" type="button" (click)="load(0)">Aplicar</button>
                      </section>

                      <div class="table-wrap">
                        <table>
                          <thead>
                            <tr>
                %7$s
                              <th>Acciones</th>
                            </tr>
                          </thead>
                          <tbody>
                            @for (row of rows; track trackRow(row)) {
                              <tr>
                %8$s
                                <td class="actions">
                                  <button class="btn" type="button" (click)="view(row)">Ver</button>
                                  <button class="btn" type="button" (click)="edit(row)">Editar</button>
                                  <button class="btn btn-danger" type="button" (click)="remove(row)">Eliminar</button>
                                </td>
                              </tr>
                            } @empty {
                              <tr><td colspan="%9$d">No hay registros.</td></tr>
                            }
                          </tbody>
                        </table>
                      </div>

                      <div class="pager">
                        <button class="btn" type="button" [disabled]="page === 0" (click)="load(page - 1)">Anterior</button>
                        <span>Pagina {{ page + 1 }} de {{ totalPages || 1 }}</span>
                        <button class="btn" type="button" [disabled]="page + 1 >= totalPages" (click)="load(page + 1)">Siguiente</button>
                      </div>
                    </main>
                  `,
                })
                export class %1$sListComponent implements OnInit {
                  private readonly api = inject(%1$sApi);
                  private readonly router = inject(Router);
                  rows: %1$sResponse[] = [];
                  q = '';
                  sort = '';
                  direction = 'asc';
                  filters: Record<string, string> = {};
                  page = 0;
                  readonly size = 20;
                  total = 0;
                  totalPages = 0;

                  ngOnInit(): void { this.load(0); }

                  load(page: number): void {
                    this.api.list({ q: this.q, sort: this.sort, direction: this.direction, page, size: this.size, filters: this.filters })
                      .subscribe((result) => {
                        this.rows = result.content;
                        this.page = result.page;
                        this.total = result.totalElements;
                        this.totalPages = result.totalPages;
                      });
                  }

                  setFilter(name: string, value: string): void { this.filters[name] = value; }
                  trackRow(row: %1$sResponse): string { return JSON.stringify(this.api.keyFromRow(row)); }
                  format(value: unknown): string {
                    if (value === null || value === undefined) return '—';
                    if (Array.isArray(value)) return value.join(', ');
                    if (typeof value === 'object') return JSON.stringify(value);
                    return String(value);
                  }
                  view(row: %1$sResponse): void {
                    this.router.navigate(['/entities/%4$s/view'], { queryParams: this.api.keyQuery(row) });
                  }
                  edit(row: %1$sResponse): void {
                    this.router.navigate(['/entities/%4$s/edit'], { queryParams: this.api.keyQuery(row) });
                  }
                  remove(row: %1$sResponse): void {
                    if (!confirm('Eliminar este registro?')) return;
                    this.api.delete(this.api.keyFromRow(row)).subscribe(() => this.load(this.page));
                  }
                }
                """.formatted(
                cn, kebab, escapeHtml(entity.displayName()), entity.tableName(),
                indent(filtersHtml, 16), sortOptions, headers, cells, columns.size() + 1
        );
    }

    private String entityDetail(DomainManifestPlan.Entity entity) {
        String cn = entity.codeName();
        String kebab = kebab(cn);
        String items = entity.attributes().stream()
                .filter(DomainManifestPlan.Attribute::readable)
                .map(field -> """
                              <div class="detail-item">
                                <strong>%s</strong>
                                <span>{{ format(record.%s) }}</span>
                              </div>
                        """.formatted(escapeHtml(field.logicalName()), field.apiName()))
                .collect(Collectors.joining("\n"));
        String relationItems = entity.relations().stream()
                .map(rel -> """
                              <div class="detail-item">
                                <strong>%s</strong>
                                <span>{{ format(record.%s) }}</span>
                              </div>
                        """.formatted(escapeHtml(rel.name()), rel.requestField()))
                .collect(Collectors.joining("\n"));
        return """
                import { Component, inject, OnInit } from '@angular/core';
                import { ActivatedRoute, Router, RouterLink } from '@angular/router';
                import { %1$sApi } from './%2$s.api';
                import { %1$sResponse } from './%2$s.models';

                @Component({
                  standalone: true,
                  imports: [RouterLink],
                  template: `
                    <main class="page">
                      <div class="page-header">
                        <h1>Detalle — %3$s</h1>
                        <div class="actions">
                          <button class="btn" type="button" (click)="edit()">Editar</button>
                          <a class="btn" routerLink="/entities/%4$s">Volver</a>
                        </div>
                      </div>
                      @if (record) {
                        <section class="card detail-grid">
                %5$s
                %6$s
                        </section>
                      }
                    </main>
                  `,
                })
                export class %1$sDetailComponent implements OnInit {
                  private readonly api = inject(%1$sApi);
                  private readonly route = inject(ActivatedRoute);
                  private readonly router = inject(Router);
                  record: %1$sResponse | null = null;

                  ngOnInit(): void {
                    const key = this.api.keyFromQuery(this.route.snapshot.queryParamMap.keys
                      .reduce<Record<string, string | null>>((all, key) => {
                        all[key] = this.route.snapshot.queryParamMap.get(key);
                        return all;
                      }, {}));
                    this.api.get(key).subscribe((record) => this.record = record);
                  }

                  edit(): void {
                    if (!this.record) return;
                    this.router.navigate(['/entities/%4$s/edit'], { queryParams: this.api.keyQuery(this.record) });
                  }

                  format(value: unknown): string {
                    if (value === null || value === undefined) return '—';
                    if (Array.isArray(value)) return value.join(', ');
                    if (typeof value === 'object') return JSON.stringify(value);
                    return String(value);
                  }
                }
                """.formatted(cn, kebab, escapeHtml(entity.displayName()), entity.tableName(), indent(items, 16), indent(relationItems, 16));
    }

    private String entityForm(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
        String cn = entity.codeName();
        String kebab = kebab(cn);
        String controls = formControls(entity);
        String templateFields = formFields(entity, manifest);
        String patch = patchValue(entity);
        String payload = payload(entity);
        String relationSetup = relationSetup(entity, manifest);
        String relationState = relationState(entity, manifest);
        return """
                import { Component, inject, OnInit } from '@angular/core';
                import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
                import { ActivatedRoute, Router, RouterLink } from '@angular/router';
                import { ReferenceDataService } from '../../core/api/reference-data.service';
                import { %1$sApi } from './%2$s.api';
                import { %1$sId, %1$sRequest, %1$sResponse } from './%2$s.models';

                @Component({
                  standalone: true,
                  imports: [ReactiveFormsModule, RouterLink],
                  template: `
                    <main class="page">
                      <div class="page-header">
                        <h1>{{ editing ? 'Editar' : 'Crear' }} — %3$s</h1>
                        <a class="btn" routerLink="/entities/%4$s">Volver</a>
                      </div>
                      <form class="card" [formGroup]="form" (ngSubmit)="save()">
                        <div class="form-grid">
                %5$s
                        </div>
                        @if (error) { <div class="error">{{ error }}</div> }
                        <div class="form-actions">
                          <button class="btn btn-primary" type="submit" [disabled]="form.invalid || saving">
                            {{ saving ? 'Guardando…' : 'Guardar' }}
                          </button>
                          <a class="btn" routerLink="/entities/%4$s">Cancelar</a>
                        </div>
                      </form>
                    </main>
                  `,
                })
                export class %1$sFormComponent implements OnInit {
                  private readonly api = inject(%1$sApi);
                  readonly references = inject(ReferenceDataService);
                  private readonly route = inject(ActivatedRoute);
                  private readonly router = inject(Router);
                  editing = false;
                  saving = false;
                  error = '';
                  private key: %1$sId | null = null;
                  readonly form = new FormGroup({
                %6$s
                  });
                %7$s

                  ngOnInit(): void {
                %8$s
                    const params = this.route.snapshot.queryParamMap;
                    if (params.keys.length > 0) {
                      this.editing = true;
                      this.key = this.api.keyFromQuery(params.keys.reduce<Record<string, string | null>>((all, name) => {
                        all[name] = params.get(name);
                        return all;
                      }, {}));
                      this.api.get(this.key).subscribe((record) => this.patch(record));
                    }
                  }

                  save(): void {
                    if (this.form.invalid) {
                      this.form.markAllAsTouched();
                      return;
                    }
                    this.saving = true;
                    this.error = '';
                    const request = this.toRequest();
                    const action = this.editing && this.key
                      ? this.api.update(this.key, request)
                      : this.api.create(request);
                    action.subscribe({
                      next: (saved) => {
                        this.saving = false;
                        this.router.navigate(['/entities/%4$s/view'], { queryParams: this.api.keyQuery(saved) });
                      },
                      error: (failure) => {
                        this.saving = false;
                        this.error = failure?.error?.message ?? 'No se pudo guardar el registro.';
                      },
                    });
                  }

                  private patch(record: %1$sResponse): void {
                    this.form.patchValue({
                %9$s
                    });
                  }

                  private toRequest(): %1$sRequest {
                    const raw = this.form.getRawValue();
                    return {
                %10$s
                    } as %1$sRequest;
                  }

                  parseOption(value: string, multiple: boolean): unknown {
                    if (multiple) return (value ? [value] : []).map((item) => JSON.parse(item));
                    return value === '' ? null : JSON.parse(value);
                  }
                }
                """.formatted(
                cn, kebab, escapeHtml(entity.displayName()), entity.tableName(), indent(templateFields, 16),
                controls, relationState, relationSetup, patch, payload
        );
    }

    private String formControls(DomainManifestPlan.Entity entity) {
        List<String> out = new ArrayList<>();
        for (DomainManifestPlan.Attribute a : entity.attributes()) {
            if (!a.createWritable() && !a.updateWritable()) continue;
            String initial = a.type() == DomainManifestPlan.SemanticType.BOOLEAN ? "false" : "null";
            String validators = a.validation().requiredOnCreate() && !a.writeOnly()
                    ? ", { validators: [Validators.required] }"
                    : "";
            String type = a.type() == DomainManifestPlan.SemanticType.BOOLEAN
                    ? "boolean"
                    : tsType(a.type()) + " | null";
            out.add("    %s: new FormControl<%s>(%s%s),".formatted(
                    a.apiName(),
                    type,
                    initial,
                    validators
            ));
        }
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            boolean many = "MANY_TO_MANY".equals(rel.kind());
            String type = many ? "string[]" : "string | null";
            String initial = many ? "[]" : "null";
            String validators = !many && !Boolean.TRUE.equals(rel.optional()) ? ", { validators: [Validators.required] }" : "";
            out.add("    %s: new FormControl<%s>(%s%s),".formatted(rel.requestField(), type, initial, validators));
        }
        return String.join("\n", out);
    }

    private String relationState(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
        List<String> out = new ArrayList<>();
        Map<java.util.UUID, DomainManifestPlan.Entity> byId = manifest.entities().stream()
                .collect(Collectors.toMap(DomainManifestPlan.Entity::id, e -> e));
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            DomainManifestPlan.Entity target = byId.get(rel.targetEntityId());
            out.add("  %sOptions: Record<string, unknown>[] = [];".formatted(rel.name()));
            out.add("  readonly %sIdFields = [%s];".formatted(
                    rel.name(),
                    target.identifier().fields().stream().map(f -> "'" + ts(f.name()) + "'").collect(Collectors.joining(", "))
            ));
        }
        return String.join("\n", out);
    }

    private String relationSetup(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
        if (entity.relations().isEmpty()) return "    // No relation reference data required.";
        Map<java.util.UUID, DomainManifestPlan.Entity> byId = manifest.entities().stream()
                .collect(Collectors.toMap(DomainManifestPlan.Entity::id, e -> e));
        List<String> out = new ArrayList<>();
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            DomainManifestPlan.Entity target = byId.get(rel.targetEntityId());
            out.add("    this.references.list('%s').subscribe((page) => this.%sOptions = page.content);"
                    .formatted(target.endpoint(), rel.name()));
        }
        return String.join("\n", out);
    }

    private String formFields(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
        List<String> out = new ArrayList<>();
        for (DomainManifestPlan.Attribute a : entity.attributes()) {
            if (!a.createWritable() && !a.updateWritable()) continue;
            String inputType = inputType(a);
            if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) {
                out.add("""
                          <label class="field">
                            <span>%s</span>
                            <input type="checkbox" formControlName="%s" />
                          </label>
                        """.formatted(escapeHtml(a.logicalName()), a.apiName()));
            } else {
                out.add("""
                          <label class="field">
                            <span>%s</span>
                            <input type="%s" formControlName="%s" %s />
                          </label>
                        """.formatted(
                        escapeHtml(a.logicalName()),
                        inputType,
                        a.apiName(),
                        a.validation().requiredOnCreate() && !a.writeOnly() ? "required" : ""
                ));
            }
        }
        Map<java.util.UUID, DomainManifestPlan.Entity> byId = manifest.entities().stream()
                .collect(Collectors.toMap(DomainManifestPlan.Entity::id, e -> e));
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            DomainManifestPlan.Entity target = byId.get(rel.targetEntityId());
            boolean many = "MANY_TO_MANY".equals(rel.kind());
            out.add("""
                          <label class="field">
                            <span>%s</span>
                            <select formControlName="%s" %s>
                              %s
                              @for (option of %sOptions; track references.optionKey(option, %sIdFields)) {
                                <option [value]="references.optionKey(option, %sIdFields)">
                                  {{ references.optionLabel(option, %sIdFields) }}
                                </option>
                              }
                            </select>
                          </label>
                        """.formatted(
                    escapeHtml(rel.name()),
                    rel.requestField(),
                    many ? "multiple" : "",
                    many ? "" : "<option value=\"\">—</option>",
                    rel.name(), rel.name(), rel.name(), rel.name()
            ));
        }
        return String.join("\n", out);
    }

    private String patchValue(DomainManifestPlan.Entity entity) {
        List<String> out = new ArrayList<>();
        for (DomainManifestPlan.Attribute a : entity.attributes()) {
            if ((!a.createWritable() && !a.updateWritable()) || a.writeOnly()) continue;
            out.add("      %s: record.%s,".formatted(a.apiName(), a.apiName()));
        }
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            boolean many = "MANY_TO_MANY".equals(rel.kind());
            if (many) {
                out.add("      %s: (record.%s ?? []).map((value) => JSON.stringify(value)),".formatted(rel.requestField(), rel.requestField()));
            } else {
                out.add("      %s: record.%s == null ? null : JSON.stringify(record.%s),".formatted(rel.requestField(), rel.requestField(), rel.requestField()));
            }
        }
        return String.join("\n", out);
    }

    private String payload(DomainManifestPlan.Entity entity) {
        List<String> out = new ArrayList<>();
        for (DomainManifestPlan.Attribute a : entity.attributes()) {
            if (!a.createWritable() && !a.updateWritable()) continue;
            out.add("      %s: raw.%s as %s,".formatted(a.apiName(), a.apiName(), tsType(a.type()) + (a.nullable() ? " | null" : "")));
        }
        for (DomainManifestPlan.Relation rel : entity.relations()) {
            boolean many = "MANY_TO_MANY".equals(rel.kind());
            out.add("      %s: %s,".formatted(
                    rel.requestField(),
                    many
                            ? "(raw.%s ?? []).map((value) => JSON.parse(value))".formatted(rel.requestField())
                            : "raw.%s == null || raw.%s === '' ? null : JSON.parse(raw.%s)".formatted(rel.requestField(), rel.requestField(), rel.requestField())
            ));
        }
        return String.join("\n", out);
    }

    private String authService() {
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

    private String authInterceptor() {
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

    private String authGuard() {
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

    private String loginComponent() {
        return """
                import { Component, inject } from '@angular/core';
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
                  private readonly auth = inject(AuthService);
                  private readonly router = inject(Router);
                  loading = false;
                  error = '';
                  readonly form = new FormGroup({
                    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
                    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
                  });

                  submit(): void {
                    if (this.form.invalid) return;
                    this.loading = true;
                    this.error = '';
                    this.auth.login(this.form.controls.username.value, this.form.controls.password.value).subscribe({
                      next: () => this.router.navigateByUrl('/dashboard'),
                      error: (failure) => { this.loading = false; this.error = failure?.error?.message ?? 'No se pudo iniciar sesion.'; },
                    });
                  }
                }
                """;
    }

    private String bootstrapComponent(DomainManifestPlan.Entity authEntity, DomainManifestPlan manifest) {
        String form = entityForm(authEntity, manifest);
        // Reuse the same explicit field semantics but point submission to /api/auth/bootstrap.
        String component = form
                .replace("import { ReferenceDataService } from '../../core/api/reference-data.service';", "import { ReferenceDataService } from '../core/api/reference-data.service';")
                .replace("import { " + authEntity.codeName() + "Api } from './" + kebab(authEntity.codeName()) + ".api';\n", "")
                .replace("import { " + authEntity.codeName() + "Id, " + authEntity.codeName() + "Request, " + authEntity.codeName() + "Response } from './" + kebab(authEntity.codeName()) + ".models';\n",
                        "import { AuthService } from '../core/auth/auth.service';\n")
                .replace("export class " + authEntity.codeName() + "FormComponent", "export class BootstrapComponent")
                .replace("  private readonly api = inject(" + authEntity.codeName() + "Api);\n", "  private readonly auth = inject(AuthService);\n")
                .replace("  editing = false;\n", "")
                .replace("  private key: " + authEntity.codeName() + "Id | null = null;\n", "")
                .replace("{{ editing ? 'Editar' : 'Crear' }} — " + escapeHtml(authEntity.displayName()), "Primera cuenta")
                .replace("<a class=\"btn\" routerLink=\"/entities/" + authEntity.tableName() + "\">Volver</a>", "<a class=\"btn\" routerLink=\"/login\">Volver al login</a>")
                .replace("<a class=\"btn\" routerLink=\"/entities/" + authEntity.tableName() + "\">Cancelar</a>", "<a class=\"btn\" routerLink=\"/login\">Cancelar</a>");
        int initStart = component.indexOf("  ngOnInit(): void {");
        int saveStart = component.indexOf("  save(): void {");
        if (initStart >= 0 && saveStart > initStart) {
            String before = component.substring(0, initStart);
            String after = component.substring(saveStart);
            component = before + "  ngOnInit(): void {\n" + indent(relationSetup(authEntity, manifest), 4) + "\n  }\n\n" + after;
        }
        int patchStart = component.indexOf("  private patch(");
        if (patchStart >= 0) {
            int toReq = component.indexOf("  private toRequest()", patchStart);
            component = component.substring(0, patchStart) + component.substring(toReq);
        }
        int saveMethod = component.indexOf("  save(): void {");
        int toRequestMethod = component.indexOf("  private toRequest()", saveMethod);
        if (saveMethod >= 0 && toRequestMethod > saveMethod) {
            String newSave = """
                  save(): void {
                    if (this.form.invalid) {
                      this.form.markAllAsTouched();
                      return;
                    }
                    this.saving = true;
                    this.error = '';
                    this.auth.bootstrap(this.toRequest()).subscribe({
                      next: () => this.router.navigateByUrl('/dashboard'),
                      error: (failure) => {
                        this.saving = false;
                        this.error = failure?.error?.message ?? 'No se pudo crear la primera cuenta.';
                      },
                    });
                  }

                """;
            component = component.substring(0, saveMethod) + newSave + component.substring(toRequestMethod);
        }
        DomainManifestPlan.Attribute password = authEntity.attributes().stream()
                .filter(attribute -> attribute.id().equals(manifest.authentication().passwordAttributeId()))
                .findFirst()
                .orElseThrow();
        component = component.replace(
                password.apiName() + ": new FormControl<string | null>(null),",
                password.apiName() + ": new FormControl<string | null>(null, { validators: [Validators.required] }),"
        );
        component = component.replace(
                "formControlName=\"" + password.apiName() + "\"  />",
                "formControlName=\"" + password.apiName() + "\" required />"
        );
        component = component.replace("private toRequest(): " + authEntity.codeName() + "Request", "private toRequest(): Record<string, unknown>");
        component = component.replace("} as " + authEntity.codeName() + "Request;", "};");
        return component;
    }

    private String readme(DomainManifestPlan manifest, String artifactName, String color) {
        return """
                # %s web

                Frontend Angular generado por ClassForge CU-17.

                - Angular: %s
                - API proxy de desarrollo: `http://localhost:8080`
                - Color primario: `%s`
                - Modo: `%s`
                - Componentes especificos por entidad: list, detail y form
                - Dashboard: conteo por entidad + accesos directos
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
                artifactName, ANGULAR_VERSION, color, manifest.generationMode(),
                manifest.authentication().enabled()
                        ? "- Auth: login, bootstrap inicial, Bearer JWT, route guard e interceptor"
                        : "- Auth: no aplica en SIMPLE_CRUD"
        );
    }

    private String controlType(DomainManifestPlan.SemanticType type, boolean nullable) {
        return tsType(type) + (nullable ? " | null" : "");
    }

    private String inputType(DomainManifestPlan.Attribute attribute) {
        if (attribute.writeOnly()) return "password";
        return switch (attribute.type()) {
            case INTEGER, LONG, DECIMAL -> "number";
            case DATE -> "date";
            case DATETIME -> "datetime-local";
            default -> "text";
        };
    }

    private String tsType(DomainManifestPlan.SemanticType type) {
        return switch (type) {
            case INTEGER, LONG, DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private String parseExpression(String expression, DomainManifestPlan.SemanticType type) {
        return switch (type) {
            case INTEGER, LONG, DECIMAL -> "Number(" + expression + " ?? 0)";
            case BOOLEAN -> expression + " === 'true'";
            default -> expression + " ?? ''";
        };
    }

    private String kebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    private String title(String artifactName) {
        return artifactName.replace('-', ' ');
    }

    private String normalizeColor(String color) {
        String value = color == null ? "#2563EB" : color.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^#[0-9A-F]{6}$")) throw new IllegalArgumentException("primary color must be #RRGGBB");
        return value;
    }

    private String contrast(String color) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance > 0.62 ? "#111827" : "#FFFFFF";
    }

    private String indent(String value, int spaces) {
        if (value == null || value.isBlank()) return "";
        String prefix = " ".repeat(spaces);
        return value.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

    private String ts(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
