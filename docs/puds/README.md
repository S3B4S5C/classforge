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
Fase: Elaboración
Ciclo 1: CERRADO
Ciclo 2: CERRADO
CU-31: CERRADO
CU-09: CERRADO
Corte: 5 de septiembre de 2026
```

El Ciclo 1 consolidó la arquitectura ejecutable base. El Ciclo 2 cerró colaboración entre cuentas reales (`CU-31`) y entrada visual segura al modelo canónico (`CU-09`).

## Fuentes

- `current-status.md`: estado real del software al último corte.
- `use-cases.md`: especificación vigente y backlog de casos de uso.
- `cycles/`: objetivo, riesgos, resultado y cierre de cada Ciclo.
- `iterations/`: evidencia técnica granular de los incrementos.
- `history/`: planes y bitácoras superadas, conservadas como evidencia histórica.
- `../evidence/cu09/`: evidencia técnica y de aceptación de Imagen -> UML.

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

## UML académico

Los diagramas académicos viven en `../uml/`. El catálogo debe reflejar el software implementado y puede elaborarse editorialmente para la entrega sin convertirse en una fuente de verdad alternativa.
