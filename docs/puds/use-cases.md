# ClassForge — Casos de Uso y Ruta de Implementación según PUDS

> Documento de diseño para `docs/puds/use-cases.md`

## 1. Propósito

Este documento define los casos de uso principales de **ClassForge** y los organiza como guía para la implementación siguiendo el **Proceso Unificado de Desarrollo de Software (PUDS)**.

ClassForge es una herramienta CASE colaborativa que permite modelar diagramas de clases UML y, a partir de ellos, generar aplicaciones funcionales compuestas por:

- Backend Spring Boot + JPA.
- API REST documentada mediante OpenAPI.
- Colección de Postman.
- Frontend web Angular.
- Frontend mobile basado en Angular + Capacitor.
- Interacción mediante voz y lenguaje natural sobre la aplicación generada.
- Importación y exportación con Enterprise Architect.
- Creación de diagramas manual, por imagen y por voz.
- Colaboración en tiempo real.
- Funcionamiento sin depender de Internet para las capacidades esenciales de IA y STT.

---

# 2. Cómo se aplicará PUDS al proyecto

El desarrollo se organizará según las cuatro fases del Proceso Unificado:

1. **Inicio**
   - Delimitar el alcance.
   - Identificar actores y casos de uso.
   - Definir los riesgos principales.
   - Construir una visión general del sistema.

2. **Elaboración**
   - Consolidar la arquitectura.
   - Resolver los riesgos técnicos más importantes.
   - Implementar los casos de uso arquitectónicamente significativos.
   - Definir el modelo canónico UML y los contratos internos.

3. **Construcción**
   - Implementar incrementalmente la funcionalidad restante.
   - Completar generación de backend y frontend.
   - Completar colaboración, IA, STT, imagen y XMI.
   - Aumentar cobertura de pruebas.

4. **Transición**
   - Integrar, estabilizar y preparar demostraciones.
   - Validar instalación y ejecución offline.
   - Probar escenarios completos.
   - Preparar documentación, tutorial y evidencias.

El proyecto será **dirigido por casos de uso, centrado en la arquitectura, iterativo e incremental**.

---

# 3. Principio de implementación

Los casos de uso no deben implementarse como funciones aisladas. Todos deben depender de un mismo núcleo:

```text
Entradas
  ├─ Manual
  ├─ Imagen
  ├─ Voz
  └─ Enterprise Architect / XMI
          │
          ▼
      Modelo UML canónico
          │
          ├─ Renderizado
          ├─ Colaboración
          ├─ Persistencia
          ├─ Validación
          ├─ Transformación relacional
          ├─ Generación Spring Boot
          ├─ Generación Frontend
          └─ Exportación XMI
```

La IA, el canvas, XMI y los generadores **no deben tener modelos de dominio independientes**.

---

# 4. Actores

## A1. Modelador

Usuario principal de ClassForge.

Puede:

- Crear proyectos.
- Crear y editar diagramas.
- Importar modelos.
- Generar aplicaciones.
- Exportar proyectos.
- Utilizar voz e IA.

## A2. Colaborador

Usuario conectado al mismo proyecto que participa en la edición en tiempo real.

Puede:

- Visualizar cambios.
- Crear y editar elementos.
- Ver presencia de otros usuarios.
- Recibir actualizaciones del modelo.

## A3. Enterprise Architect

Sistema externo con el que ClassForge intercambia modelos mediante XMI.

## A4. Sistema de archivos

Actor técnico externo utilizado para:

- Guardar proyectos.
- Importar imágenes.
- Importar XMI.
- Exportar XMI.
- Guardar proyectos generados.

## A5. Usuario de la aplicación generada

Persona que utiliza la aplicación web o mobile generada por ClassForge.

Puede:

- Utilizar la interfaz CRUD.
- Consultar datos mediante voz.
- Crear, actualizar o eliminar información mediante lenguaje natural.

---

# 5. Casos de uso de ClassForge

---

## CU-01 — Crear proyecto de modelado

**Actor principal:** Modelador  
**Prioridad:** Alta  
**Fase PUDS inicial:** Inicio / Elaboración

### Objetivo

Crear un proyecto que contenga un modelo UML independiente y persistible.

### Precondiciones

- ClassForge está iniciado.

### Flujo principal

1. El usuario selecciona **Nuevo proyecto**.
2. Introduce un nombre.
3. ClassForge genera un identificador único.
4. ClassForge crea un modelo UML vacío.
5. Se inicializa la revisión del proyecto.
6. El proyecto se abre en el diagramador.

### Postcondiciones

- Existe un proyecto editable.
- El proyecto puede ser guardado.
- El modelo canónico UML está inicializado.

### Implementación requerida

- `Project`.
- `ProjectService`.
- Persistencia local inicial.
- Endpoint REST para proyectos.
- Vista inicial Angular.

### Criterios de aceptación

- El usuario puede crear un proyecto.
- El proyecto continúa existiendo tras reiniciar la aplicación.
- Cada proyecto posee ID, nombre y modelo UML.

---

## CU-02 — Abrir y guardar proyecto

**Actor principal:** Modelador  
**Prioridad:** Alta  
**Fase:** Elaboración

### Objetivo

Persistir y recuperar completamente un modelo de ClassForge.

### Flujo principal

1. El usuario selecciona un proyecto existente.
2. ClassForge recupera su modelo UML.
3. El diagramador representa el modelo.
4. El usuario realiza modificaciones.
5. ClassForge guarda el nuevo estado.

### Postcondiciones

- El proyecto conserva estructura UML y metadatos.
- La representación visual puede reconstruirse.

### Decisión de diseño

El modelo persistido debe ser el **modelo UML canónico**, no el estado interno de JointJS.

La información visual se almacenará como metadatos independientes:

- posición X/Y;
- dimensiones;
- configuración de visualización.

---

## CU-03 — Crear diagrama manualmente

**Actor principal:** Modelador  
**Prioridad:** Crítica  
**Fase:** Elaboración

### Objetivo

Construir un diagrama de clases UML mediante interacción gráfica.

### Flujo principal

1. El usuario abre un proyecto.
2. Selecciona **Crear clase**.
3. Introduce el nombre.
4. La clase se añade al modelo UML.
5. El canvas refleja el nuevo elemento.
6. El usuario añade atributos.
7. El usuario crea relaciones.
8. ClassForge valida las modificaciones.

### Operaciones mínimas

- Crear clase.
- Renombrar clase.
- Eliminar clase.
- Añadir atributo.
- Editar atributo.
- Eliminar atributo.
- Crear asociación.
- Crear generalización.
- Crear agregación.
- Crear composición.
- Definir multiplicidades.
- Cambiar visibilidad.
- Mover elementos visualmente.

### Criterio arquitectónico

La interfaz gráfica ejecutará **comandos de dominio**.

Ejemplo:

```text
Click "Crear clase"
      ↓
CreateClassCommand
      ↓
UMLModel
      ↓
Evento
      ↓
Canvas
```

Esto permitirá reutilizar las mismas operaciones para:

- colaboración;
- undo/redo;
- IA;
- voz.

---

## CU-04 — Validar modelo UML

**Actor principal:** Modelador  
**Prioridad:** Crítica  
**Fase:** Elaboración

### Objetivo

Impedir que el modelo interno llegue a estados inválidos.

### Validaciones iniciales

- Nombres de clases válidos.
- IDs únicos.
- Relaciones entre elementos existentes.
- Multiplicidades válidas.
- Atributos con tipos reconocidos.
- No permitir referencias a elementos eliminados.
- Validar restricciones requeridas para generación.

### Flujo principal

1. Se solicita una modificación.
2. El dominio valida la operación.
3. Si es válida, se aplica.
4. Si no es válida, se rechaza.
5. La interfaz muestra el error.

### Regla

Ningún adaptador externo debe saltarse esta validación:

```text
UI ────────────┐
IA ────────────┤
XMI ───────────┼─> Commands -> Validator -> UMLModel
Imagen ────────┤
Colaboración ──┘
```

---

## CU-05 — Deshacer y rehacer cambios

**Actor principal:** Modelador  
**Prioridad:** Media-Alta  
**Fase:** Elaboración / Construcción

### Objetivo

Revertir operaciones del diagramador.

### Flujo principal

1. El usuario ejecuta una modificación.
2. La modificación se almacena como comando.
3. Selecciona **Deshacer**.
4. ClassForge revierte el comando.
5. Selecciona **Rehacer**.
6. ClassForge vuelve a aplicarlo.

### Dependencia

Requiere que las modificaciones del modelo utilicen patrón Command.

---

## CU-06 — Colaborar en tiempo real

**Actores:** Modelador, Colaborador  
**Prioridad:** Crítica  
**Fase:** Elaboración

### Objetivo

Permitir que varias personas modifiquen el mismo proyecto y observen los cambios en tiempo real.

### Flujo principal

1. Usuario A abre un proyecto.
2. Usuario B abre el mismo proyecto.
3. Ambos se conectan al canal WebSocket del proyecto.
4. Usuario A realiza una modificación.
5. El comando llega a Spring Boot.
6. El servidor valida la revisión.
7. El servidor aplica el comando.
8. Incrementa la revisión del proyecto.
9. Difunde el cambio.
10. Usuario B actualiza automáticamente su vista.

### Arquitectura

```text
Angular A ─┐
           ├─ WebSocket/STOMP ─> Spring Boot ─> UMLModel
Angular B ─┘                         │
                                    └─ broadcast
```

### Regla

El servidor es la autoridad del estado colaborativo.

Los clientes **no envían el modelo completo**, envían operaciones.

### Ejemplo

```json
{
  "operationId": "uuid",
  "projectId": "project-1",
  "baseRevision": 41,
  "type": "ADD_ATTRIBUTE",
  "payload": {
    "classId": "animal",
    "name": "species",
    "dataType": "String"
  }
}
```

---

## CU-07 — Visualizar presencia de colaboradores

**Actores:** Modelador, Colaborador  
**Prioridad:** Media  
**Fase:** Construcción

### Objetivo

Mostrar quién está conectado y qué elemento está manipulando.

### Eventos efímeros

- USER_JOINED.
- USER_LEFT.
- USER_SELECTED_ELEMENT.
- USER_MOVED_CURSOR.

Estos eventos no modifican el modelo UML y no necesitan persistencia permanente.

---

## CU-08 — Crear o modificar un modelo mediante voz

**Actor:** Modelador  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Permitir operar ClassForge mediante lenguaje hablado.

### Flujo principal

1. El usuario activa el micrófono.
2. Angular captura audio.
3. El audio se envía al servicio STT local.
4. Whisper convierte audio a texto.
5. El texto se entrega al asistente.
6. El LLM genera uno o varios comandos estructurados.
7. ClassForge valida los comandos.
8. Los comandos se aplican al modelo.
9. El canvas refleja el resultado.

### Ejemplo

Usuario:

> Crea una clase Animal con id Long y nombre String.

Resultado interno:

```json
{
  "commands": [
    {
      "type": "CREATE_CLASS",
      "name": "Animal"
    },
    {
      "type": "ADD_ATTRIBUTE",
      "class": "Animal",
      "name": "id",
      "dataType": "Long"
    },
    {
      "type": "ADD_ATTRIBUTE",
      "class": "Animal",
      "name": "nombre",
      "dataType": "String"
    }
  ]
}
```

### Regla crítica

La IA no modifica directamente el modelo.

---

## CU-09 — Crear un modelo desde una imagen

**Actor:** Modelador  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Convertir una fotografía o imagen de un diagrama de clases en un modelo editable.

### Flujo principal

1. El usuario selecciona una imagen.
2. ClassForge realiza preprocesamiento opcional.
3. Un modelo multimodal local interpreta la imagen.
4. El modelo produce una estructura intermedia.
5. ClassForge valida cada elemento UML.
6. Se genera el modelo canónico.
7. El canvas muestra el resultado.
8. El usuario corrige manualmente cualquier error.

### Regla

La salida del modelo multimodal debe considerarse una **propuesta**, no una verdad absoluta.

---

## CU-10 — Importar modelo desde Enterprise Architect

**Actores:** Modelador, Enterprise Architect  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Convertir un archivo XMI proveniente de Enterprise Architect al modelo canónico de ClassForge.

### Flujo principal

1. El usuario selecciona **Importar XMI**.
2. Selecciona el archivo exportado desde Enterprise Architect.
3. El adaptador analiza XMI.
4. Convierte elementos soportados al modelo canónico.
5. ClassForge valida el resultado.
6. Se crea o actualiza el proyecto.
7. El modelo se representa en el canvas.

### Subconjunto inicial soportado

- Package.
- Class.
- Property.
- Association.
- Generalization.
- Aggregation.
- Composition.
- Multiplicity.
- DataType.

---

## CU-11 — Exportar modelo a Enterprise Architect

**Actores:** Modelador, Enterprise Architect  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Generar un XMI importable por Enterprise Architect.

### Flujo principal

1. El usuario selecciona **Exportar a Enterprise Architect**.
2. ClassForge valida el modelo.
3. El adaptador genera XMI.
4. Se guarda el archivo.
5. El archivo puede importarse en Enterprise Architect.

### Prueba obligatoria

La compatibilidad debe comprobarse mediante una prueba real:

```text
ClassForge
   ↓ exportar XMI
Enterprise Architect
   ↓ importar
Modelo equivalente
```

y también en sentido inverso.

---

# 6. Casos de uso de generación

---

## CU-12 — Transformar UML a modelo relacional

**Actor:** Modelador  
**Prioridad:** Crítica  
**Fase:** Elaboración / Construcción

### Objetivo

Convertir determinísticamente el modelo UML a una representación de base de datos relacional.

### Flujo principal

1. El usuario solicita generación.
2. ClassForge valida el modelo UML.
3. Se aplica el conjunto de reglas de transformación.
4. Se genera `RelationalModel`.
5. El modelo resultante se utiliza para generar JPA y persistencia.

### Ejemplos de reglas

- Clase persistible → tabla.
- Atributo → columna.
- Identificador → primary key.
- Asociación 1:N → foreign key en lado N.
- Asociación N:M → tabla intermedia.
- Asociación 1:1 → foreign key según convención definida.
- Herencia → estrategia documentada.
- Enum → representación definida por convención.

### Regla crítica

Esta transformación **no utiliza IA**.

Debe ser:

- reproducible;
- determinista;
- verificable;
- documentada.

---

## CU-13 — Generar backend Spring Boot

**Actor:** Modelador  
**Prioridad:** Crítica  
**Fase:** Construcción

### Objetivo

Generar un backend ejecutable basado en el modelo UML.

### Artefactos

```text
generated/backend/
├─ build.gradle
├─ settings.gradle
└─ src/
   └─ main/
      ├─ java/
      │  ├─ entity/
      │  ├─ repository/
      │  ├─ service/
      │  ├─ controller/
      │  ├─ dto/
      │  └─ exception/
      └─ resources/
```

### Tecnologías

- Java 21 como target.
- Spring Boot.
- Gradle.
- Spring Web MVC.
- Spring Data JPA.
- Hibernate.
- Jakarta Validation.
- PostgreSQL.
- H2 opcional para demostración.
- OpenAPI.

### Flujo principal

1. Se obtiene `RelationalModel`.
2. Se construye un modelo de generación.
3. FreeMarker procesa templates.
4. Se generan clases y configuración.
5. Se genera proyecto Gradle.
6. Opcionalmente se ejecuta un build de validación.
7. Se reportan errores o éxito.

### Criterio

El backend generado debe compilar y ejecutar sin modificaciones manuales para los modelos soportados.

---

## CU-14 — Generar API CRUD expresiva

**Actor:** Modelador  
**Prioridad:** Crítica  
**Fase:** Construcción

### Objetivo

Generar una API suficientemente genérica para que posteriormente pueda ser operada por lenguaje natural.

### Capacidades generadas

Para cada entidad:

- CREATE.
- READ.
- UPDATE.
- DELETE.
- LIST.
- SEARCH.
- FILTER.
- SORT.
- PAGINATION.
- COUNT.
- Navegación de relaciones.

### Ejemplo

```http
GET /api/animals?page=0&size=5&sort=createdAt,desc
```

Esto permite interpretar posteriormente:

> Muéstrame los últimos cinco animales.

---

## CU-15 — Generar OpenAPI y Postman

**Actor:** Modelador  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Proporcionar contratos y pruebas de la API generada.

### Flujo

```text
Backend generado
       ↓
    OpenAPI
       ↓
┌──────┴─────────┐
↓                ↓
Cliente TS     Postman
```

### Resultado

- OpenAPI JSON/YAML.
- Colección Postman.
- Clientes TypeScript cuando corresponda.

---

## CU-16 — Generar Domain Manifest

**Actor:** Sistema  
**Prioridad:** Crítica  
**Fase:** Construcción

### Objetivo

Describir de forma estructurada el dominio generado para que el frontend y el asistente comprendan qué entidades y operaciones existen.

### Ejemplo

```json
{
  "entities": [
    {
      "name": "Animal",
      "endpoint": "/api/animals",
      "attributes": [
        {
          "name": "nombre",
          "type": "String",
          "searchable": true
        },
        {
          "name": "createdAt",
          "type": "DateTime",
          "sortable": true
        }
      ]
    }
  ]
}
```

### Uso

El Domain Manifest será consumido por:

- generador de UI;
- asistente de IA;
- ejecutor de comandos;
- validación de consultas.

---

## CU-17 — Generar frontend web

**Actor:** Modelador  
**Prioridad:** Crítica  
**Fase:** Construcción

### Objetivo

Generar una aplicación Angular funcional basada en el modelo.

### Capacidades

Por entidad:

- listado;
- detalle;
- creación;
- edición;
- eliminación;
- navegación entre relaciones;
- filtros;
- paginación;
- ordenamiento.

### Inferencia de controles

| Tipo | Control |
|---|---|
| String | input |
| Long / Integer | number |
| Decimal | number |
| Boolean | checkbox/switch |
| Date | date picker |
| DateTime | datetime |
| Enum | select |
| N:1 | select/autocomplete |
| 1:N | tabla/listado |

### Estrategia

No generar toda la UI desde cero.

Usar un **frontend template reusable** y generar principalmente:

- modelos;
- configuración;
- rutas;
- Domain Manifest;
- cliente API.

---

## CU-18 — Generar frontend mobile

**Actor:** Modelador  
**Prioridad:** Alta  
**Fase:** Construcción

### Objetivo

Producir una aplicación mobile sin mantener un segundo framework independiente.

### Decisión

Usar:

```text
Angular generado
      ↓
Capacitor
      ↓
Android
```

### Resultado

El mismo dominio generado puede producir:

- frontend web;
- frontend Android.

La primera plataforma mobile objetivo será Android.

---

# 7. Casos de uso de la aplicación generada

---

## CU-19 — Consultar datos mediante voz

**Actor:** Usuario de la aplicación generada  
**Prioridad:** Crítica  
**Fase:** Construcción

### Objetivo

Consultar la API generada usando lenguaje natural hablado.

### Ejemplo

> Quiero ver los últimos 5 animales.

### Flujo

1. El usuario habla.
2. El audio se convierte a texto mediante STT.
3. El asistente recibe:
   - texto;
   - Domain Manifest;
   - operaciones permitidas.
4. La IA produce `AssistantCommand`.
5. Se valida el comando.
6. Se transforma a petición API.
7. Se ejecuta.
8. Se presenta el resultado.

### Ejemplo interno

```json
{
  "operation": "LIST",
  "entity": "Animal",
  "limit": 5,
  "sort": {
    "field": "createdAt",
    "direction": "DESC"
  }
}
```

---

## CU-20 — Crear datos mediante voz

**Actor:** Usuario de la aplicación generada  
**Prioridad:** Crítica  
**Fase:** Construcción

### Ejemplo

> Añade un animal llamado Luna de especie perro.

### Resultado

```json
{
  "operation": "CREATE",
  "entity": "Animal",
  "data": {
    "nombre": "Luna",
    "especie": "perro"
  }
}
```

### Flujo

1. STT produce texto.
2. El asistente identifica la entidad.
3. Identifica campos.
4. Valida contra Domain Manifest.
5. Solicita información faltante si es obligatoria.
6. Ejecuta `POST`.
7. Informa el resultado.

---

## CU-21 — Ejecutar una operación relacionada mediante voz

**Actor:** Usuario de la aplicación generada  
**Prioridad:** Alta  
**Fase:** Construcción

### Ejemplo

> Añade una cita para Luna mañana a las 4 de la tarde.

### Flujo

1. Identificar `Cita`.
2. Identificar referencia a `Animal`.
3. Buscar Animal con nombre "Luna".
4. Resolver su ID.
5. Interpretar fecha y hora.
6. Construir `CREATE Cita`.
7. Validar.
8. Crear cita.
9. Informar resultado.

### Representación posible

```json
{
  "steps": [
    {
      "operation": "SEARCH",
      "entity": "Animal",
      "filters": [
        {
          "field": "nombre",
          "operator": "EQ",
          "value": "Luna"
        }
      ]
    },
    {
      "operation": "CREATE",
      "entity": "Cita",
      "data": {
        "animalId": "$previous.id",
        "fecha": "2026-09-22T16:00:00"
      }
    }
  ]
}
```

---

## CU-22 — Actualizar información mediante voz

**Actor:** Usuario de la aplicación generada  
**Prioridad:** Alta  
**Fase:** Construcción

### Ejemplo

> Cambia el nombre de Luna a Lunita.

### Flujo

1. Buscar entidad.
2. Resolver registro.
3. Detectar modificación.
4. Validar campo.
5. Ejecutar actualización.
6. Confirmar resultado.

---

## CU-23 — Eliminar información mediante voz

**Actor:** Usuario de la aplicación generada  
**Prioridad:** Media-Alta  
**Fase:** Construcción

### Regla de seguridad

Las operaciones destructivas deben solicitar confirmación.

Ejemplo:

> Elimina la cita de mañana de Luna.

El sistema:

1. Busca la cita.
2. Muestra/describe el registro encontrado.
3. Solicita confirmación.
4. Solo después ejecuta DELETE.

---

# 8. Casos de uso de IA local y offline

---

## CU-24 — Ejecutar Speech-to-Text localmente

**Actor:** Modelador / Usuario de aplicación generada  
**Prioridad:** Crítica  
**Fase:** Elaboración / Construcción

### Objetivo

Convertir voz a texto sin depender de Internet.

### Tecnología prevista

- whisper.cpp.

### Arquitectura principal

```text
Frontend
   │ audio
   ▼
Servidor local
   │
whisper.cpp
   │
   ▼
texto
```

### Requisito

La funcionalidad esencial debe poder demostrarse con Internet desconectado.

---

## CU-25 — Ejecutar asistente de IA localmente

**Actor:** Modelador / Usuario de aplicación generada  
**Prioridad:** Crítica  
**Fase:** Elaboración / Construcción

### Objetivo

Interpretar lenguaje natural sin API externa obligatoria.

### Arquitectura

```text
Texto
  ↓
LLM local
  ↓
AssistantCommand
  ↓
Validador determinista
  ↓
Aplicación
```

### Regla crítica

El LLM no tiene autoridad final.

La autoridad pertenece a:

- modelo UML;
- Domain Manifest;
- validadores;
- API.

---

# 9. Casos de uso de auditoría y trazabilidad

---

## CU-26 — Registrar cambios del proyecto

**Actor:** Sistema  
**Prioridad:** Alta  
**Fase:** Elaboración / Construcción

### Objetivo

Conservar evidencia del proceso y facilitar trazabilidad.

### Información recomendable

- operación;
- fecha;
- usuario;
- revisión anterior;
- revisión nueva;
- elemento afectado.

Esto puede utilizarse posteriormente como evidencia PUDS del proceso real de desarrollo y evolución de modelos.

---

## CU-27 — Generar un proyecto de demostración reproducible

**Actor:** Modelador  
**Prioridad:** Alta  
**Fase:** Transición

### Objetivo

Mantener al menos un dominio de referencia con el cual verificar el pipeline completo.

### Proyecto recomendado

**Veterinaria**

Modelo mínimo:

- Animal.
- Cita.
- Veterinario.
- Propietario.

### Prueba de extremo a extremo

```text
UML veterinaria
      ↓
Spring Boot
      ↓
Frontend web/mobile
      ↓
Ejecutar
      ↓
"crea un animal..."
      ↓
"muestra los últimos 5 animales"
      ↓
"crea una cita para Luna..."
```

Este proyecto debe mantenerse durante todo el desarrollo como prueba de regresión funcional.

---

# 10. Casos de uso arquitectónicamente significativos

Durante **Elaboración** se deben atacar primero los casos que más riesgo técnico representan.

| Caso | Motivo |
|---|---|
| CU-03 Modelado manual | Valida el modelo canónico |
| CU-04 Validación UML | Protege todos los adaptadores |
| CU-06 Colaboración | Define consistencia distribuida |
| CU-12 UML → Relacional | Núcleo CASE |
| CU-13 Generación Spring Boot | Valida el objetivo central |
| CU-16 Domain Manifest | Conecta generación e IA |
| CU-19 Consulta por voz | Valida el objetivo de interacción |
| CU-24 STT local | Valida offline |
| CU-25 IA local | Valida hardware y arquitectura |

No conviene dejar estos casos para el final.

---

# 11. Orden recomendado de implementación por iteraciones PUDS

---

## Fase I — Inicio

### Iteración I1 — Visión y alcance

**Objetivo:** definir exactamente qué construye ClassForge.

Entregables:

- visión del producto;
- actores;
- casos de uso;
- requisitos funcionales;
- requisitos no funcionales;
- riesgos iniciales;
- glosario.

Casos trabajados:

- CU-01.
- CU-03 a nivel conceptual.
- CU-13 a nivel conceptual.
- CU-17/CU-18 a nivel conceptual.
- CU-19 a nivel conceptual.

**Salida esperada:** alcance aprobado y backlog inicial.

---

# 12. Fase II — Elaboración

Esta es la fase más importante para reducir riesgos.

---

## Iteración E1 — Modelo canónico UML

### Objetivo

Construir el núcleo independiente de UI.

Implementar:

- `UMLModel`.
- `UMLClass`.
- `UMLAttribute`.
- `UMLRelationship`.
- multiplicidades.
- tipos.
- IDs.
- validadores.
- comandos.

Casos:

- CU-01.
- CU-02.
- CU-03 parcialmente.
- CU-04.
- CU-05 parcialmente.

### Criterio de salida

Debe ser posible crear un modelo completo mediante pruebas backend sin usar todavía JointJS.

---

## Iteración E2 — Diagramador web

### Objetivo

Conectar Angular al dominio UML.

Implementar:

- canvas;
- clases;
- atributos;
- relaciones;
- selección;
- propiedades;
- zoom/pan;
- auto-layout;
- comandos desde UI.

Tecnologías:

- Angular 22.
- JointJS Community.
- ELK.js.
- Angular Material.

Casos:

- CU-03.
- CU-05.

### Criterio

Una veterinaria sencilla puede modelarse manualmente.

---

## Iteración E3 — Colaboración

### Objetivo

Resolver sincronización multiusuario antes de que el producto sea demasiado grande.

Implementar:

- Spring WebSocket.
- STOMP.
- canales por proyecto.
- revisionado.
- comandos remotos.
- presencia.

Casos:

- CU-06.
- CU-07.
- CU-26.

### Prueba

Dos navegadores modifican el mismo diagrama y ambos convergen al mismo estado.

---

## Iteración E4 — Prueba de generación Spring Boot

### Objetivo

Demostrar técnicamente el núcleo CASE.

Implementar inicialmente solo:

```text
UMLClass
 ↓
Table
 ↓
@Entity
 ↓
Repository
 ↓
Service
 ↓
Controller
```

Casos:

- CU-12.
- CU-13.
- CU-14 parcialmente.

### Prueba

Un diagrama `Animal` genera un proyecto Spring Boot que:

```bash
./gradlew build
./gradlew bootRun
```

sin cambios manuales.

---

## Iteración E5 — Prueba de IA/STT local

### Objetivo

Eliminar pronto el riesgo de hardware.

Implementar prototipo:

```text
Audio
 ↓
whisper.cpp
 ↓
Texto
 ↓
LLM local
 ↓
JSON
```

No es necesario conectarlo todavía al producto completo.

Casos:

- CU-24.
- CU-25.

### Pruebas mínimas

- Laptop i5 11ª / 16 GB.
- Medir latencia.
- Medir RAM.
- Verificar ejecución sin Internet.

### Criterio

La orden:

> crea una clase Animal

debe convertirse de forma razonable a un comando JSON válido.

---

# 13. Fase III — Construcción

---

## Iteración C1 — UML completo soportado

Completar:

- asociaciones;
- multiplicidades;
- herencia;
- composición;
- agregación;
- tipos;
- enums;
- metadatos de generación.

Casos:

- CU-03.
- CU-04.
- CU-05.

---

## Iteración C2 — Persistencia y colaboración robusta

Completar:

- guardado;
- apertura;
- revisionado;
- reconexión;
- conflictos;
- historial básico.

Casos:

- CU-02.
- CU-06.
- CU-07.
- CU-26.

---

## Iteración C3 — Generador relacional

Implementar y documentar todas las convenciones UML → relacional.

Casos:

- CU-12.

Pruebas específicas:

- 1:1.
- 1:N.
- N:M.
- herencia.
- enums.
- campos obligatorios.

---

## Iteración C4 — Generador Spring Boot completo

Completar:

- entidades;
- repositorios;
- servicios;
- controllers;
- DTO;
- validación;
- excepciones;
- filtros;
- sorting;
- paginación;
- relaciones;
- configuración Gradle.

Casos:

- CU-13.
- CU-14.

---

## Iteración C5 — OpenAPI y Postman

Implementar:

- OpenAPI.
- cliente TypeScript.
- colección Postman.

Casos:

- CU-15.

---

## Iteración C6 — Domain Manifest

Implementar generación del descriptor de dominio.

Casos:

- CU-16.

### Criterio

El manifiesto debe ser suficiente para conocer:

- entidades;
- atributos;
- tipos;
- endpoints;
- relaciones;
- operaciones;
- filtros;
- campos requeridos.

---

## Iteración C7 — Frontend web generado

Implementar template Angular reusable.

Casos:

- CU-17.

### Prueba

Veterinaria genera:

- listado Animal;
- formulario Animal;
- listado Cita;
- formulario Cita;
- navegación de relaciones.

---

## Iteración C8 — Frontend mobile generado

Agregar Capacitor al template.

Casos:

- CU-18.

### Criterio

El mismo proyecto generado puede:

- ejecutarse en navegador;
- empaquetarse como Android.

---

## Iteración C9 — Asistente de voz de la aplicación generada

Conectar:

```text
audio
 ↓
STT
 ↓
LLM
 ↓
AssistantCommand
 ↓
API
```

Casos:

- CU-19.
- CU-20.
- CU-21.
- CU-22.
- CU-23.
- CU-24.
- CU-25.

### Escenarios obligatorios

1. Consultar.
2. Crear.
3. Actualizar.
4. Eliminar con confirmación.
5. Ejecutar operación que involucre una relación.

---

## Iteración C10 — Voz dentro de ClassForge

Reutilizar la misma infraestructura de comandos para controlar el diagramador.

Casos:

- CU-08.

---

## Iteración C11 — Imagen a UML

Implementar:

- carga de imagen;
- preprocesamiento;
- análisis multimodal;
- generación de propuesta;
- validación;
- corrección.

Casos:

- CU-09.

---

## Iteración C12 — Enterprise Architect

Implementar:

- importador XMI;
- exportador XMI;
- pruebas con EA real.

Casos:

- CU-10.
- CU-11.

---

# 14. Fase IV — Transición

---

## Iteración T1 — Integración total

Ejecutar pruebas de extremo a extremo.

### Escenario principal

```text
1. Crear proyecto.
2. Modelar veterinaria.
3. Editar colaborativamente.
4. Guardar.
5. Generar backend.
6. Ejecutar backend.
7. Generar frontend.
8. Ejecutar web.
9. Generar Android.
10. Consultar animales por voz.
11. Crear animal por voz.
12. Crear cita relacionada.
13. Exportar XMI.
14. Abrir en Enterprise Architect.
```

---

## Iteración T2 — Offline

Desconectar Internet.

Comprobar:

- ClassForge inicia.
- Diagramador funciona.
- colaboración funciona sobre LAN;
- Whisper funciona;
- LLM funciona;
- generación funciona;
- aplicación generada acepta voz.

---

## Iteración T3 — Robustez

Probar:

- modelos vacíos;
- relaciones inválidas;
- XMI inválido;
- IA devuelve JSON incorrecto;
- pérdida de WebSocket;
- servidor no disponible;
- modelo demasiado grande;
- errores de compilación del proyecto generado.

---

## Iteración T4 — Documentación y demostración

Preparar:

- tutorial;
- onboarding;
- ejemplos;
- capturas;
- diagramas UML;
- arquitectura;
- trazabilidad;
- registro de iteraciones;
- pruebas;
- bibliografía.

---

# 15. Dependencias entre casos de uso

```text
CU-01 Crear proyecto
   ↓
CU-02 Persistir
   ↓
CU-03 Modelar manualmente
   ↓
CU-04 Validar UML
   ↓
   ├─────────────> CU-06 Colaboración
   │
   ├─────────────> CU-08 Voz → UML
   │
   ├─────────────> CU-09 Imagen → UML
   │
   ├─────────────> CU-10/11 XMI
   │
   └─────────────> CU-12 UML → Relacional
                         ↓
                     CU-13 Backend
                         ↓
                     CU-14 API
                         ↓
                     CU-15 OpenAPI/Postman
                         ↓
                     CU-16 Domain Manifest
                         ↓
                  ┌──────┴───────┐
                  ↓              ↓
              CU-17 Web       CU-18 Mobile
                  └──────┬───────┘
                         ↓
                    CU-19..23 Voz
                         ↓
                    CU-24/25 IA local
```

---

# 16. Prioridad de implementación

## P0 — Imprescindible

- CU-01 Crear proyecto.
- CU-02 Guardar/abrir.
- CU-03 Diagramación manual.
- CU-04 Validación UML.
- CU-06 Colaboración.
- CU-12 UML → Relacional.
- CU-13 Backend Spring Boot.
- CU-14 API CRUD expresiva.
- CU-15 OpenAPI/Postman.
- CU-16 Domain Manifest.
- CU-17 Frontend web.
- CU-19 Consultas por voz.
- CU-20 Crear mediante voz.
- CU-24 STT local.
- CU-25 IA local.

## P1 — Muy importante

- CU-05 Undo/Redo.
- CU-07 Presencia.
- CU-08 Voz en ClassForge.
- CU-10/11 Enterprise Architect.
- CU-18 Mobile.
- CU-21 Operaciones relacionadas.
- CU-22 Actualizar por voz.
- CU-23 Eliminar por voz.
- CU-26 Auditoría.

## P2 — Completar después de asegurar el núcleo

- CU-09 Imagen → UML.
- refinamientos avanzados del canvas;
- experiencia visual avanzada;
- optimizaciones móviles.

La imagen es requisito del producto, pero técnicamente conviene implementarla después de garantizar que el modelo canónico y los generadores están estables.

---

# 17. Matriz de trazabilidad inicial

| Necesidad del producto | Caso(s) de uso | Módulo |
|---|---|---|
| Diagramar UML | CU-03, CU-04 | `diagram` |
| Guardar proyectos | CU-01, CU-02 | `project` |
| Colaboración | CU-06, CU-07 | `collaboration` |
| Voz para diagramar | CU-08, CU-24, CU-25 | `assistant` |
| Foto → UML | CU-09 | `integration/image` |
| Enterprise Architect | CU-10, CU-11 | `integration/xmi` |
| UML → BD | CU-12 | `generation/relational` |
| Spring Boot | CU-13, CU-14 | `generation/spring` |
| Postman | CU-15 | `generation/postman` |
| Frontend web | CU-17 | `generation/frontend` |
| Frontend mobile | CU-18 | `generation/frontend` |
| Voz en app generada | CU-19..CU-25 | template + assistant |
| Offline | CU-24, CU-25 | `assistant/runtime` |
| Auditoría | CU-26 | `project/history` |

---

# 18. Definition of Done por caso de uso

Un caso de uso no se considerará terminado hasta cumplir:

1. Implementación funcional.
2. Pruebas unitarias del dominio cuando corresponda.
3. Prueba de integración cuando exista infraestructura externa.
4. Manejo de errores.
5. Documentación actualizada.
6. Correspondencia con diagramas UML.
7. Evidencia de ejecución.
8. Commit(s) identificables dentro de la iteración PUDS.
9. Si afecta generación:
   - proyecto generado compila;
   - proyecto generado ejecuta;
   - prueba de extremo a extremo actualizada.

---

# 19. Prueba de regresión principal

Durante todo el desarrollo se mantendrá el dominio **Veterinaria** como escenario de referencia.

## Modelo orientativo

```text
Propietario
- id: Long
- nombre: String

Animal
- id: Long
- nombre: String
- especie: String
- fechaNacimiento: Date
- createdAt: DateTime

Veterinario
- id: Long
- nombre: String
- especialidad: String

Cita
- id: Long
- fecha: DateTime
- motivo: String
```

Relaciones:

```text
Propietario 1 ───── * Animal
Animal      1 ───── * Cita
Veterinario 1 ───── * Cita
```

## Escenarios de prueba

### E1

> Muéstrame los últimos cinco animales.

### E2

> Crea un animal llamado Luna de especie perro.

### E3

> Busca a Luna.

### E4

> Añade una cita para Luna mañana a las cuatro de la tarde.

### E5

> Muéstrame las citas de Luna.

### E6

> Cambia la especie de Luna a gato.

### E7

> Elimina la cita de mañana de Luna.

Debe solicitar confirmación.

---

# 20. Riesgos que deben resolverse temprano

## R1 — Modelo canónico insuficiente

Si el dominio UML está mal diseñado, afectará:

- canvas;
- XMI;
- colaboración;
- generación;
- IA.

**Mitigación:** E1 antes de construir funcionalidades avanzadas.

## R2 — Generación Spring incorrecta

**Mitigación:** prueba de generación durante Elaboración, no al final.

## R3 — Conflictos de colaboración

**Mitigación:** servidor autoritativo + revisiones + comandos.

## R4 — IA local demasiado lenta

**Mitigación:** prototipo con hardware objetivo durante Elaboración.

## R5 — IA produce operaciones inválidas

**Mitigación:** JSON estructurado + esquemas + validación determinista.

## R6 — XMI incompatible con Enterprise Architect

**Mitigación:** pruebas de round-trip con EA real.

## R7 — Frontend generado excesivamente complejo

**Mitigación:** template Angular reusable dirigido por Domain Manifest.

## R8 — Diagrama de clases no expresa lógica empresarial arbitraria

**Mitigación:** delimitar generación automática a:

- CRUD;
- filtros;
- ordenamiento;
- paginación;
- búsquedas;
- relaciones;
- operaciones derivables del modelo.

Las reglas empresariales que no puedan inferirse del modelo deberán requerir metadatos adicionales o quedar fuera del alcance automático.

---

# 21. Regla de trazabilidad para PUDS

Cada funcionalidad relevante debe poder seguirse así:

```text
Requisito
   ↓
Caso de uso
   ↓
Iteración PUDS
   ↓
Modelo/Diagrama UML
   ↓
Código
   ↓
Prueba
   ↓
Evidencia
```

Ejemplo:

```text
"El usuario puede consultar mediante voz"
        ↓
CU-19
        ↓
Construcción C9
        ↓
Diagrama de secuencia Assistant
        ↓
AssistantService + VoiceController
        ↓
IntegrationTest
        ↓
captura / log / commit
```

Esta relación deberá mantenerse durante todo el proyecto para facilitar posteriormente la documentación del proceso.

---

# 22. Orden práctico de desarrollo

Si se utiliza este documento como guía diaria, el orden recomendado es:

```text
01. Modelo canónico UML
02. Validadores
03. Commands
04. Persistencia de proyectos
05. API REST de proyectos/modelos
06. Canvas Angular
07. Undo/Redo
08. WebSocket + colaboración
09. Modelo relacional
10. Generador Spring Boot mínimo
11. Build automático del backend generado
12. CRUD genérico
13. Filtros/sort/paginación
14. OpenAPI
15. Postman
16. Domain Manifest
17. Template Angular generado
18. Web generado funcional
19. Capacitor / Android
20. whisper.cpp
21. LLM local
22. AssistantCommand
23. Voz para aplicaciones generadas
24. Voz para ClassForge
25. Imagen → UML
26. XMI import
27. XMI export
28. End-to-end veterinaria
29. Offline test
30. Hardening y transición
```

No se debe avanzar al siguiente bloque crítico si el anterior todavía no puede demostrarse de manera reproducible.

---

# 23. Resultado final esperado

Al concluir las iteraciones, el siguiente escenario debe funcionar:

```text
Usuario crea un proyecto
        ↓
Modela una veterinaria
        ↓
Otro colaborador modifica el mismo diagrama
        ↓
Los cambios se sincronizan
        ↓
Usuario genera aplicación
        ↓
ClassForge genera:
    backend Spring Boot
    OpenAPI
    Postman
    frontend Angular
    frontend Android
        ↓
Se ejecuta Spring Boot
        ↓
Se ejecuta frontend
        ↓
Usuario dice:
"crea un animal llamado Luna"
        ↓
STT local
        ↓
IA local
        ↓
AssistantCommand
        ↓
API generada
        ↓
Animal almacenado
        ↓
Usuario dice:
"muéstrame los últimos cinco animales"
        ↓
Consulta generada
        ↓
Resultado mostrado
```

Este flujo representa la prueba integradora principal de ClassForge y debe orientar las decisiones de implementación durante todo el PUDS.

---

# 24. Resumen de estrategia PUDS

## Inicio

Definir qué se construirá.

## Elaboración

Probar que la arquitectura puede soportarlo.

## Construcción

Completar funcionalidades incrementalmente.

## Transición

Demostrar que el sistema completo funciona de extremo a extremo.

La prioridad no será acumular pantallas o funcionalidades aisladas. La prioridad será mantener un **incremento ejecutable** que atraviese cada vez más partes del flujo completo de ClassForge.

<!-- AUTH-USE-CASES-V1 -->
# Addendum PUDS — Autenticacion y ownership

A partir de este incremento, el actor Modelador se interpreta como Usuario autenticado cuando accede a proyectos persistentes.

## Revision de CU-01 — Crear proyecto de modelado

### Precondicion nueva

- El usuario se encuentra autenticado.

### Postcondicion nueva

- El proyecto queda asociado persistentemente al `ownerId` del usuario autenticado.
- El proyecto no aparece en la biblioteca de otros usuarios salvo futura membresia aceptada.

### Regla

La propiedad se resuelve en backend. `localStorage` no almacena la lista de proyectos ni determina ownership.

## CU-28 — Registrar cuenta

**Actor principal:** Visitante  
**Prioridad:** Critica  
**Fase PUDS:** Elaboracion

### Objetivo

Crear una identidad local que pueda ser propietaria de proyectos.

### Flujo

1. Visitante abre landing.
2. Selecciona Crear cuenta.
3. Introduce nombre, correo y contrasena.
4. Backend valida.
5. Backend normaliza correo.
6. Contrasena se codifica con BCrypt.
7. Usuario se persiste.
8. Backend emite JWT.
9. Frontend establece sesion.
10. Usuario navega a sus proyectos.

## CU-29 — Iniciar sesion

**Actor principal:** Usuario registrado  
**Prioridad:** Critica  
**Fase PUDS:** Elaboracion

1. Usuario introduce correo y contrasena.
2. Backend localiza cuenta.
3. BCrypt valida contrasena.
4. Backend emite JWT.
5. Frontend almacena token de sesion.
6. Usuario accede a su biblioteca.

## CU-30 — Acceder a proyectos propios

**Actor:** Usuario autenticado  
**Prioridad:** Critica  
**Fase PUDS:** Elaboracion

1. Frontend envia JWT.
2. Spring Security valida firma y expiracion.
3. Se obtiene `currentUser.id`.
4. ProjectService consulta por owner.
5. Solo esos proyectos son devueltos.

Solicitar un UUID de otro usuario devuelve 404.

## CU-31 — Invitar colaborador mediante link (futuro)

**Actor principal:** Propietario  
**Actor secundario:** Usuario invitado  
**Prioridad:** Alta  
**Fase prevista:** Construccion

1. Propietario solicita invitacion.
2. Backend genera token seguro y expirable.
3. Se construye URL.
4. Invitado abre URL.
5. Inicia sesion o se registra.
6. Acepta.
7. Backend crea `ProjectMembership`.
8. Proyecto aparece en su biblioteca.
9. WebSocket autoriza sesion colaborativa mediante membresia.

Este caso queda documentado pero no implementado en este incremento.

<!-- CU02-PROJECT-DOCUMENT-V1 -->
# Revision de CU-02 — Documento versionado

CU-02 se implementa sobre usuarios autenticados y proyectos con ownership.

## Precondiciones

- Usuario autenticado.
- Usuario propietario del proyecto o, en una iteracion futura, miembro autorizado.

## Flujo de apertura

1. Usuario selecciona un proyecto.
2. Angular solicita `GET /api/projects/{id}`.
3. Backend valida el usuario.
4. Se recupera `ProjectDocument`.
5. Se migra el formato si corresponde.
6. Frontend reconstruye el workspace desde el documento canonico.

## Flujo de guardado

1. Frontend conserva una copia editable del documento.
2. Una modificacion marca el workspace como dirty.
3. Usuario solicita guardar.
4. Frontend envia documento + `baseRevision`.
5. Backend bloquea el proyecto durante la transaccion.
6. Compara `baseRevision` con la revision actual.
7. Si coinciden, persiste e incrementa revision.
8. Si no coinciden, devuelve 409.
9. Frontend informa conflicto y solicita recarga.

## Regla arquitectonica

El documento separa:

- semantica UML;
- layout visual.

JointJS no se utiliza como formato persistido.

## Postcondiciones

- El documento sobrevive recarga y reinicio del backend.
- La revision almacenada representa la version del documento.
- El proyecto continua aislado por ownership.
