# C10-cu27-001 — Demo reproducible final

Implementación de CU-27.

## Incremento

- `DemoScenarioSeeder` bajo perfil `demo`;
- XMI canónico `veterinaria-cu27.xmi`;
- OWNER/EDITOR y Project ID deterministas;
- base H2 separada `classforge-demo`;
- `demo-reset.ps1` para limpiar sólo el estado demo, `demo-start.ps1` para validar/mostrar comandos manuales, `demo-stop.ps1` como recordatorio sin kill y `demo-smoke.ps1` para verificar una instancia ya levantada;
- `demo-acceptance.ps1` como agregador de regresiones finales;
- `demo-ea-smoke.ps1` para round-trip real mediante Enterprise Architect Automation;
- ejemplo y prompts bajo `examples/veterinaria-cu27/`;
- acceptance HTTP de login OWNER/EDITOR, UML y XMI round-trip.

## Modelo

Seis clases: Propietario, Animal, Veterinario, Cita, Tratamiento y Usuario. Cinco relaciones cubren asociación, agregación y composición. Usuario contiene `username/password` para permitir demostrar CU-14 Auth sin alterar el fixture.

## Criterio de cierre

La demo se considera reproducible cuando un reset reconstruye el mismo proyecto y `demoScenarioAcceptance` confirma identidades, roles, UML y XMI. Las capacidades generadas se recertifican con los acceptance ya cerrados de CU-13..19 y CU-10/11.
