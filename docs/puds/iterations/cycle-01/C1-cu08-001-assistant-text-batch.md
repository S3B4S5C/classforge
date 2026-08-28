# C1 — CU08-001 Assistant texto + BATCH

**Ciclo:** 1  
**Caso:** CU-08  
**Incremento:** 001

## Objetivo

Convertir lenguaje natural escrito en una propuesta UML segura sin permitir escritura directa del LLM.

## Pipeline

```text
texto
 -> llama.cpp / Gemma
 -> AssistantSemanticPlan
 -> grounding
 -> normalización
 -> resolver Java
 -> BATCH UmlCommand
 -> preview
 -> validación
 -> Aplicar
```

## Decisiones

- LLM no genera UUID;
- Java resuelve nombres e identidad;
- un pedido puede producir varios comandos;
- BATCH es atómico;
- una frase aplicada produce una sola operación/revisión colaborativa;
- preview no persiste;
- `baseRevision` protege el plan.
