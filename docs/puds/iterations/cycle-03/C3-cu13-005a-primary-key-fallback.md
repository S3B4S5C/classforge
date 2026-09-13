# C3-cu13-005a — Opt-in first-attribute primary-key fallback

**Estado:** VALIDADO / CERRADO (cierre condicionado a gates locales del parche)

## Objetivo

Mejorar la UX previa a C3-cu13-006 sin debilitar CU-12: la generación continúa exigiendo identifiers explícitos por defecto, pero puede ofrecer una segunda acción cuando el único bloqueo es `CLASS_IDENTIFIER_REQUIRED` y todas las clases raíz afectadas tienen al menos un atributo.

## Contrato

1. El primer intento siempre usa el `UmlModel` canónico sin inferencias.
2. Si existe cualquier diagnóstico distinto de `CLASS_IDENTIFIER_REQUIRED`, no se ofrece fallback.
3. Si alguna clase afectada no tiene atributos, no se ofrece fallback.
4. El backend devuelve `PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED` y las parejas clase/primer atributo propuestas.
5. Solo una confirmación explícita reintenta con `useFirstAttributeAsIdentifier=true`.
6. El retry crea una copia efímera del UML, marca el primer atributo de cada clase raíz afectada como identifier no-null y vuelve a pasar por CU-12.
7. El proyecto, documento UML y revisión canónicos permanecen sin cambios.
8. Cualquier error restante vuelve a fallar cerrado y no produce ZIP parcial.

## UX

Estado recuperable:

- título: `Faltan claves primarias en el modelo UML`;
- explica que se puede corregir el diagrama o continuar solo para esa exportación;
- enumera `Clase: se usará “atributo” como clave primaria`;
- CTA: `Continuar usando el primer atributo como clave primaria`;
- aclara que el UML no se modifica.

Estado no recuperable:

- título: `El modelo UML no está completamente listo`;
- muestra observaciones breves sobre identifiers, tipos o relaciones;
- no ofrece CTA de fallback.

## Gates esperados

- backend `SpringBootGenerationServiceTests`;
- backend `SpringBootGenerationControllerTests`;
- regresión `com.classforge.generation.*`;
- frontend `npm run test:generation`;
- frontend `npm run build`;
- `git diff --check`.

C3-cu13-006 permanece como siguiente incremento y es responsable del hardening de `GeneratedProject`, acceptance compilable/H2 y cierre global de CU-13.


## Hardening de build detectado por export manual

Una ejecucion manual de un proyecto exportado revelo dos condiciones distintas:

- el target Java 21 funciona cuando Gradle detecta un JDK 21 local; esto es un requisito de toolchain, no un error de generacion;
- el `build.gradle` generado necesitaba importar explicitamente `spring-boot-dependencies:4.0.8` como Gradle platform para gobernar las versiones de starters, H2 y PostgreSQL.

El renderer queda protegido por un test focal que exige el BOM de Spring Boot 4.0.8. El acceptance compilable completo sigue perteneciendo a C3-cu13-006.
