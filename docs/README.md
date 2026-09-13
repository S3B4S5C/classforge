# Documentación de ClassForge

Esta carpeta contiene las fuentes Markdown que alimentarán la documentación oficial de presentación.

Los `.md` son documentos versionables y constituyen la fuente de verdad. El Word final debe construirse editorialmente a partir de estas fuentes; no debe convertirse en una segunda fuente normativa.

## Convención documental

| Carpeta | Pregunta que responde | Uso posterior en Word |
|---|---|---|
| `product/` | ¿Qué producto se pretende construir? | Introducción, visión, alcance, requisitos |
| `puds/` | ¿Cómo se desarrolló y en qué estado está? | PUDS, fases, ciclos, CU, trazabilidad |
| `architecture/` | ¿Cómo está construido y por qué? | Arquitectura, diseño, decisiones |
| `evidence/` | ¿Qué pruebas y resultados respaldan las decisiones? | Validación, benchmarks, cierre |
| `uml/` | ¿Qué modelos UML académicos representan el sistema? | Diagramas y explicación UML |
| `runtime/` | ¿Cómo se instala, ejecuta y demuestra? | Transición, instalación, tutorial, anexos |

## Jerarquía de autoridad

1. `puds/current-status.md` decide qué está implementado ahora.
2. `puds/use-cases.md` contiene la especificación vigente de los CU.
3. `puds/cycles/` explica objetivos, riesgos y cierre de cada Ciclo.
4. `puds/iterations/` conserva la evidencia técnica por incremento.
5. `architecture/` explica las decisiones técnicas vigentes.
6. `evidence/` consolida resultados medidos y decisiones de aceptación.
7. `product/product.md` describe el producto objetivo, incluyendo capacidades futuras.
8. `puds/history/` conserva planes superados sin gobernar el presente.

## Corte actual

```text
Fase PUDS: Construcción
Ciclo 1: CERRADO
Ciclo 2: CERRADO
Ciclo 3: CERRADO
Ciclo 4: CERRADO
CU-31: CERRADO
CU-09: CERRADO
CU-12: CERRADO
CU-13: CERRADO
CU-14: CERRADO
CU-15: CERRADO
Incremento actual: C4-cu15-000
Fecha: 13-09-2026
```

## CU-09 como fuente para el Word final

Para explicar Imagen -> UML en la documentación presentable, usar en este orden:

1. `puds/use-cases.md` — definición normativa del caso de uso.
2. `evidence/cu09/cu09-closure-report.md` — narrativa completa de desarrollo, decisiones y validación.
3. `architecture/vision-input-pipeline.md` — flujo técnico vigente desde imagen hasta `ProjectDocument`.
4. `evidence/cu09/vision-model-selection.md` — selección Qwen3-VL-4B.
5. `evidence/cu09/cv-first-class-regions.md` — Cal-011.
6. `evidence/cu09/hybrid-cv-geometry.md` — Cal-010..017 y benchmark focal.
7. `evidence/cu09/cu09-acceptance.json` — evidencia estructurada del cierre.
8. `puds/cycles/cycle-02-elaboration.md` — contexto y cierre del Ciclo 2.

## CU-13 como fuente para el Word final

Para explicar UML -> relacional -> Spring Boot/JPA y el cierre de CU-13, usar en este orden:

1. `puds/use-cases.md` — definición normativa de CU-12/CU-13.
2. `evidence/cu13/cu13-closure-report.md` — resumen de alcance, gates y decisión de cierre.
3. `architecture/relational-model.md` — contrato de la IR relacional cerrada por CU-12.
4. `architecture/spring-boot-generation.md` — arquitectura vigente del generador/export.
5. `evidence/cu13/cu13-acceptance.json` — evidencia máquina-legible de determinismo y proyectos generados.
6. `puds/cycles/cycle-03-construction.md` — cierre del Ciclo 3 con CU-12, CU-13 y CU-14 aceptados.
7. `puds/cycles/cycle-04-api-contract.md` — Ciclo 4 abierto para CU-15 OpenAPI/Postman.


## Cierre normativo de CU-14

CU-14 conserva dos modos de generación explícitos y mutuamente excluyentes:

1. **CRUD simple:** genera la API CRUD expresiva sin Spring Security, login, JWT ni tratamiento especial de credenciales.
2. **Sistema de Información con Auth:** genera la misma API más autenticación. Antes de exportar, el usuario debe seleccionar la **tabla/entidad de autenticación**, el **atributo de usuario/login** y el **atributo de contraseña** pertenecientes a esa misma tabla/entidad.

La definición detallada está en `puds/use-cases.md`, `product/product.md` y `puds/iterations/cycle-03/C3-cu14-000-crud-auth-scope.md`.

## CU-15 cerrado — OpenAPI y Postman

CU-15 está CERRADO dentro del Ciclo 4. El contrato normativo y su cierre se concentran en:

1. `puds/use-cases.md` — comportamiento del CU.
2. `puds/cycles/cycle-04-api-contract.md` — objetivo, riesgos y salida del Ciclo 4.
3. `puds/iterations/cycle-04/C4-cu15-000-openapi-postman-scope.md` — decisiones cerradas antes de implementar.
4. `puds/iterations/cycle-04/C4-cu15-001-openapi-postman-generation.md` — incremento de implementación/cierre.
5. `architecture/openapi-postman-generation.md` — arquitectura implementada.
6. `evidence/cu15/` — acceptance y cierre.
7. `../openspec/changes/cu15-openapi-postman/` — requisitos y tareas cerradas.

CU-15 no genera Domain Manifest ni cliente TypeScript; esos consumidores pertenecen a CU-16/CU-17.

## Composición prevista del Word

1. Portada e identificación.
2. Resumen ejecutivo.
3. Problema, visión y objetivos.
4. Alcance y requisitos.
5. Proceso Unificado de Desarrollo de Software.
6. Casos de uso y trazabilidad.
7. Arquitectura de ClassForge.
8. Modelos y diagramas UML.
9. Implementación por ciclos.
10. Pruebas, benchmarks y evidencias.
11. Instalación, ejecución y demostración.
12. Limitaciones y trabajo pendiente.
13. Conclusiones.
14. Bibliografía.
15. Anexos y tutorial.

Los detalles técnicos se mantienen primero en los Markdown especializados y luego se condensan editorialmente para la presentación.
