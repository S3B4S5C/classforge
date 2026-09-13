import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import {
  defaultSpringArtifactName,
  defaultSpringBasePackage,
  extractSpringDownloadFileName,
  isValidSpringBasePackage,
  springBootGenerationErrorCopy,
} from './spring-boot-generation.utils.ts';

test('derives safe deterministic generation defaults', () => {
  assert.equal(
    defaultSpringArtifactName('Biblioteca Central'),
    'biblioteca-central',
  );
  assert.equal(
    defaultSpringArtifactName('Árbol & Café'),
    'arbol-cafe',
  );
  assert.equal(
    defaultSpringArtifactName('2026 Demo'),
    'app-2026-demo',
  );
  assert.equal(
    defaultSpringBasePackage('biblioteca-api'),
    'com.example.biblioteca_api',
  );
});

test('validates base packages with Java keyword rejection', () => {
  assert.equal(
    isValidSpringBasePackage('com.example.biblioteca'),
    true,
  );
  assert.equal(
    isValidSpringBasePackage('com.example.generated_app'),
    true,
  );
  assert.equal(
    isValidSpringBasePackage('com.example.class'),
    false,
  );
  assert.equal(
    isValidSpringBasePackage('com.example.true'),
    false,
  );
  assert.equal(
    isValidSpringBasePackage('Com.example.demo'),
    false,
  );
  assert.equal(
    isValidSpringBasePackage('com..demo'),
    false,
  );
});

test('extracts attachment filenames safely', () => {
  assert.equal(
    extractSpringDownloadFileName(
      'attachment; filename="biblioteca-backend.zip"',
      'fallback.zip',
    ),
    'biblioteca-backend.zip',
  );
  assert.equal(
    extractSpringDownloadFileName(
      "attachment; filename*=UTF-8''biblioteca%20api.zip",
      'fallback.zip',
    ),
    'biblioteca api.zip',
  );
  assert.equal(
    extractSpringDownloadFileName(null, 'fallback.zip'),
    'fallback.zip',
  );
});

test('maps stale revision to actionable retry guidance', () => {
  const copy = springBootGenerationErrorCopy(
    {
      status: 409,
      code: 'STALE_PROJECT_REVISION',
      payload: { message: 'stale' },
    },
  );

  assert.equal(copy.staleRevision, true);
  assert.equal(copy.primaryKeyFallbackAvailable, false);
  assert.match(copy.message, /sincronizarse/i);
});

test('offers explicit first-attribute primary-key fallback only for the dedicated backend category', () => {
  const copy = springBootGenerationErrorCopy(
    {
      status: 400,
      code: 'PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED',
      payload: {
        message: 'fallback',
        diagnostics: [
          {
            code: 'CLASS_IDENTIFIER_REQUIRED',
            path: 'classes',
          },
        ],
        primaryKeyFallbacks: [
          {
            className: 'Cliente',
            attributeName: 'codigo',
          },
          {
            className: 'Prestamo',
            attributeName: 'fecha',
          },
        ],
      },
    },
  );

  assert.equal(copy.primaryKeyFallbackAvailable, true);
  assert.match(copy.title, /claves primarias/i);
  assert.match(copy.message, /no modifica tu UML/i);
  assert.deepEqual(copy.observations, [
    'Cliente: se usará “codigo” como clave primaria para esta exportación.',
    'Prestamo: se usará “fecha” como clave primaria para esta exportación.',
  ]);
});

test('keeps non-recoverable UML mapping failures fail-closed with observations', () => {
  const copy = springBootGenerationErrorCopy(
    {
      status: 400,
      code: 'RELATIONAL_MAPPING_REJECTED',
      payload: {
        message: 'invalid',
        diagnostics: [
          {
            code: 'CUSTOM_TYPE_UNSUPPORTED',
            path: 'attributes',
          },
          {
            code: 'UNSUPPORTED_MULTIPLICITY',
            path: 'relationships',
          },
        ],
      },
    },
  );

  assert.equal(copy.primaryKeyFallbackAvailable, false);
  assert.match(copy.title, /no está completamente listo/i);
  assert.equal(copy.observations.length, 2);
  assert.match(copy.observations[0], /tipo personalizado/i);
  assert.match(copy.observations[1], /multiplicidad/i);
});

test('keeps internal generation errors concise', () => {
  const copy = springBootGenerationErrorCopy(
    {
      status: 500,
      code: 'TEMPLATE_RENDER_FAILED',
      payload: { message: 'SECRET_INTERNAL_DETAIL' },
    },
  );

  assert.equal(copy.staleRevision, false);
  assert.equal(copy.primaryKeyFallbackAvailable, false);
  assert.doesNotMatch(copy.message, /SECRET_INTERNAL_DETAIL/);
});

test('wires the Spring Boot export endpoint as a binary download', () => {
  const source = readFileSync(
    new URL(
      '../data/spring-boot-generation-api.service.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(
    source,
    /\/generation\/spring-boot/,
  );
  assert.match(source, /responseType:\s*'blob'/);
  assert.match(source, /Content-Disposition/);
  assert.match(source, /primaryKeyFallbacks/);
});

test('keeps baseRevision automatic and adds explicit non-persistent primary-key confirmation', () => {
  const workspace = readFileSync(
    new URL(
      '../pages/project-workspace/project-workspace.page.ts',
      import.meta.url,
    ),
    'utf8',
  );
  const dialog = readFileSync(
    new URL(
      '../dialogs/spring-boot-export-dialog/spring-boot-export-dialog.component.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(
    workspace,
    /baseRevision:\s*\n\s*this\.store\.revision\(\)/,
  );
  assert.doesNotMatch(
    dialog,
    /formControlName="baseRevision"/,
  );
  assert.match(
    dialog,
    /generate\(true\)/,
  );
  assert.match(
    dialog,
    /useFirstAttributeAsIdentifier/,
  );
  assert.match(
    dialog,
    /Continuar usando el primer atributo como clave primaria/i,
  );
  assert.doesNotMatch(
    dialog,
    /RelationalModel|FreeMarker|PasswordEncoder/,
  );
});

test('exposes the two CU-14 generation modes and explicit Auth selectors', () => {
  const dialog = readFileSync(
    new URL(
      '../dialogs/spring-boot-export-dialog/spring-boot-export-dialog.component.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(dialog, /value="SIMPLE_CRUD"/);
  assert.match(dialog, /value="AUTH_INFORMATION_SYSTEM"/);
  assert.match(dialog, /CRUD simple/);
  assert.match(dialog, /Sistema de Informacion con Auth/);
  assert.match(dialog, /formControlName="authClassId"/);
  assert.match(dialog, /formControlName="usernameAttributeId"/);
  assert.match(dialog, /formControlName="passwordAttributeId"/);
  assert.match(dialog, /login y JWT/);
});

test('advertises CU-15 OpenAPI and Postman artifacts without adding a new mode', () => {
  const dialog = readFileSync(
    new URL(
      '../dialogs/spring-boot-export-dialog/spring-boot-export-dialog.component.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(dialog, /openapi\.yaml/);
  assert.match(dialog, /coleccion Postman/i);
  assert.doesNotMatch(dialog, /formControlName="(?:openApi|postman|apiArtifacts)"/);
});


test('advertises CU-16 Domain Manifest as a generated artifact without adding configuration', () => {
  const dialog = readFileSync(
    new URL(
      '../dialogs/spring-boot-export-dialog/spring-boot-export-dialog.component.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(dialog, /domain-manifest\.json/);
  assert.match(dialog, /semantica del dominio/i);
  assert.doesNotMatch(dialog, /formControlName="(?:domainManifest|manifestSchema|manifestAliases)"/);
});


test('configures CU-17 generated Angular UI with a user-selected primary color', () => {
  const dialog = readFileSync(
    new URL(
      '../dialogs/spring-boot-export-dialog/spring-boot-export-dialog.component.ts',
      import.meta.url,
    ),
    'utf8',
  );
  const model = readFileSync(
    new URL(
      './spring-boot-generation.model.ts',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(dialog, /formControlName="primaryColor"/);
  assert.match(dialog, /type="color"/);
  assert.match(dialog, /#2563EB/);
  assert.match(dialog, /frontend Angular/i);
  assert.match(dialog, /dashboard/i);
  assert.match(dialog, /componentes especificos/i);
  assert.match(model, /primaryColor:\s*string/);
});
