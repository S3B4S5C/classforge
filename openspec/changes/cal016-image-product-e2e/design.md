# Cal-016 - Image product E2E

## Context

The previous image acceptance E2E verified:

```text
image plan -> command executor -> ProjectService.saveDocument -> reopen
```

That verified command semantics and persistence, but bypassed the production
Apply authority.

## Decision

CU-09 image E2Es apply the exact BATCH returned in
`AssistantImagePlanResponse.command` through the canonical route:

```text
frontend Command Bus -> ProjectOperation -> ProjectCollaborationService.apply
  -> ProjectCommandExecutor -> validation -> persistence
```

No ImageApply endpoint or Vision Command Bus is created. `AssistantImagePlanService`
