# Iteración C1 — CU07-001 Presencia de colaboradores

## Fase

Construcción.

## Objetivo

Visualizar quién está conectado y qué elemento está manipulando sin contaminar el modelo UML.

## Implementado

- presencia STOMP sobre CU-06;
- registro en memoria;
- USER_JOINED;
- USER_LEFT;
- USER_SELECTED_ELEMENT;
- USER_MOVED_CURSOR;
- PRESENCE_SNAPSHOT;
- SessionDisconnectEvent;
- autorización por proyecto;
- coordenadas locales JointJS;
- throttle aproximado de 15 Hz;
- overlays de cursor;
- listado responsive de sesiones;
- indicador de selección remota;
- prueba de revisión inalterada.

## Escenario demostrable

1. Chrome y Edge abren el mismo proyecto con el owner.
2. Ambos aparecen en Colaboradores.
3. Un cliente selecciona Animal.
4. El otro muestra quién trabaja en Animal.
5. El cursor se visualiza remotamente.
6. Cerrar una pestaña provoca USER_LEFT.
7. La revisión UML no cambia por presencia.

## Limitación

El acceso multiusuario real permanece pendiente de ProjectMembership/invitaciones.

## Resultado

**CU-07 — CERRADO.**