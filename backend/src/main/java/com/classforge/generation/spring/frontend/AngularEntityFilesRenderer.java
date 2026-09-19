package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class AngularEntityFilesRenderer {

    String entityModels(DomainManifestPlan.Entity entity) {
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

    String requestFields(DomainManifestPlan.Entity entity) {
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

    String responseFields(DomainManifestPlan.Entity entity) {
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

    String relationType(DomainManifestPlan.Relation relation) {
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

    String entityApi(DomainManifestPlan.Entity entity) {
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

    String queryToId(DomainManifestPlan.Entity entity) {
        return entity.identifier().fields().stream()
                .map(field -> "      %s: %s,".formatted(
                        field.name(),
                        parseExpression("params['" + field.name() + "']", field.type())
                ))
                .collect(Collectors.joining("\n"));
    }

    String entityList(DomainManifestPlan.Entity entity) {
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

    String entityDetail(DomainManifestPlan.Entity entity) {
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

    String entityForm(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
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

    String formControls(DomainManifestPlan.Entity entity) {
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

    String relationState(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
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

    String relationSetup(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
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

    String formFields(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
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

    String patchValue(DomainManifestPlan.Entity entity) {
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

    String payload(DomainManifestPlan.Entity entity) {
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

    String bootstrapComponent(DomainManifestPlan.Entity authEntity, DomainManifestPlan manifest) {
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

    String controlType(DomainManifestPlan.SemanticType type, boolean nullable) {
        return tsType(type) + (nullable ? " | null" : "");
    }

    String inputType(DomainManifestPlan.Attribute attribute) {
        if (attribute.writeOnly()) return "password";
        return switch (attribute.type()) {
            case INTEGER, LONG, DECIMAL -> "number";
            case DATE -> "date";
            case DATETIME -> "datetime-local";
            default -> "text";
        };
    }

    String tsType(DomainManifestPlan.SemanticType type) {
        return switch (type) {
            case INTEGER, LONG, DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    String parseExpression(String expression, DomainManifestPlan.SemanticType type) {
        return switch (type) {
            case INTEGER, LONG, DECIMAL -> "Number(" + expression + " ?? 0)";
            case BOOLEAN -> expression + " === 'true'";
            default -> expression + " ?? ''";
        };
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
