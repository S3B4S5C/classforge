# XMI 2.1 / Enterprise Architect

**Estado:** CU-10 y CU-11 implementados en Ciclo 9.

ClassForge intercambia el subconjunto UML representable por `ProjectDocument` mediante XMI 2.1 canónico orientado a interoperabilidad con Sparx Systems Enterprise Architect.

## Subconjunto soportado

- `uml:Package` / `packagedElement uml:Package` — se aplana al importar;
- `uml:Class`;
- `uml:Property` como atributo de clase;
- `uml:PrimitiveType` y `uml:DataType`;
- `uml:Association`;
- `aggregation="shared"` -> `AGGREGATION`;
- `aggregation="composite"` -> `COMPOSITION`;
- `generalization` -> `GENERALIZATION`;
- `lowerValue` / `upperValue`, incluido `*`;
- visibilidad de atributos;
- `isID=true` cuando el XMI lo declara.

No se declara soporte general para diagramas, operaciones UML, stereotypes, profiles, tagged values, association classes ni extensiones propietarias de EA. `xmi:Extension` puede existir y se ignora salvo que una futura iteración incorpore metadata concreta.

## Importación

```text
.xmi/.xml
  -> parser XML seguro
  -> EnterpriseArchitectXmiImporter
  -> ProjectDocument propuesto
  -> ProjectDocumentValidator
  -> preview token (10 min)
  -> confirmación
  -> ProjectService.saveDocument(baseRevision)
  -> revisión nueva
  -> reload/resync del workspace
```

Reglas:

1. máximo 5 MiB;
2. XMI 2.1 explícito cuando `xmi:version` está presente;
3. DTD y entidades externas están prohibidos (XXE fail-closed);
4. el preview no modifica el proyecto;
5. el token está ligado a usuario + proyecto + revisión;
6. una revisión obsoleta produce `PROJECT_REVISION_CONFLICT`;
7. el token se consume una sola vez;
8. paquetes anidados se aplanan porque `ProjectDocument` no modela paquetes;
9. elementos fuera del subconjunto se ignoran con diagnóstico visible cuando no impiden interpretar el modelo soportado.

### Identidad

Los IDs estándar de EA con forma `EAID_<32 hex>` / `EAPK_<32 hex>` se convierten directamente en UUID. Para IDs XMI arbitrarios se usa UUID v3 determinista sobre el `xmi:id`, de modo que importar el mismo archivo produce las mismas identidades.

## Exportación

```text
ProjectDocument validado
  -> EnterpriseArchitectXmiExporter
  -> XMI 2.1 UTF-8 determinista
  -> descarga *.xmi
```

El archivo contiene un `uml:Model`, un `uml:Package`, tipos, clases, propiedades, generalizaciones y asociaciones. Los UUID canónicos se serializan como `EAID_<UUID sin guiones>` para que una reimportación ClassForge recupere exactamente los IDs originales.

La exportación no incluye `DiagramLayout`: XMI estándar conserva la semántica UML y la disposición visual de JointJS sigue siendo una proyección local de ClassForge.

## Round-trip

El acceptance exige:

```text
EA-shaped XMI 2.1 fixture
  -> ClassForge
  -> ProjectDocument válido

ProjectDocument
  -> XMI 2.1
  -> ProjectDocument
  -> misma semántica e IDs
```

También recorre HTTP real `preview -> apply -> export -> re-preview -> re-apply` y comprueba protección por revisión y token one-shot.

La verificación manual abriendo el XMI exportado en una instalación concreta de Enterprise Architect se conserva como smoke de Transición para CU-27; no se confunde con la evidencia automatizada del parser/serializer.
