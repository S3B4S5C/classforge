# Ciclo 9 — CU-10 + CU-11 XMI / Enterprise Architect

**Fase:** Construcción
**Estado:** CERRADO al superar `xmiEnterpriseArchitectAcceptance` y regresiones.

## Objetivo

Cerrar la interoperabilidad bidireccional entre el modelo canónico de ClassForge y XMI 2.1 compatible con el subconjunto de clases usado por Enterprise Architect.

## Casos de uso

- CU-10 — Importar XMI de Enterprise Architect.
- CU-11 — Exportar XMI para Enterprise Architect.

Ambos se implementan en el mismo ciclo porque comparten identidad, tipos, asociaciones, multiplicidades, fixtures y round-trip.

## Decisiones

- XMI 2.1, no Native XML/XEA.
- parser propio sobre JAXP/DOM seguro; no se incorpora Eclipse UML2.
- `ProjectDocument` continúa siendo la única autoridad canónica.
- Package se acepta y se aplana.
- importación con preview + apply y `baseRevision`.
- exportación read-only y determinista.
- UUIDs ClassForge se preservan al exportar/reimportar.
- XXE/DTD externos se rechazan.
- elementos no representables quedan fuera del subconjunto y se diagnostican.
- smoke visual/manual dentro de EA se integra en CU-27 de Transición.

## Salida

- endpoints `/api/projects/{id}/xmi/import/preview`, `/import/apply`, `/export`;
- UI `XMI / Enterprise Architect` en el workspace;
- fixture XMI 2.1 estilo EA;
- import/export/round-trip y HTTP acceptance;
- documentación de arquitectura, OpenSpec y evidencia;
- CU-26 queda `DESCARTADO / FUERA DE ALCANCE` por decisión de producto;
- siguiente caso funcional: CU-27 demo reproducible.
