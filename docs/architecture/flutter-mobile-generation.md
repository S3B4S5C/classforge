# Generación Flutter mobile — CU-18

**Estado:** implementado y aceptado en Ciclo 7.

CU-18 genera un proyecto Flutter independiente dentro de `mobile/`. No empaqueta el Angular de CU-17 ni usa Capacitor.

## Fuentes canónicas

```text
SpringGenerationModel + DomainManifestPlan + SpringApiContract
                      |
                      v
               FlutterMobileRenderer
                      |
                      v
                  mobile/
```

El renderer reutiliza la misma semántica, endpoints, IDs, relaciones, modo Simple/Auth y color primario ya fijados por CU-14..17.

## Estructura

Cada entidad genera archivos específicos:

```text
mobile/lib/entities/<entity>/
  <entity>_model.dart
  <entity>_api.dart
  <entity>_list_page.dart
  <entity>_detail_page.dart
  <entity>_form_page.dart
```

También se generan dashboard, API client, theme, reference picker y scaffold Android.

## Modos

### SIMPLE_CRUD

CRUD/query/relaciones sin Auth.

### AUTH_INFORMATION_SYSTEM

Añade login, bootstrap, Bearer JWT, `flutter_secure_storage`, auth gate y manejo global de 401.

Password se trata como secreto y usa controles `obscureText`.

## Relaciones

- to-one: selector simple;
- many-to-many / one-to-many: selector múltiple con chips;
- IDs de relaciones y PK explícitas se conservan en requests;
- PK simples autogenerables (`UUID`, `Integer`, `Long`, `String`) se omiten de creación/bootstrap y no se solicitan en formularios, salvo una PK `String` seleccionada explícitamente como credencial Auth.

## Tema

CU-18 reutiliza el mismo `primaryColor #RRGGBB` de CU-17 y lo convierte a `Color(0xFFRRGGBB)` / `ColorScheme.fromSeed`.

## Android

Android es la plataforma formalmente aceptada. La app usa `10.0.2.2:8080` como backend por defecto en emulador Android.

iOS, desktop y Flutter Web quedan fuera del cierre formal de CU-18.

## Acceptance

`generatedFlutterAcceptance` genera Simple/Auth y ejecuta:

```text
flutter pub get
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter test
flutter build apk --debug
```

Luego se ejecutan regresiones CU-13..17 y gates globales de ClassForge.
