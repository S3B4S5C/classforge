# PUDS — ClassForge

Evidencia del **Proceso Unificado de Desarrollo de Software** aplicado a ClassForge.

## Convención

En este proyecto utilizamos la palabra **Ciclo** como nombre operativo de una **iteración PUDS**.

No significa un ciclo de vida completo. Las fases PUDS continúan siendo:

```text
Inicio -> Elaboración -> Construcción -> Transición
```

Una fase puede contener uno o varios ciclos.

## Estado vigente

```text
Fase: Elaboración
Ciclo 1: CERRADO
Ciclo 2: NO ABIERTO
Corte: 28 de agosto de 2026
```

El Ciclo 1 consolida un incremento ejecutable con CU-01 a CU-08, autenticación/ownership y los runtimes locales necesarios para CU-08.

## Fuentes

- `current-status.md`: estado real del software al último corte.
- `use-cases.md`: especificación vigente y backlog de casos de uso.
- `cycles/`: objetivos, riesgos, resultado y cierre de cada Ciclo.
- `iterations/`: evidencia técnica granular producida dentro de cada Ciclo.
- `history/`: planes y bitácoras antiguas conservadas como evidencia.

## Regla de trazabilidad

```text
Necesidad
  -> requisito
  -> caso de uso
  -> Ciclo
  -> incremento técnico
  -> arquitectura/UML
  -> código
  -> prueba
  -> evidencia
```

## UML

Los diagramas académicos viven en `../uml/`.

El catálogo indica qué diagramas son necesarios y cuál es su estado; no se debe declarar un diagrama como terminado hasta que represente el software realmente implementado.
