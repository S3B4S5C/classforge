# Generated Assistant requirements

## Requirement: shared text and voice planner
The generated application SHALL route text and Whisper transcripts through the same native-tool planner in generated Spring.

## Requirement: deterministic Java authority
The generated application SHALL validate LLM-proposed operations against generated domain metadata before execution and SHALL fail closed for unknown, ambiguous, incompatible or sensitive references.

## Requirement: mutation confirmation
CREATE, UPDATE, DELETE and relation mutations SHALL NOT execute during planning. Planning SHALL return only a sanitized summary and opaque short-lived token; apply SHALL consume that token.

## Requirement: runtime reuse
Default generated runtime URLs SHALL target local llama.cpp on port 8092 and whisper.cpp on port 8093 and SHALL be configurable without changing generated source.

## Requirement: client isolation
Angular and Flutter SHALL communicate with generated Spring and SHALL NOT call llama.cpp or whisper.cpp directly.
