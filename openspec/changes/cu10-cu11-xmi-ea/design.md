# CU-10 / CU-11 XMI Enterprise Architect — design

ClassForge adds a bidirectional XMI 2.1 adapter around the canonical `ProjectDocument`. Import parses an EA-compatible UML subset into a validated proposal and requires revision-bound preview/apply. Export serializes the same subset deterministically and encodes ClassForge UUIDs into recoverable XMI IDs.

The adapter intentionally ignores proprietary diagram metadata and unsupported UML families rather than creating a second domain model. XML parsing is hardened against DTD/XXE. Package hierarchy is flattened because the canonical model has no package concept.
