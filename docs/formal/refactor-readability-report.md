# Refactor final de legibilidad — Functional Freeze

**Corte:** 14 de septiembre de 2026.  
**Objetivo:** reducir deuda de concentración y mejorar la navegabilidad para documentación/defensa sin modificar funcionalidades ya cerradas.

## Regla de congelamiento

Durante este refactor se mantuvieron congelados endpoints, contratos JSON, operationIds, Domain Manifest, formatos XMI, comandos UML, códigos/mensajes de validación, generated output, UX deliberada, esquema persistente, heurísticas Vision, prompts y parámetros IA. Los cambios se limitaron a extracción de responsabilidades, movimiento interno, documentación, tests de caracterización e higiene del entregable.

## Ejecución del plan en 10 pasos

| Paso | Resultado |
|---|---|
| 1. Documentación | catálogo UML actualizado al corte final; referencias vigentes a scripts corregidas; historia de producto separada de la especificación consolidada; heading de presencia actualizado; residuos runtime retirados; `.gitattributes` añadido. |
| 2. Mapa de código | creados `code-map.md` y `traceability-matrix.md`. |
| 3. Generadores | Angular, Flutter y Assistant generado convertidos a fachadas pequeñas con renderers por responsabilidad. |
| 4. `GeneratedProjectValidator` | convertido a fachada con validadores de skeleton, Java, texto y artefactos/API. |
| 5. Workspace frontend | `ProjectWorkspaceStore` conserva su API; historial/documento autoritativo y tracking realtime se extrajeron a colaboradores puros y caracterizados. El HTML/SCSS se mantiene intacto para no alterar encapsulación/cascada de estilos. |
| 6. Command executor frontend | dispatcher pequeño + ejecutores por clases, atributos, relaciones y layout. |
| 7. `ProjectDocumentValidator` | fachada + validadores por clases, relaciones, generalización, layout y calidad. |
| 8. `ProjectCommandExecutor` | fachada + handlers por clases, atributos, relaciones y layout. |
| 9. Assistant | evaluado. Se refactorizó únicamente el renderer de la aplicación generada en el paso 3. Los resolvers semánticos de ClassForge se conservaron byte a byte por ser reglas sensibles ya cerradas y bien cubiertas. |
| 10. Vision | no se modificó ningún archivo del pipeline Vision. El árbol `assistant/vision` conserva exactamente el mismo SHA-256 que el baseline (`04c28f3e6521ac204295ac16307e5e7549bb10050a00cf7590795390c75ef8d4`). |

## Reducción de hotspots (fachadas)

| Archivo público/fachada | Antes | Después |
|---|---:|---:|
| `AngularFrontendRenderer.java` | 1755 líneas | 73 líneas |
| `FlutterMobileRenderer.java` | 1328 | 80 |
| `GeneratedAssistantRenderer.java` | 1014 | 40 |
| `GeneratedProjectValidator.java` | 876 | 73 |
| `uml-command-executor.ts` | 957 | 74 |
| `ProjectDocumentValidator.java` | 1035 | 61 |
| `ProjectCommandExecutor.java` | 1001 | 56 |
| `project-workspace.store.ts` | 1642 | ~1540, con estado documental/realtime extraído |

El objetivo no fue minimizar líneas totales —las reglas siguen existiendo— sino transformar puntos de entrada monolíticos en índices legibles hacia colaboradores con una responsabilidad clara.

## Characterization tests frontend

`npm run test:characterization` ejecuta tres grupos (25 tests en total) sin requerir un runner adicional:

- 15 tests existentes del flujo de generación frontend;
- 5 tests de `UmlCommandExecutor` / `UmlCommandInverter` / `UmlCommandBus` (incluye contratos de error de handlers extraídos);
- 5 tests del estado extraído del workspace (incluyen revisión stale/gap y resync de reconexión).

Invariantes cubiertas: no mutación del documento de entrada, inversas create/rename/move, dirty/undo/redo, atomicidad observable de BATCH, separación historial local/revisión confirmada, instalación de estado autoritativo, pending operations, buffering y generaciones de resync.

## Evidencia local de no cambio

Para Domain Manifests representativos de **Auth** y **CRUD simple** se compilaron y ejecutaron los renderers originales y refactorizados. El stream ordenado `path + contenido` de todos los archivos generados produjo hashes idénticos. En Auth:

```text
Angular   c09767ea136022377d48fe1351ee818e230947492ac1622855b2d508ebef189f
Flutter   0aefee358d814a7fbb7b830a467c364452db17142f9c12dae37357a6d1ff3a40
Assistant 7a964b66e5833526efee91319127bc88c3d965bc16ca15e318e532ccbd54d7f0
```

En CRUD simple:

```text
Angular   bb6a8caabbb03f004ca265a7d7c08508336e8f5281c96dae5f9c3110eca50e4b
Flutter   9b1e5de2173b2386ccb88f944c7a272782e756f9f6805f9badedea1f1ec5a901
Assistant 7a964b66e5833526efee91319127bc88c3d965bc16ca15e318e532ccbd54d7f0
```

También se realizó compilación focal con `javac` de las extracciones Java (generadores, `GeneratedProjectValidator`, validación de documento y command executor) usando únicamente stubs mínimos para dependencias de framework cuando fue necesario. Esto valida sintaxis, visibilidad y dependencias internas de las clases extraídas.

Para `ProjectCommandExecutor` + `ProjectDocumentValidator`, un escenario determinista se ejecutó contra baseline y refactor; el digest de documento resultante, rechazo y diagnósticos fue idéntico: `e8e0ff44dce926dfdd9d29927448b6be6b5816eb6061eebc5b51de1df62156d2`. Además, los 59 archivos TypeScript del frontend pasan el parse/syntax check nativo de Node 22.

## Correcciones posteriores a la validación final del propietario

La primera ejecución integral sobre Windows detectó dos derivas puntuales que los gates focales previos no habían expuesto:

- `GeneratedProjectValidator.validPath()` había perdido durante la extracción el rechazo de rutas con prefijo de unidad Windows (`C:`) y el `split("/", -1)` que conserva segmentos vacíos. Se restauró el cuerpo exacto del baseline y se añadió cobertura explícita de ruta terminada en `/`.
- Tres handlers TypeScript extraídos utilizaban `UmlCommandError` sin importarlo. Se restauraron los imports y el characterization test ahora ejecuta ramas de `DUPLICATE_CLASS_ID`, `DUPLICATE_ATTRIBUTE_ID` y `RELATIONSHIP_NOT_FOUND`, evitando que este tipo de error quede oculto hasta `ng build`.

Los acceptances de Spring/API/Manifest/Angular generado/Flutter/Assistant ya habían pasado en la ejecución del propietario; estas correcciones no alteran generated output ni contratos funcionales.

## Decisiones conservadoras

`ProjectWorkspaceStore` sigue siendo una fachada amplia porque concentra coordinación de UI, APIs y realtime. Se extrajo el estado puro que más dificultaba razonar sobre revisiones e historial, pero no se convirtió el template gigante en nuevos componentes: mover el HTML habría obligado a redistribuir estilos scoped de Angular y aumentaba el riesgo de alterar la UI sin aportar una garantía funcional equivalente.

Tampoco se dividieron `UmlToolCallResolver`, `AssistantEntityReferenceResolver`, `UmlAssistantCommandResolver` ni los analizadores Vision. En esos módulos el valor de una reducción adicional de líneas es menor que el riesgo de modificar grounding, heurísticas o resolución semántica en un producto declarado terminado.

## Gates finales que debe ejecutar el propietario

Este entorno no dispone del cache/red necesarios para ejecutar de punta a punta Gradle/Angular/Flutter. Antes de congelar el commit final se deben repetir los gates que ya forman parte del proyecto:

```powershell
cd backend
.\gradlew.bat test --no-daemon
.\gradlew.bat springCrudGenerationAcceptance springApiArtifactsAcceptance domainManifestAcceptance generatedAngularAcceptance generatedFlutterAcceptance generatedAssistantAcceptance --no-daemon -PflutterCommand="F:\\FlutterSDK\\flutter\\bin\\flutter.bat"
```

Y en frontend:

```powershell
cd frontend
npm ci
npm run test:characterization
npm run build
```

Finalmente ejecutar el smoke/runbook de CU-27 y los acceptances XMI sobre el hardware de defensa.

## Estado documental posterior al refactor

La documentación fuente queda preparada para convertirse en documentación académica formal. La autoridad de estado continúa siendo `docs/puds/current-status.md`; `docs/formal/traceability-matrix.md` y `docs/formal/code-map.md` son mapas de lectura, no nuevas fuentes normativas.
