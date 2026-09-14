# Veterinaria CU-27

Fixture canónico de la demo reproducible final de ClassForge.

## Modelo

- `Propietario`: `id`, `nombre`, `email`.
- `Animal`: `id`, `nombre`, `especie`, `fechaNacimiento`, `createdAt`.
- `Veterinario`: `id`, `nombre`, `especialidad`.
- `Cita`: `id`, `fecha`, `motivo`.
- `Tratamiento`: `id`, `descripcion`.
- `Usuario`: `id`, `username`, `password`, `rol` (permite demostrar generación Auth).

Relaciones: agregación Propietario→Animal, composición Animal→Cita, asociación Veterinario→Cita, Cita→Tratamiento y Usuario→Veterinario.

`veterinaria-cu27.xmi` es XMI 2.1 con UUIDs canónicos de ClassForge y es el mismo recurso utilizado por el perfil Spring `demo`.

## Credenciales del perfil demo

```text
OWNER : demo@classforge.local / classforge-demo
EDITOR: colaborador@classforge.local / classforge-demo
```

Proyecto determinista:

```text
Veterinaria CU-27
27000000-0000-0000-0000-000000000100
```

Estas credenciales existen únicamente al arrancar con el perfil `demo`.
