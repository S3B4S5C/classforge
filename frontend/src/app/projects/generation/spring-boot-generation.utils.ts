export const SPRING_ARTIFACT_NAME_PATTERN =
  /^[a-z][a-z0-9-]{0,62}$/;

const SPRING_BASE_PACKAGE_PATTERN =
  /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/;

const JAVA_KEYWORDS = new Set([
  'abstract',
  'assert',
  'boolean',
  'break',
  'byte',
  'case',
  'catch',
  'char',
  'class',
  'const',
  'continue',
  'default',
  'do',
  'double',
  'else',
  'enum',
  'extends',
  'final',
  'finally',
  'float',
  'for',
  'goto',
  'if',
  'implements',
  'import',
  'instanceof',
  'int',
  'interface',
  'long',
  'native',
  'new',
  'package',
  'private',
  'protected',
  'public',
  'return',
  'short',
  'static',
  'strictfp',
  'super',
  'switch',
  'synchronized',
  'this',
  'throw',
  'throws',
  'transient',
  'try',
  'void',
  'volatile',
  'while',
  '_',
  'true',
  'false',
  'null',
  'exports',
  'module',
  'open',
  'opens',
  'provides',
  'record',
  'requires',
  'sealed',
  'to',
  'transitive',
  'uses',
  'var',
  'with',
  'yield',
  'permits',
  'non-sealed',
]);

export interface SpringBootGenerationErrorCopy {
  title: string;
  message: string;
  staleRevision: boolean;
  primaryKeyFallbackAvailable: boolean;
  observations: string[];
}

export function isValidSpringBasePackage(
  value: string,
): boolean {
  if (!SPRING_BASE_PACKAGE_PATTERN.test(value)) {
    return false;
  }

  return value
    .split('.')
    .every((segment) => !JAVA_KEYWORDS.has(segment));
}

export function defaultSpringArtifactName(
  projectName: string,
): string {
  const normalized = projectName
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

  let artifactName = normalized;

  if (!artifactName) {
    artifactName = 'classforge-app';
  } else if (!/^[a-z]/.test(artifactName)) {
    artifactName = `app-${artifactName}`;
  }

  artifactName = artifactName
    .slice(0, 63)
    .replace(/-+$/g, '');

  return SPRING_ARTIFACT_NAME_PATTERN.test(artifactName)
    ? artifactName
    : 'classforge-app';
}

export function defaultSpringBasePackage(
  artifactName: string,
): string {
  let segment = artifactName.replace(/-/g, '_');

  if (!/^[a-z]/.test(segment)) {
    segment = `app_${segment}`;
  }

  if (JAVA_KEYWORDS.has(segment)) {
    segment = `app_${segment}`;
  }

  const candidate = `com.example.${segment}`;

  return isValidSpringBasePackage(candidate)
    ? candidate
    : 'com.example.classforge_app';
}

export function extractSpringDownloadFileName(
  contentDisposition: string | null,
  fallback: string,
): string {
  if (!contentDisposition) {
    return fallback;
  }

  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(
    contentDisposition,
  )?.[1];

  if (encoded) {
    try {
      return decodeURIComponent(encoded);
    } catch {
      return fallback;
    }
  }

  const quoted = /filename="([^"]+)"/i.exec(
    contentDisposition,
  )?.[1];

  if (quoted) {
    return quoted;
  }

  const plain = /filename=([^;]+)/i.exec(
    contentDisposition,
  )?.[1]?.trim();

  return plain || fallback;
}

interface GenerationErrorShape {
  status: number;
  code: string | null;
  payload: {
    message?: string;
    diagnostics?: Array<{
      code?: string;
      path?: string | null;
    }>;
    primaryKeyFallbacks?: Array<{
      className?: string;
      attributeName?: string;
    }>;
  } | null;
}

function asGenerationError(
  error: unknown,
): GenerationErrorShape | null {
  if (
    !error
    || typeof error !== 'object'
    || !('status' in error)
    || typeof error.status !== 'number'
    || !('code' in error)
    || !(typeof error.code === 'string' || error.code === null)
    || !('payload' in error)
  ) {
    return null;
  }

  return error as GenerationErrorShape;
}

function relationalObservations(
  payload: GenerationErrorShape['payload'],
): string[] {
  const diagnostics = payload?.diagnostics ?? [];

  return diagnostics
    .map((diagnostic) => {
      switch (diagnostic.code) {
        case 'CLASS_IDENTIFIER_REQUIRED':
          return 'Hay una clase sin clave primaria definida.';
        case 'CUSTOM_TYPE_UNSUPPORTED':
          return 'Hay un atributo con un tipo personalizado que todavía no puede exportarse.';
        case 'MULTIPLE_INHERITANCE_UNSUPPORTED':
          return 'La exportación no admite herencia múltiple.';
        case 'SUBCLASS_IDENTIFIER_NOT_ALLOWED':
          return 'Una subclase define una clave propia aunque debe heredar la de su clase padre.';
        case 'REFLEXIVE_RELATIONSHIP_UNSUPPORTED':
          return 'Hay una relación reflexiva que necesita roles explícitos.';
        case 'UNSUPPORTED_MULTIPLICITY':
          return 'Hay una relación con una multiplicidad que todavía no puede transformarse.';
        case 'COMPOSITION_OWNER_MULTIPLICITY_INVALID':
          return 'Hay una composición cuya multiplicidad de propietario no es válida.';
        case 'RELATIONAL_TABLE_NAME_COLLISION':
          return 'Dos clases producirían el mismo nombre de tabla.';
        case 'RELATIONAL_COLUMN_NAME_COLLISION':
          return 'Dos atributos producirían el mismo nombre de columna.';
        case 'RELATIONSHIP_CLASS_NOT_FOUND':
          return 'Hay una relación que referencia una clase que ya no está disponible.';
        case 'GENERALIZATION_CYCLE':
          return 'La herencia contiene un ciclo y debe corregirse.';
        default:
          return diagnostic.path
            ? `Revisa la observación del modelo en ${diagnostic.path}.`
            : 'Revisa una observación pendiente del modelo UML.';
      }
    })
    .filter((value, index, values) =>
      values.indexOf(value) === index,
    );
}

function primaryKeyFallbackObservations(
  payload: GenerationErrorShape['payload'],
): string[] {
  const fallbacks = payload?.primaryKeyFallbacks ?? [];

  return fallbacks
    .filter((fallback) =>
      typeof fallback.className === 'string'
      && typeof fallback.attributeName === 'string',
    )
    .map((fallback) =>
      `${fallback.className}: se usará “${fallback.attributeName}” como clave primaria para esta exportación.`,
    );
}

export function springBootGenerationErrorCopy(
  error: unknown,
): SpringBootGenerationErrorCopy {
  const generationError = asGenerationError(error);

  if (!generationError) {
    return {
      title: 'No se pudo generar el backend',
      message:
        'Ocurrió un error inesperado. Intenta nuevamente.',
      staleRevision: false,
      primaryKeyFallbackAvailable: false,
      observations: [],
    };
  }

  if (generationError.status === 0) {
    return {
      title: 'Sin conexión con el backend',
      message:
        'Verifica la conexión con ClassForge e intenta nuevamente.',
      staleRevision: false,
      primaryKeyFallbackAvailable: false,
      observations: [],
    };
  }

  switch (generationError.code) {
    case 'STALE_PROJECT_REVISION':
      return {
        title: 'El proyecto cambió',
        message:
          'La revisión usada para generar ya no es la actual. Cierra este diálogo, espera a que el workspace termine de sincronizarse y vuelve a generar.',
        staleRevision: true,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };

    case 'INVALID_GENERATION_CONFIGURATION':
      return {
        title: 'Revisa la configuración',
        message:
          'El nombre del artefacto o el package base no cumplen el formato requerido.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };

    case 'PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED':
      return {
        title: 'Faltan claves primarias en el modelo UML',
        message:
          'Puedes corregir el diagrama o continuar esta exportación usando el primer atributo de cada clase afectada como clave primaria. Este ajuste solo se aplica al ZIP y no modifica tu UML.',
        staleRevision: false,
        primaryKeyFallbackAvailable: true,
        observations: primaryKeyFallbackObservations(
          generationError.payload,
        ),
      };

    case 'RELATIONAL_MAPPING_REJECTED':
      return {
        title: 'El modelo UML no está completamente listo',
        message:
          'Revisa las observaciones del modelo —identificadores, tipos y relaciones— antes de volver a generar.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: relationalObservations(
          generationError.payload,
        ),
      };

    case 'SPRING_MODEL_REJECTED':
      return {
        title: 'El modelo no puede convertirse a Spring Boot',
        message:
          'Revisa nombres de clases y atributos, identificadores y relaciones para evitar ambigüedades de Java/JPA.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };

    case 'PROJECT_NOT_FOUND':
      return {
        title: 'Proyecto no disponible',
        message:
          'El proyecto ya no está disponible o tu acceso cambió. Vuelve a la lista de proyectos y reintenta.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };

    case 'TEMPLATE_RENDER_FAILED':
    case 'GENERATED_PROJECT_INVALID':
    case 'ARCHIVE_FAILED':
      return {
        title: 'No se pudo preparar el ZIP',
        message:
          'ClassForge encontró un problema interno al construir el backend. Intenta nuevamente; si persiste, revisa los logs del backend.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };

    default:
      return {
        title: 'No se pudo generar el backend',
        message:
          generationError.payload?.message
          || 'Intenta nuevamente.',
        staleRevision: false,
        primaryKeyFallbackAvailable: false,
        observations: [],
      };
  }
}
