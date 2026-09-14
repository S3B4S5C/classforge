# Ciclo 8 — Construcción — Assistant de datos generado

**Fase:** Construcción
**Estado:** CERRADO
**Caso:** CU-19; absorbe CU-20, CU-21, CU-22 y CU-23.

## Objetivo

Cerrar en una sola capacidad las consultas y mutaciones mediante chat/voz de la aplicación generada, reutilizando Whisper/Qwen locales y el patrón native-tools + grounding + preview/apply ya validado en ClassForge.

## Decisiones

- Spring generado concentra STT, LLM, grounding y ejecución;
- Angular/Flutter son clientes del mismo Assistant;
- mismas instancias locales `:8092` y `:8093` por defecto;
- Domain Manifest es la autoridad semántica;
- CU-20..23 se absorben en intents de CU-19;
- reads directos; mutations con preview token opaco + apply;
- password y valores sensibles nunca se devuelven como comando crudo;
- no se inventan operaciones de negocio fuera de CRUD/relaciones declaradas.

## Criterios de salida

- [x] chat y voz Angular/Flutter;
- [x] Whisper -> mismo planner de texto;
- [x] Qwen native tool calling;
- [x] QUERY/COUNT/GET/CREATE/UPDATE/DELETE;
- [x] SET/ADD/REMOVE relation;
- [x] grounding y tipos fail-closed;
- [x] previews sanitizados y apply explícito;
- [x] Simple/Auth;
- [x] determinismo;
- [x] build de backends generados;
- [x] regresiones CU-13..18;
- [x] documentación/evidencia.

## Resultado

CU-19 y Ciclo 8 quedan CERRADOS. CU-20..23 quedan ABSORBIDOS POR CU-19. El siguiente candidato funcional es CU-26 — registrar cambios del proyecto; CU-24/25 son infraestructura ya reutilizada.
