# CU-19 Generated application Assistant — design

CU-19 reuses the proven ClassForge local-assistant architecture in generated applications without coupling exported projects to ClassForge runtime classes.

Generated Spring owns Whisper transcription, llama.cpp/Qwen native tool calling, Domain Manifest grounding, validation and execution. Angular and Flutter only call generated Spring endpoints. Reads execute immediately; mutations return sanitized previews and opaque pending tokens consumed by apply. CU-20..23 are absorbed as CREATE/relations/UPDATE/DELETE intents of CU-19.
