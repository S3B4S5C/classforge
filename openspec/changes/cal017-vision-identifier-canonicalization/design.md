# Cal-017 - Vision identifier canonicalization

## Problem

Vision raw transcription may correctly contain natural-language Unicode
identifiers such as `Categoría`, `Préstamo`, and `añoPublicacion`, while
ProjectDocument intentionally accepts only code-compatible identifiers. The
previous benchmark compared the compiled semantic IR but did not prove that it
could resolve into a valid ProjectDocument preview.

## Boundary decision

VisionUmlProposal remains faithful to visual evidence. VisionProposalCompiler
canonicalizes only identifiers crossing into AssistantSemanticPlan.
ProjectDocumentValidator is unchanged. No VLM prompt restriction is introduced,

## Benchmark decision

ORIGINAL PLAN attempts must resolve to a canonical BATCH, preview, and pass
ProjectDocumentValidator before they can be Exact.
