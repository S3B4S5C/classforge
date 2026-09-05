# Cal-018 - Separate image to UML UI

## Decision

The conversational UML assistant and image analysis have distinct interaction
durations and responsibilities. The workspace mounts them as sibling cards:

```text
Inspector
Asistente UML
Imagen a UML
```

`ProjectAssistantPanelComponent` is limited to text and voice conversation.
`ProjectImageUmlPanelComponent` owns ephemeral image selection, preparation,
analysis, retry, and presentation state. Both reuse the existing API and
`ProjectWorkspaceStore`; Apply remains `store.applyAssistantCommand(command,
baseRevision)`.

## UX boundary

The normal UI hides runtime, VLM, evidence, warning, BATCH, and diagnostic
details. The image card has compact empty, selected, analyzing, ready, and
error states. No backend or API contract changes are required.
