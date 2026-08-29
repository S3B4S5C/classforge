# C2-cu08-fix-009 — resolución semántica tolerante y benchmark del Assistant

**Estado:** EXPERIMENTAL / COMPARABLE  
**CU-31:** CERRADO  
**Ciclo 2:** ABIERTO  
**Siguiente CU:** CU-09

## Problema observado

Una instrucción simple:

```text
Crea una asociacion entre animal y mascota
```

fue interpretada por Gemma como `CREATE_RELATIONSHIP`, pero el plan llegó con `sourceClassName` y `targetClassName` nulos. El grounding actuó correctamente al no aplicar una acción incompleta, pero el usuario terminó viendo un error pese a que el significado lingüístico era inequívoco.

El objetivo de este fix experimental es separar comprensión lingüística de resolución determinista de referencias UML.

## Pipeline experimental

```text
texto
-> resolver tolerante de referencias existentes
-> llama.cpp recibe canonicalName como contexto
-> AssistantSemanticPlan raw
-> AssistantSemanticCompiler
-> grounding
-> normalizer
-> resolver UML / UUID
-> BATCH / preview
```

`AssistantEntityReferenceResolver` trabaja contra las clases reales del `ProjectDocument` y tolera:

- mayúsculas/minúsculas;
- acentos;
- transposiciones simples;
- una edición tipográfica en nombres suficientemente largos;
- variantes como `4nimal -> Animal`.

La resolución silenciosa exige un candidato suficientemente fuerte y dominante. Fragmentos ambiguos o demasiado cortos no se resuelven automáticamente.

## Creación vs referencia

La autocorrección se aplica a referencias a símbolos existentes, no a nombres nuevos.

```text
"Relaciona 4nimal con Mascota"
-> 4nimal referencia a Animal

"Crea la clase 4nimal"
-> el nombre nuevo permanece 4nimal
```

Esto evita convertir el resolver en un corrector ortográfico agresivo.

## Compilador semántico

`AssistantSemanticCompiler` canonicaliza referencias que el LLM haya escrito con typo y puede completar extremos omitidos cuando el texto contiene dos referencias existentes inequívocas.

Ejemplo:

```text
raw LLM:
CREATE_RELATIONSHIP
sourceClassName = null
targetClassName = null

texto:
"Crea una asociacion entre 4nimal y mascota"

resolver:
4nimal  -> Animal
mascota -> Mascota

compiled:
CREATE_RELATIONSHIP Animal -> Mascota
```

El LLM continúa siendo responsable de la intención UML y del tipo de relación. Java no inventa una relación que el modelo no haya solicitado.

## Benchmark reproducible

Se agrega:

```text
scripts/assistant-reliability.ps1
```

El benchmark no aplica cambios ni modifica `ProjectDocument`. Ejecuta repetidamente el planner de llama.cpp sobre un documento UML sintético y compara:

1. **Raw LLM exact:** el plan que produjo directamente el modelo;
2. **Final pipeline:** el mismo plan después de semantic compiler + grounding + normalización;
3. **Repaired by compiler:** intentos incorrectos en raw que quedaron correctos de forma determinista.

Ejemplo:

```powershell
pwsh -NoProfile -File .\scripts\assistant-reliability.ps1 `
  -Attempts 50 `
  -Prompt "Crea una asociacion entre 4nimal y mascota" `
  -Classes Animal,Mascota `
  -ExpectedSource Animal `
  -ExpectedTarget Mascota
```

La salida resume tasas y motivos como `MISSING_SOURCE`, `MISSING_TARGET`, `WRONG_SOURCE`, `WRONG_TARGET` o errores de pipeline.

`-VerboseAttempts` imprime raw y compiled para cada intento. `-FailOnFinalError` devuelve exit code distinto de cero si al menos un intento final falla, útil para automatización.

## Alcance

Este experimento no cambia Apply, Command Bus, STOMP, revisión, persistencia ni el contrato canónico de `ProjectDocument`.

Tampoco sustituye el LLM por regex. La resolución tipográfica únicamente enlaza referencias contra símbolos que Java ya conoce.
