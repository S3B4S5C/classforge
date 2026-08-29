# Documentación de ClassForge

Esta carpeta contiene las fuentes Markdown que alimentarán la documentación oficial de presentación.

Los `.md` son documentos de trabajo versionables. El **Word final no debe convertirse en una segunda fuente de verdad**: se construirá editorialmente a partir de estas fuentes cuando el proyecto alcance el corte de entrega.

## Convención documental

| Carpeta | Pregunta que responde | Uso posterior en Word |
|---|---|---|
| `product/` | ¿Qué producto se pretende construir? | Introducción, visión, alcance, requisitos |
| `puds/` | ¿Cómo se desarrolló y en qué estado está? | PUDS, fases, ciclos, CU, trazabilidad |
| `architecture/` | ¿Cómo está construido y por qué? | Arquitectura, diseño, decisiones |
| `uml/` | ¿Qué modelos UML académicos representan el sistema? | Diagramas y explicación UML |
| `runtime/` | ¿Cómo se instala, ejecuta y demuestra? | Transición, instalación, tutorial, anexos |

## Jerarquía de autoridad

Para evitar contradicciones:

1. `puds/current-status.md` decide qué está implementado **ahora**.
2. `puds/use-cases.md` contiene la especificación **vigente** de los CU.
3. `puds/cycles/` explica qué se hizo en cada Ciclo/iteración.
4. `architecture/` explica las decisiones técnicas vigentes.
5. `product/product.md` describe el **producto objetivo**, incluyendo capacidades futuras.
6. `puds/history/` conserva planes y bitácoras superadas sin gobernar el presente.

## Corte actual

```text
Fase PUDS: Elaboración
Ciclo 1: CERRADO
Ciclo 2: ABIERTO — CU-31 CERRADO; CU-09 SIGUIENTE
Fecha: 29-08-2026
```

## Composición prevista del Word

1. Portada e identificación.
2. Resumen ejecutivo.
3. Problema, visión y objetivos.
4. Alcance y requisitos.
5. Proceso Unificado de Desarrollo de Software.
6. Casos de uso y trazabilidad.
7. Arquitectura de ClassForge.
8. Modelos y diagramas UML.
9. Implementación por ciclos.
10. Pruebas y evidencias.
11. Instalación, ejecución y demostración.
12. Limitaciones y trabajo pendiente.
13. Conclusiones.
14. Bibliografía.
15. Anexos y tutorial.

Esta estructura es editorial. Los detalles técnicos deben mantenerse primero en los Markdown especializados.
