# C1 — Fundación de autenticación y ownership

**Ciclo:** 1  
**Fase:** Elaboración  
**Tipo:** incremento transversal

## Resultado

- usuario persistente con UUID;
- email normalizado y único;
- BCrypt;
- JWT stateless;
- proyecto asociado a `ownerId`;
- endpoints protegidos;
- biblioteca filtrada por propietario.

## Trazabilidad

Soporta CU-01 y cierra CU-28, CU-29 y CU-30.

CU-31 permanece fuera de alcance porque requiere `ProjectMembership` e invitaciones.
