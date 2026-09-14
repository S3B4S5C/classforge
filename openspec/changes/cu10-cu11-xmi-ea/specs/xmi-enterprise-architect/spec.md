# XMI / Enterprise Architect requirements

## Requirement: canonical authority
XMI import and export SHALL project to/from `ProjectDocument`; no independent XMI domain SHALL become authoritative.

## Requirement: supported subset
The adapter SHALL support Package, Class, Property, DataType, Association, Aggregation, Composition, Generalization and Multiplicity sufficient for the ClassForge canonical class model.

## Requirement: safe import
Import SHALL reject malformed XML, non-XMI-2.1 version declarations, DTDs/external entities, oversized payloads and models that fail canonical validation.

## Requirement: preview before mutation
Import planning SHALL NOT mutate the project. Apply SHALL require a short-lived one-shot token bound to user, project and base revision.

## Requirement: deterministic identity
Repeated import of the same external `xmi:id` SHALL yield the same UUID. XMI exported from ClassForge SHALL encode canonical UUIDs so reimport recovers them.

## Requirement: deterministic export
The same project document and project metadata SHALL produce byte-identical XMI output.

## Requirement: honest compatibility boundary
The adapter SHALL NOT claim preservation of proprietary Enterprise Architect diagram/profile/tagged-value extensions that ClassForge cannot represent.
