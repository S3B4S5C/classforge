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
CU-31: CERRADO
CU-09: CERRADO
CU-12: CERRADO
CU-13: CERRADO
Siguiente candidato: CU-15
Corte: 13 de septiembre de 2026
```

El Ciclo 1 consolidó la arquitectura ejecutable base. El Ciclo 2 cerró colaboración entre cuentas reales (`CU-31`) y entrada visual segura al modelo canónico (`CU-09`). El Ciclo 3 cerró en Construcción con CU-12 (IR relacional), CU-13 (Spring Boot/JPA) y CU-14 (API CRUD expresiva Simple/Auth).

## Fuentes

- `current-status.md`: estado real del software al último corte.
- `use-cases.md`: especificación vigente y backlog de casos de uso.
- `cycles/`: objetivo, riesgos, resultado y cierre de cada Ciclo.
- `iterations/`: evidencia técnica granular de los incrementos.
- `history/`: planes y bitácoras superadas, conservadas como evidencia histórica.
- `../evidence/cu09/`: evidencia técnica y de aceptación de Imagen -> UML.
- `../evidence/cu13/`: evidencia técnica y de aceptación de generación Spring Boot/JPA.
- `../evidence/cu14/`: cierre y acceptance de CRUD simple / Sistema de Información con Auth.

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

CU-14 está CERRADO y con él se cierra el Ciclo 3. CU-15 queda como siguiente candidato para un nuevo ciclo.

## UML académico

Los diagramas académicos viven en `../uml/`. El catálogo debe reflejar el software implementado y puede elaborarse editorialmente para la entrega sin convertirse en una fuente de verdad alternativa.
