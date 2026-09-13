# PUDS — ClassForge

Evidencia del **Proceso Unificado de Desarrollo de Software** aplicado a ClassForge.

## Convención

En este proyecto utilizamos **Ciclo** como nombre operativo de una **iteración PUDS**.

Las fases generales continúan siendo:

```text
Inicio -> Elaboración -> Construcción -> Transición
```

## Estado vigente

```text
Fase: Construcción
Ciclo 1: CERRADO
Ciclo 2: CERRADO
Ciclo 3: CERRADO
Ciclo 4: CERRADO
Ciclo 5: CERRADO
Ciclo 6: CERRADO
Ciclo 7: CERRADO
CU-31: CERRADO
CU-09: CERRADO
CU-12: CERRADO
CU-13: CERRADO
CU-14: CERRADO
CU-15: CERRADO
CU-16: CERRADO
CU-17: CERRADO
CU-18: CERRADO
Corte: 13 de septiembre de 2026
```

El Ciclo 1 consolidó la arquitectura ejecutable base. El Ciclo 2 cerró colaboración e imagen; el Ciclo 3 cerró CU-12/13/14; el Ciclo 4 cerró CU-15 (OpenAPI/Postman); el Ciclo 5 cerró CU-16 Domain Manifest; el Ciclo 6 cerró CU-17 Angular y el Ciclo 7 cerró CU-18 Flutter/Android.

## Fuentes

- `current-status.md`: estado real del software al último corte.
- `use-cases.md`: especificación vigente y backlog de casos de uso.
- `cycles/`: objetivo, riesgos, resultado y cierre de cada Ciclo.
- `iterations/`: evidencia técnica granular de los incrementos.
- `history/`: planes y bitácoras superadas, conservadas como evidencia histórica.
- `../evidence/cu09/`: evidencia técnica y de aceptación de Imagen -> UML.
- `../evidence/cu13/`: evidencia técnica y de aceptación de generación Spring Boot/JPA.
- `../evidence/cu14/`: cierre y acceptance de CRUD simple / Sistema de Información con Auth.
- `../evidence/cu15/`: cierre y acceptance de OpenAPI/Postman.
- `../evidence/cu16/`: cierre y acceptance de Domain Manifest.
- `../evidence/cu17/`: cierre y acceptance de Angular generado.
- `../evidence/cu18/`: cierre y acceptance de Flutter mobile/Android.

## Regla de trazabilidad

```text
Necesidad
  -> requisito
  -> caso de uso
  -> Ciclo
  -> incremento técnico
  -> arquitectura
  -> código
  -> prueba
  -> evidencia
  -> decisión de cierre
```

## Cierre de CU-09

Para CU-09, la fuente más completa de trazabilidad es:

`docs/evidence/cu09/cu09-closure-report.md`

Su copia estructurada de aceptación es:

`docs/evidence/cu09/cu09-acceptance.json`

La documentación registra también las validaciones diferidas; no convierte trabajo no ejecutado en evidencia.

## Cierre de CU-13

La narrativa de cierre y la matriz completa de gates están en:

`docs/evidence/cu13/cu13-closure-report.md`

La aceptación estructurada y el SHA-256 determinista están en:

`docs/evidence/cu13/cu13-acceptance.json`

CU-14 está CERRADO y con él se cerró el Ciclo 3. CU-15 está CERRADO y con él se cerró el Ciclo 4. CU-16 está CERRADO y con él se cerró el Ciclo 5; el preflight es `iterations/cycle-05/C5-cu16-000-domain-manifest-scope.md` y el incremento de cierre `iterations/cycle-05/C5-cu16-001-domain-manifest-generation.md`.

## UML académico

Los diagramas académicos viven en `../uml/`. El catálogo debe reflejar el software implementado y puede elaborarse editorialmente para la entrega sin convertirse en una fuente de verdad alternativa.

CU-17 está CERRADO en Ciclo 6: el ZIP generado incluye `frontend/` Angular standalone con dashboard, componentes específicos por entidad, Simple/Auth y color primario configurable. Evidencia: `evidence/cu17/`.


CU-18 está CERRADO en Ciclo 7: `mobile/` Flutter se genera desde Domain Manifest/API canónica, comparte color primario con Angular y valida Android mediante Flutter analyze/test/build APK.
