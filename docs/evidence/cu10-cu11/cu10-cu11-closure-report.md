# CU-10 / CU-11 closure report

**Ciclo:** 9
**Alcance:** XMI 2.1 / Enterprise Architect — importación y exportación del subconjunto UML canónico.

## Evidencia ejecutable

`xmiEnterpriseArchitectAcceptance` cubre:

- fixture XMI 2.1 con forma de export EA;
- Package, Class, Property, PrimitiveType/DataType;
- Association, Aggregation, Composition, Generalization;
- multiplicidades `1`, `0..1`, `0..*`, `1..*`;
- `isID`, nullable y visibilidad;
- parser XML con DTD/XXE bloqueado;
- export determinista;
- round-trip semántico con UUIDs conservados;
- HTTP autenticado `preview -> apply -> export -> reimport`;
- `baseRevision` stale fail-closed;
- preview token one-shot.

El parche de cierre ejecuta además full backend, build Angular y regresiones de generación críticas. La compatibilidad con metadata propietaria de `xmi:Extension` no forma parte del alcance.

## Transición

CU-27 incorporará el smoke manual de abrir el XMI exportado por ClassForge en la instalación de Enterprise Architect usada para la demostración y el sentido inverso con un XMI exportado desde esa instalación.
