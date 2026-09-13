# C3-cu13-001 - Spring generation model + planner

**Estado:** VALIDADO / CERRADO

## Objetivo

Transformar `RelationalModel` en un `SpringGenerationModel` inmutable y listo para renderizar, sin generar archivos.

## Implementado

- Paquetes `generation.spring.model`, `planning` y `validation`.
- Configuracion estricta para artifact y package, naming Java determinista y tipos Java cerrados.
- Entidades UML-class, IDs simples/compuestos, repositorios y constraints/indexes fisicos.
- FK directas, 1:1 por FK+unique, N:M sin entidad de join, metadata de aggregation/composition y herencia JOINED multinivel.
- Diagnosticos fail-closed para nombres, PK/FK, join table, herencia y colisiones.
- Orden determinista de entidades, campos, relaciones, constraints, indexes y repositorios.

## Verificacion

Pruebas focales cubren naming/config, planner y validator con modelos relacionales construidos directamente. El planner no importa `UmlModel`, multiplicidades ni layout.

## Final hardening evidence

- El validator exige exactamente un repositorio por entidad y rechaza tanto destinos duplicados como entidades sin repositorio.
- Una FK JOINED debe coincidir, en orden y aridad, con la PK del subclass y la PK del parent inmediato.
- Se verifican los ocho mappings Java, N:M compuesta, ID JOINED compuesta, relaciones hacia subclass, unique/index agrupados y FKs directas missing/ambiguous.
- El planner conserva la correspondencia posicional de FK compuestas y es determinista ante permutaciones de tables, columns, FKs, uniques, indexes y relations.
- Gates focales, CU-12, `generation.*`, compile limpio y backend completo terminaron GREEN.

## Fuera de alcance

FreeMarker, source Java, `GeneratedProject`, ZIP, filesystem, endpoint, frontend, revision/acceso de proyecto, seguridad y ejecucion de proyectos generados quedan para incrementos posteriores.
