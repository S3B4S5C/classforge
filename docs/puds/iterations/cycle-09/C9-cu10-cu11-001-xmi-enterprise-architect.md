# C9-cu10-cu11-001 — XMI 2.1 / Enterprise Architect

Implementación conjunta de CU-10 y CU-11.

## Incremento

- parser XMI 2.1 seguro y fail-closed;
- mapeo `Package/Class/Property/DataType/Association/Generalization`;
- agregación/composición y multiplicidades;
- UUID determinista para XMI externo;
- IDs ClassForge recuperables en round-trip;
- preview token de 10 minutos, one-shot y ligado a revisión;
- export XMI determinista;
- diálogo Angular de import/export;
- fixture EA-shaped y acceptance HTTP real;
- documentación normativa y evidencia.

## Criterios de aceptación

1. fixture XMI 2.1 importa a `ProjectDocument` válido;
2. XXE/DTD externo se rechaza;
3. export repetido desde el mismo documento es byte-idéntico;
4. export -> import conserva clases, atributos, tipos, IDs y relaciones semánticas;
5. preview no muta;
6. apply incrementa revisión;
7. stale preview falla con 409;
8. token consumido no puede reutilizarse;
9. Angular compila;
10. backend completo y regresiones principales quedan verdes.

## Riesgo residual explícito

Enterprise Architect añade extensiones propietarias para diagramas y metadatos avanzados. CU-10/11 soportan deliberadamente el subconjunto UML canónico de ClassForge; no se afirma round-trip total de toda metadata propietaria de EA. El smoke manual con la instalación usada en el examen se ejecutará como parte de CU-27.
