# ClassForge

> Herramienta CASE colaborativa para modelado UML de clases y generación automática de aplicaciones backend/frontend operables mediante interfaz convencional, lenguaje natural y voz.

<!-- PRODUCT-STATUS-CYCLE3-CU13-2026-09-13 -->
## Estado de implementación al corte actual

Este documento describe el **producto objetivo**. El uso de futuro o la descripción de una capacidad no implica que dicha capacidad ya esté implementada.

Fuente normativa del estado: `../puds/current-status.md`.

| Área | Estado |
|---|---|
| Proyecto, auth, ownership y persistencia | IMPLEMENTADO |
| Modelo UML canónico y diagramación manual | IMPLEMENTADO |
| Validación, Command Bus y Undo/Redo | IMPLEMENTADO |
| Colaboración STOMP y presencia | IMPLEMENTADO |
| Assistant texto/voz para modificar UML | IMPLEMENTADO |
| llama.cpp y whisper.cpp locales | IMPLEMENTADO |
| Imagen a UML | IMPLEMENTADO |
| XMI / Enterprise Architect | IMPLEMENTADO — CU-10/CU-11 cerrados |
| UML a modelo relacional | IMPLEMENTADO — CU-12 cerrado |
| Generador Spring Boot/JPA | IMPLEMENTADO — CU-13 cerrado |
| API CRUD expresiva — CRUD simple / Sistema de Información con Auth | IMPLEMENTADO — CU-14 cerrado |
| OpenAPI/Postman | CERRADO — CU-15 implementado y aceptado |
| Domain Manifest | IMPLEMENTADO — CU-16 cerrado con schema v1 y acceptance determinista |
| Frontend web generado | IMPLEMENTADO — CU-17 cerrado |
| Frontend mobile generado | IMPLEMENTADO — CU-18 Flutter/Android cerrado |
| Voz sobre la aplicación generada | IMPLEMENTADO — CU-19 cerrado |
| Membresías e invitaciones | IMPLEMENTADO — CU-31 cerrado con membership, invitaciones, realtime/presencia/Assistant y hardening concurrente |

La separación anterior permite utilizar `product.md` como visión estable sin confundir alcance objetivo con estado actual.

---

## 1. Visión del producto

**ClassForge** es una aplicación web colaborativa orientada al diseño de diagramas de clases UML con foco en el modelado de aplicaciones respaldadas por bases de datos relacionales.

El producto permite construir un modelo de clases mediante distintos mecanismos de entrada, mantenerlo sincronizado entre varios usuarios y utilizarlo como fuente de verdad para generar automáticamente:

- un backend en Spring Boot;
- persistencia mediante JPA/Hibernate;
- una API REST documentada con OpenAPI;
- una colección de Postman;
- un frontend web;
- un frontend móvil;
- una interfaz de lenguaje natural y voz capaz de consumir el backend generado.

La aplicación debe funcionar **sin depender de Internet** para sus capacidades esenciales. La IA y el Speech-to-Text utilizados por el producto deben poder ejecutarse localmente.

---

## 2. Principio arquitectónico central

Todo ClassForge gira alrededor de un **modelo canónico propio** del dominio UML.

Ningún mecanismo de entrada debe manipular directamente el canvas, el código generado o la base de datos.

Todas las entradas convergen primero al mismo modelo interno:

```text
Diagramación manual ───────┐
                           │
Fotografía ────────────────┤
                           │
Audio + STT + IA ──────────┼──► CanonicalUmlModel
                           │
Enterprise Architect/XMI ──┘
```

Y todos los artefactos salen desde esa misma representación:

```text
CanonicalUmlModel
       │
       ├──► Canvas UML
       ├──► XMI / Enterprise Architect
       ├──► Modelo relacional
       ├──► Spring Boot + JPA
       ├──► OpenAPI
       ├──► Postman
       ├──► Frontend Web
       ├──► Frontend Mobile
       └──► Domain Manifest para asistente IA
```

Este modelo canónico debe ser la **fuente de verdad del proyecto**.

---

## 3. Aplicación principal

La aplicación principal de ClassForge será **web**.

### Stack previsto

- Angular 22
- TypeScript
- Angular Material
- JointJS Community para diagramación
- ELK.js para auto-layout
- Angular Signals
- RxJS
- STOMP/WebSocket para colaboración

La interfaz deberá ser responsive, pero no es requisito que la aplicación principal sea una app móvil nativa.

---

## 4. Diagramador UML

ClassForge debe permitir crear y editar diagramas de clases utilizando notación UML 2.5 o superior, tomando UML 2.5.1 como referencia concreta.

El dominio interno debe contemplar al menos:

- clases;
- atributos;
- operaciones cuando correspondan;
- visibilidad;
- tipos de datos;
- asociaciones;
- multiplicidades;
- generalización/herencia;
- agregación;
- composición;
- enums;
- paquetes si son necesarios;
- metadatos de generación.

El canvas es únicamente una representación visual del modelo canónico.

La posición de los nodos y otros datos visuales deben almacenarse por separado de la semántica UML cuando sea posible.

---

## 5. Formas de crear un diagrama

### 5.1. Diagramación manual

El usuario podrá:

- crear clases;
- editar clases;
- eliminar clases;
- añadir/eliminar/modificar atributos;
- crear relaciones;
- definir multiplicidades;
- definir herencia;
- mover los elementos visualmente;
- editar propiedades desde paneles o diálogos;
- utilizar undo/redo.

Las acciones de edición deberán implementarse mediante comandos reutilizables.

Ejemplos:

```text
CreateClassCommand
DeleteClassCommand
RenameClassCommand
AddAttributeCommand
RemoveAttributeCommand
CreateAssociationCommand
SetMultiplicityCommand
MoveNodeCommand
```

Los mismos comandos serán reutilizados por la interfaz manual, la colaboración y el asistente IA.

---

### 5.2. Creación a partir de fotografía

El usuario podrá proporcionar una fotografía o imagen de un diagrama de clases.

El flujo esperado será:

```text
Imagen
  ↓
Preprocesamiento opcional
  ↓
Modelo multimodal local
  ↓
Representación estructurada
  ↓
Validador UML
  ↓
CanonicalUmlModel
```

El resultado debe convertirse en elementos editables, no simplemente mostrarse como una imagen.

El reconocimiento debe intentar identificar:

- clases;
- atributos;
- relaciones;
- multiplicidades;
- herencia;
- otros elementos UML soportados.

Se podrá utilizar OpenCV/JavaCV únicamente como apoyo para tareas como:

- recorte;
- rotación;
- corrección de perspectiva;
- contraste;
- reducción de ruido.

La interpretación semántica podrá delegarse a un modelo multimodal local.

---

### 5.3. Creación mediante audio

El usuario podrá crear o modificar el diagrama mediante voz.

El flujo será:

```text
Micrófono
  ↓
Speech-to-Text local
  ↓
Texto
  ↓
Asistente IA local
  ↓
Comando estructurado
  ↓
Validador
  ↓
Command Bus
  ↓
CanonicalUmlModel
```

La IA nunca debe modificar directamente el diagrama.

Debe producir operaciones estructuradas pertenecientes a un conjunto cerrado de comandos.

Ejemplo:

```json
{
  "command": "ADD_ATTRIBUTE",
  "classId": "class-123",
  "attribute": {
    "name": "email",
    "type": "String",
    "visibility": "PRIVATE"
  }
}
```

---

### 5.4. Enterprise Architect

ClassForge deberá poder interoperar con **Sparx Systems Enterprise Architect**.

Formato principal previsto:

**XMI 2.1**

Flujos:

```text
Enterprise Architect
        ↓
      XMI 2.1
        ↓
    XmiImporter
        ↓
CanonicalUmlModel
```

```text
CanonicalUmlModel
        ↓
    XmiExporter
        ↓
      XMI 2.1
        ↓
Enterprise Architect
```

El primer objetivo será soportar correctamente el subconjunto UML utilizado por ClassForge en lugar de intentar cubrir todo XMI desde el primer día.

**Estado vigente:** CU-10/CU-11 implementan ese subconjunto mediante XMI 2.1, parser seguro, preview/apply y export determinista. Package se aplana; diagramas, profiles, tagged values y extensiones propietarias de EA quedan fuera del alcance actual.

Tecnologías previstas:

- Jackson XML;
- StAX;
- adaptador específico para XMI de Enterprise Architect.

Eclipse UML2 se considerará únicamente si el soporte XMI propio resulta insuficiente.

---

## 6. Colaboración en tiempo real

ClassForge debe ser una aplicación colaborativa.

Varios usuarios podrán abrir el mismo proyecto y visualizar los cambios de los demás en tiempo real.

### Transporte

- Spring WebSocket
- STOMP
- `@stomp/stompjs` en Angular

### Modelo de colaboración

El servidor Spring Boot será **autoritativo**.

Los clientes no enviarán continuamente el documento completo. Enviarán operaciones.

Ejemplo:

```json
{
  "operationId": "uuid",
  "projectId": "project-123",
  "userId": "user-7",
  "baseRevision": 42,
  "type": "ADD_ATTRIBUTE",
  "payload": {
    "classId": "animal",
    "name": "nombre",
    "dataType": "String"
  }
}
```

Flujo:

```text
Cliente
  ↓
WebSocket
  ↓
Servidor
  ↓
Validación
  ↓
Aplicación del comando
  ↓
Nueva revisión
  ↓
Persistencia
  ↓
Broadcast
  ↓
Todos los clientes
```

El proyecto podrá incorporar:

- presencia de usuarios;
- usuario conectado/desconectado;
- elemento seleccionado;
- indicador de quién está editando;
- cursores remotos opcionales;
- última modificación;
- revisionado del documento.

Para el alcance inicial no se requiere un CRDT completo como Yjs.

La primera estrategia será:

- servidor autoritativo;
- comandos;
- número de revisión;
- detección de operaciones obsoletas;
- resolución sencilla de conflictos;
- last-write-wins únicamente donde sea seguro.

---

## 7. Funcionamiento offline y en red local

"Offline" significa que ClassForge no deberá depender de Internet para funcionar.

Esto no impide utilizar una red local.

Escenario esperado:

```text
Laptop anfitriona
├── ClassForge backend
├── IA local
├── STT local
└── colaboración
       │
       │ Wi-Fi / hotspot / LAN
       │ SIN INTERNET
       ▼
otros navegadores / móviles / laptops
```

Por tanto:

- la colaboración puede funcionar en una LAN sin Internet;
- un equipo puede actuar como host;
- los demás dispositivos se conectan a ese host;
- los modelos de IA y STT pueden ejecutarse centralmente en la laptop anfitriona.

---

## 8. Transformación UML → modelo relacional

La transformación desde UML hacia base de datos debe ser **determinista** y no depender de IA.

Flujo:

```text
CanonicalUmlModel
       ↓
RelationalMapper
       ↓
RelationalModel
```

La IR relacional implementada por CU-12 es derivada, interna y efímera: no se persiste ni se expone por UI/API. Usa nombres singulares `snake_case`, PK compuestas explícitas sin IDs sustitutos, multiplicidades canónicas exactas, FK deterministas, `CASCADE` en composición y herencia JOINED. `CUSTOM` y enums permanecen diferidos hasta que el UML canónico modele explícitamente su semántica; no existe fallback implícito.

El modelo relacional deberá representar al menos:

```text
DatabaseModel
 ├── Tables
 │    ├── Columns
 │    ├── PrimaryKeys
 │    ├── ForeignKeys
 │    ├── UniqueConstraints
 │    └── Indexes cuando corresponda
 └── Relations
```

Se deberán documentar reglas para:

- clase → tabla;
- atributo → columna;
- identificador → primary key;
- asociación 1:1;
- asociación 1:N;
- asociación N:M;
- composición;
- herencia;
- enums;
- nulabilidad;
- restricciones.

Las reglas deberán estar sustentadas por la bibliografía utilizada para la transformación orientado a objetos → modelo relacional.

---

## 9. CU-13 - Generación de backend Spring Boot/JPA

**Estado actual: IMPLEMENTADO / CERRADO.** La evidencia ejecutable está en `../evidence/cu13/`.

CU-13 genera un proyecto reproducible desde el modelo relacional: Java 21, Spring Boot **4.0.8** fijado, Gradle Wrapper 9.2.0, Spring Web MVC bootstrap, Spring Data JPA, Hibernate, Jakarta Validation, entidades, repositorios, IDs simples y compuestos con `@IdClass`, relaciones directas, N:M, herencia JOINED y un context-load test.

H2 es la configuración default del proyecto generado. PostgreSQL es un profile adicional configurado por variables de entorno. No se usan versiones flotantes.

La generacion exige identificadores explicitos por defecto. Como ayuda de exportacion, si las unicas observaciones bloqueantes son clases raiz sin identifier y todas ellas tienen al menos un atributo, la UI puede ofrecer continuar usando el primer atributo de cada clase afectada como clave primaria solo para ese ZIP. La eleccion es explicita, no persiste cambios, no incrementa revision y no altera el `UmlModel` canonico. Clases sin atributos o cualquier otro error de tipos/relaciones/estructura mantienen el rechazo normal.

### Estructura específica CU-13

```text
src/main/java/.../
├── Application.java
├── entity/
└── repository/

src/main/resources/
├── application.yml
└── application-postgres.yml

src/test/java/.../
└── ApplicationTests.java
```

CU-13 no crea directorios vacíos para API futura.

### Plantillas CU-13

Apache FreeMarker es propiedad de ClassForge y renderiza el modelo preparado. Las templates conceptuales cubren proyecto/build, bootstrap, entity, `IdClass`, repository, configuración H2/PostgreSQL, context-load test, README y `.gitignore`. No se genera código complejo por concatenación manual.

### Estructura objetivo acumulada tras CU-13/CU-14

```text
src/main/java/.../
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── exception/
└── config/
```

Los artefactos `service.java.ftl`, `controller.java.ftl`, `dto.java.ftl` y `exception-handler.java.ftl` pertenecen al alcance de CU-14, no al output exclusivo de CU-13.

---

## 10. CU-14 - API CRUD expresiva y modos de aplicación

Cada entidad generada deberá incluir, cuando corresponda:

- CREATE;
- READ by id;
- UPDATE;
- DELETE;
- LIST;
- paginación;
- ordenamiento;
- filtros;
- búsquedas por propiedades;
- conteo;
- navegación de relaciones.

CU-14 cerrado incluye controllers, services, DTOs, mapping, CREATE/READ/UPDATE/DELETE/LIST, búsqueda libre, filtros `filter.<campo>`, ordenamiento, paginación, conteo y navegación por IDs de relaciones. La API es suficientemente expresiva para servir de base a los casos posteriores sin generar un endpoint específico por frase.

### 10.1 Dos modos de generación

La UX de CU-14 debe presentar dos modos **explícitos y mutuamente excluyentes**, no un checkbox ambiguo:

#### CRUD simple

Genera la API expresiva sobre el proyecto Spring Boot/JPA de CU-13 sin seguridad de aplicación. No agrega Spring Security, login, JWT ni reglas especiales de credenciales. Es la opción directa para prototipos, APIs internas o sistemas donde la autenticación se resolverá fuera de ClassForge.

#### Sistema de Información con Auth

Genera la misma API CRUD y añade autenticación. Al seleccionar este modo, la UI debe mostrar tres campos obligatorios y dependientes:

1. **Tabla/entidad de autenticación.** Solo pueden elegirse tablas de `RelationalModel` con origen `UML_CLASS`; una tabla N:M de unión no es candidata.
2. **Usuario/login.** Se elige un atributo de la entidad seleccionada.
3. **Contraseña.** Se elige otro atributo de la misma entidad.

La UI muestra nombres comprensibles, pero la solicitud de generación debe transportar identidades estables de la clase/atributos de origen. Los selectores de usuario y contraseña se recalculan al cambiar la tabla de autenticación y nunca permiten elegir columnas derivadas de FK, PK heredada o join-table como si fueran atributos UML.

La configuración Auth pertenece únicamente al target generado: no agrega estereotipos al UML, no persiste `RelationalModel`, no modifica `ProjectDocument` y no incrementa la revisión.

El resultado Auth debe incluir como mínimo:

- Spring Security;
- `PasswordEncoder` para no persistir credenciales en texto plano;
- endpoint de login;
- emisión/validación JWT;
- protección stateless de todo el API salvo `/api/auth/login` y el bootstrap inicial;
- DTOs de respuesta que omitan la contraseña;
- tratamiento seguro de writes sobre el atributo seleccionado como contraseña.

La selección debe validarse antes de renderizar. Referencias stale, tabla inexistente, atributos ajenos a la entidad, columnas sintéticas/relacionales o usuario y contraseña apuntando al mismo atributo provocan rechazo fail-closed y ningún ZIP parcial.

Este perfil no debe confundirse con la autenticación de ClassForge: las cuentas OWNER/EDITOR controlan quién puede **generar** el artefacto; la tabla/entidad elegida controla quién podrá **iniciar sesión en la aplicación generada**.

Ejemplos de consultas deseadas:

```text
"Muéstrame los últimos 5 animales"
"Busca los animales llamados Luna"
"Enséñame las citas de hoy"
"Crea un animal llamado Firulais"
"Cambia el nombre de Luna a Lunita"
"Elimina la cita de mañana"
"Muéstrame las citas de Luna"
```

---

## 11. Auditoría estándar

Para que expresiones como "últimos", "recientes" o "modificados recientemente" tengan una semántica estable, ClassForge deberá permitir generar campos de auditoría.

Ejemplo mediante metadato/estereotipo:

```text
<<auditable>>
Animal
```

Generación esperada:

```text
createdAt
updatedAt
```

Esto puede mapearse a Spring Data Auditing.

---

## 12. CU-15 - OpenAPI y Postman

CU-15 fija un contrato reproducible para documentar y ejercitar exactamente la API que CU-14 ya genera. No requiere iniciar la aplicación generada ni ejecutar `springdoc`, `openapi-to-postman` u otra herramienta externa durante la exportación.

Flujo objetivo:

```text
SpringApiGenerationPlan (CU-14)
       |
       v
SpringApiContract / OpenAPI model canónico
       |                         |
       v                         v
openapi.yaml              postman_collection.json
       \_________________________/
                  |
                  v
           mismo ZIP CU-13/14
```

`openapi.yaml` utiliza OpenAPI 3.0.3 y es el contrato externo normativo de la API generada. La colección Postman se renderiza desde el mismo contrato canónico; no se mantiene manualmente como una segunda especificación.

En modo `SIMPLE_CRUD`, ambos artefactos describen CRUD, list/count, búsqueda, filtros, sorting, paginación y relaciones sin seguridad.

En modo `AUTH_INFORMATION_SYSTEM`, OpenAPI añade `bearerAuth` y marca el API como protegido salvo `POST /api/auth/bootstrap` y `POST /api/auth/login`. La contraseña seleccionada es de escritura y nunca forma parte de schemas de respuesta. Postman añade `baseUrl` y `jwt`, guarda automáticamente `accessToken` después de bootstrap/login y usa `Bearer {{jwt}}` en requests protegidos.

CU-15 entrega solamente:

- `openapi.yaml`;
- `postman_collection.json`.

CU-16 añade `domain-manifest.json` schema v1 al mismo ZIP. Conserva UUIDs estables, IDs, atributos, relaciones, herencia, capacidades, operationIds y Auth, y se valida contra el contrato CU-15. CU-17 y CU-18 son clientes hermanos: generan respectivamente `frontend/` Angular y `mobile/` Flutter desde el mismo Domain Manifest/API canónica. CU-19 añade un Assistant compartido por ambos clientes; el Spring generado reutiliza Whisper/Qwen locales y mantiene la autoridad de grounding/ejecución.

---

## 12.1 Assistant de la aplicación generada (CU-19)

CU-19 fusiona los antiguos CU-19..23 en una sola capacidad de interacción con datos por chat y voz. El backend Spring generado llama a Whisper en `127.0.0.1:8093` y Qwen/llama.cpp en `127.0.0.1:8092`; Angular y Flutter nunca contactan directamente esos runtimes. Qwen propone native tool calls y Java valida contra Domain Manifest antes de consultar o mutar. QUERY/GET/COUNT ejecutan directamente; CREATE/UPDATE/relaciones/DELETE requieren preview sanitizado y un token opaco consumido por `apply`.

CU-20, CU-21, CU-22 y CU-23 se conservan como trazabilidad pero quedan absorbidos por CU-19.

---

## 13. Generación de frontend

El frontend generado es distinto de la aplicación web principal ClassForge.

ClassForge debe poder generar una interfaz para utilizar el backend generado.

El docente podrá solicitar un frontend web o móvil, por lo que el generador debe contemplar ambas salidas.

### Estrategia elegida

Mantener **dos clientes generados independientes sobre el mismo contrato canónico**:

- Angular para Web;
- Flutter para Mobile/Android;
- Spring generado como backend común, incluido el Assistant CU-19.

Flujo:

```text
Domain/Application Model
       ↓
Angular Frontend Generator
       ↓
Angular application
       │
       ├──► Web build
       │
       └──► Flutter → Android
```

La ruta mobile vigente usa un generador Flutter independiente. React Native, Vue Native y otros stacks alternativos permanecen fuera del alcance.

---

## 14. Frontend CRUD generado

A partir de una entidad como:

```text
Animal
- id: Long
- nombre: String
- fechaNacimiento: Date
- vacunado: Boolean
- especie: Especie
```

ClassForge podrá inferir controles de formulario.

Ejemplo de reglas:

| Tipo de dominio | Componente UI |
|---|---|
| String | input text |
| Integer / Long | input number |
| Decimal | input number |
| Boolean | checkbox / switch |
| Date | date picker |
| DateTime | datetime picker |
| Enum | select |
| N:1 | select / autocomplete |
| 1:N | listado / tabla relacionada |
| Text | textarea |

Cada entidad podrá generar de forma estándar:

- listado;
- detalle;
- creación;
- edición;
- eliminación;
- búsqueda;
- filtros;
- navegación de relaciones.

El resultado será prioritariamente un frontend CRUD funcional, no una UI específica de negocio diseñada manualmente.

---

## 15. Template frontend reutilizable

Para reducir la cantidad de código generado, el frontend podrá basarse en un template reutilizable.

Ejemplo:

```text
generated-app-template/
├── assistant/
├── crud/
├── forms/
├── tables/
├── routing/
├── api/
└── domain/
```

El generador producirá principalmente:

- configuración;
- Domain Manifest;
- modelos;
- rutas;
- cliente de API;
- metadatos de formularios/tablas.

Una veterinaria y una biblioteca podrán compartir el mismo motor de frontend cambiando únicamente el dominio generado.

---

## 16. Domain Manifest

Junto con cada aplicación generada se creará `domain-manifest.json`: un contrato semántico compacto para el generador de frontend y el futuro asistente de la aplicación.

CU-16 fija schema `1.0` y la siguiente autoridad:

```text
SpringGenerationModel
SpringApiGenerationPlan
SpringApiContract
        |
        v
DomainManifestPlan
        |
        v
domain-manifest.json
```

El manifest **no** será reconstruido desde OpenAPI ni será una fuente manual independiente. Su valor diferencial frente a OpenAPI es preservar semántica y trazabilidad del dominio ClassForge.

Ejemplo orientativo de schema v1:

```json
{
  "schemaVersion": "1.0",
  "generationMode": "AUTH_INFORMATION_SYSTEM",
  "api": {
    "baseUrl": "http://localhost:8080",
    "openApiFile": "openapi.yaml",
    "postmanFile": "postman_collection.json"
  },
  "authentication": {
    "enabled": true,
    "scheme": "BEARER_JWT",
    "entityId": "7e0d...",
    "usernameAttributeId": "aa11...",
    "passwordAttributeId": "bb22...",
    "tokenVariable": "jwt",
    "expiresInSeconds": 3600,
    "bootstrapOperationId": "bootstrapAuthentication",
    "loginOperationId": "loginAuthentication"
  },
  "entities": [
    {
      "id": "1f23...",
      "logicalName": "Animal",
      "codeName": "Animal",
      "tableName": "animal",
      "endpoint": "/api/animal",
      "displayName": "Animal",
      "aliases": [],
      "identifier": {
        "kind": "SIMPLE",
        "fields": [
          { "attributeId": "31ce...", "name": "id", "type": "UUID" }
        ]
      },
      "attributes": [],
      "relations": [],
      "operationIds": ["listAnimal", "createAnimal", "getAnimal"]
    }
  ],
  "operations": []
}
```

El manifiesto deberá contener al menos:

- versión de schema y modo de generación;
- UUIDs estables de clases, atributos y relaciones cuando existan;
- entidades, IDs simples/compuestos y herencia;
- atributos con tipos semánticos, nullability, mutabilidad y capacidades;
- relaciones y targets estables;
- endpoints/capacidades mediante los mismos `operationId` de CU-15;
- metadata Auth cuando corresponda;
- aliases explícitos, inicialmente vacíos si el modelo no los provee.

Tipos semánticos iniciales:

```text
STRING | INTEGER | LONG | DECIMAL | BOOLEAN | DATE | DATETIME | UUID
```

No se inferirán plurales ni aliases con heurísticas/LLM durante CU-16. `displayName` parte del nombre lógico UML.

En Auth, password será sensible y write-only: nunca aparecerá como dato de respuesta ni como campo searchable/filterable/sortable.

El manifest será un artefacto efímero dentro del ZIP generado; no se persiste dentro de `ProjectDocument`.

---

## 17. Asistente de lenguaje natural en la aplicación generada

La característica central del frontend generado será la posibilidad de controlar el backend mediante lenguaje natural y voz.

Ejemplo del dominio veterinario:

```text
Animal
Cita
Veterinario
```

El usuario podrá decir:

```text
"Quiero ver los últimos cinco animales."
```

El flujo será:

```text
Audio
  ↓
STT
  ↓
Texto
  ↓
LLM + Domain Manifest
  ↓
AssistantCommand
  ↓
Validador
  ↓
API Executor
  ↓
Spring Boot generado
  ↓
Resultado
```

El LLM no deberá generar URLs arbitrarias ni ejecutar código.

---

## 18. Lenguaje intermedio del asistente

Se definirá un lenguaje cerrado de operaciones.

Operaciones iniciales:

```text
LIST
GET
SEARCH
CREATE
UPDATE
DELETE
COUNT
```

Estructura aproximada:

```json
{
  "operation": "LIST",
  "entity": "Animal",
  "filters": [],
  "sort": {
    "field": "createdAt",
    "direction": "DESC"
  },
  "limit": 5
}
```

El frontend/backend deberá validar:

- que la entidad exista;
- que la operación esté permitida;
- que los campos existan;
- que los tipos coincidan;
- que los filtros sean válidos;
- que no se soliciten operaciones fuera del manifiesto.

Solo después se ejecutará la petición REST correspondiente.

---

## 19. Operaciones compuestas

El asistente podrá generar pequeños planes compuestos cuando una operación dependa de otra.

Ejemplo:

```text
"Añade una cita para Luna mañana a las 4 de la tarde."
```

Posible representación:

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
        "animalId": "$step1.result.id",
        "fecha": "2026-08-28T16:00:00"
      }
    }
  ]
}
```

El motor de ejecución deberá validar cada paso antes de ejecutarlo.

---

## 20. Alcance semántico de la generación

ClassForge puede generar automáticamente de forma razonable:

| Funcionalidad | Alcance |
|---|---:|
| Entidades JPA | Sí |
| Tablas y relaciones | Sí |
| Repositories | Sí |
| Servicios CRUD | Sí |
| Controllers REST | Sí |
| DTOs | Sí |
| Validaciones básicas | Sí |
| OpenAPI | Sí |
| Postman | Sí |
| Cliente TypeScript | Sí |
| Listados frontend | Sí |
| Formularios CRUD | Sí |
| Navegación | Sí |
| Web | Sí |
| Android mediante Flutter | Sí |
| Consultas por voz | Sí |
| Filtrado/ordenación | Sí |
| Consultas sobre relaciones | Sí |
| Lógica empresarial arbitraria no modelada | No |
| Reglas de negocio inexistentes en el modelo | No |

Un diagrama de clases no contiene suficiente información para generar automáticamente cualquier comportamiento empresarial imaginable.

ClassForge se enfocará inicialmente en **operaciones estructurales derivables del modelo de dominio**.

---

## 21. Metadatos de generación y perfil UML

Para ampliar la capacidad del generador sin abandonar UML, ClassForge podrá soportar metadatos y/o un perfil UML propio.

Ejemplos:

```text
<<entity>>
<<auditable>>
<<readOnly>>
<<searchable>>
<<crud>>
```

Propiedades:

```text
{ searchable = true }
{ sortable = true }
{ defaultSort = DESC }
{ required = true }
{ unique = true }
```

Estos metadatos podrán utilizarse para controlar:

- generación backend;
- generación frontend;
- búsqueda;
- auditoría;
- validaciones;
- comportamiento del asistente.

---

## 22. IA local

La IA debe ser local y desacoplada del frontend principal.

### Runtime previsto

- llama.cpp

### Estrategia de modelos

Planner textual oficial:

- Qwen2.5-3B-Instruct Q4_K_M mediante llama.cpp con `--jinja`;
- native function calling para seleccionar tools UML semánticas;
- Java conserva autoridad sobre referencias existentes, UUID, grounding, validación y Apply.

Modelo multimodal para CU-09:

- baseline de evaluación: Qwen3-VL-2B-Instruct Q4_K_M + mmproj Q8_0 mediante llama.cpp en `127.0.0.1:8094`;
- challenger: Qwen3-VL-4B-Instruct Q4_K_M + mmproj Q8_0, sujeto a benchmark local;
- produce `VisionUmlProposal` bajo JSON Schema y converge en la misma IR/Command Bus, nunca escribe directamente `ProjectDocument`;
- el runtime visual permanece separado del Qwen textual de CU-08 para evitar un cutover no justificado.

La selección final entre 2B y 4B depende de la evidencia regression/holdout, VRAM y latencia de C2-cu09-002; no se hereda de la elección textual de CU-08.

---

## 23. Speech-to-Text local

Tecnología prevista:

- whisper.cpp

Configuraciones iniciales:

- laptop: `base` o `small`;
- dispositivos móviles: `tiny` o `base` si se decide ejecutar on-device.

El objetivo principal no es realizar transcripción profesional extensa, sino interpretar comandos cortos y claros.

---

## 24. Estrategia de ejecución de IA

El modo principal será **workstation/local server**.

```text
Laptop anfitriona
├── Spring Boot
├── llama.cpp
├── whisper.cpp
└── backend generado
      │
      │ LAN / hotspot
      ▼
Web / Android
```

Esto permite:

- operar sin Internet;
- centralizar modelos pesados;
- utilizar móviles de recursos medios como clientes;
- mantener una única implementación del asistente.

### On-device mobile

Será una mejora opcional.

En móvil se podrá investigar:

- whisper.cpp tiny/base;
- llama.cpp Android;
- modelos alrededor de 1B cuantizados.

No se considerará requisito que un teléfono de gama media ejecute el modelo multimodal pesado.

---

## 25. Backend del asistente generado

Cada backend generado podrá incorporar endpoints estándar para el asistente.

Ejemplo:

```text
POST /api/assistant/execute
POST /api/assistant/voice
```

`/execute` podrá recibir texto:

```json
{
  "text": "muéstrame los últimos cinco animales"
}
```

`/voice` podrá recibir audio y ejecutar:

```text
Audio
  ↓
whisper.cpp
  ↓
LLM
  ↓
AssistantCommand
  ↓
CommandValidator
  ↓
API/domain services
  ↓
resultado
```

El asistente deberá consumir únicamente capacidades declaradas por el dominio generado.

---

## 26. Seguridad y validación del asistente

La IA nunca será considerada una fuente confiable de instrucciones ejecutables.

Toda salida del modelo deberá pasar por validación estructural.

Principios:

1. Salida estructurada.
2. Esquema cerrado.
3. Allow-list de operaciones.
4. Allow-list de entidades y campos.
5. Validación de tipos.
6. Validación de relaciones.
7. Confirmación opcional para acciones destructivas.
8. Sin SQL generado directamente por el LLM.
9. Sin ejecución de código generado por el LLM.
10. Sin URLs arbitrarias suministradas por el LLM.

---

## 27. Persistencia de proyectos ClassForge

### Decision vigente

ClassForge utiliza **Spring Data JPA/Hibernate** para persistir proyectos.

En desarrollo se utiliza **H2 en archivo**. PostgreSQL permanece como destino previsto para despliegues posteriores.

El agregado persistido conserva:

- identidad y metadata;
- ownership;
- `ProjectDocument`;
- `UmlModel`;
- `DiagramLayout`;
- revision;
- timestamps.

La representacion fisica actual reutiliza una columna historica denominada `uml_model`, aunque su significado logico es `ProjectDocument`. Esto permitio conservar los datos H2 existentes durante la evolucion CU-01 → CU-02.

Los tests Spring utilizan H2 en memoria.

### Formato portable futuro

Un archivo `*.classforge` sigue siendo una posibilidad futura de exportacion, importacion o backup.

No es el mecanismo primario de persistencia vigente.

Si se implementa, debera adaptar el mismo modelo canonico y no convertirse en una segunda fuente de verdad.

```json
{
  "formatVersion": "1.0",
  "project": {},
  "umlModel": {},
  "layout": {},
  "generationMetadata": {},
  "revision": 42
}
```

---
## 28. Stack consolidado

### Aplicación ClassForge

```text
Frontend
- Angular 22
- TypeScript
- Angular Material
- JointJS Community
- ELK.js
- Signals
- RxJS
- @stomp/stompjs

Backend
- Java 21
- Spring Boot 4.0.8
- Gradle
- Spring Web MVC
- Spring WebSocket
- STOMP
- Jackson
- Jackson XML
- StAX
- FreeMarker

IA / STT
- llama.cpp
- Qwen2.5-3B-Instruct Q4_K_M para texto/voz -> tools UML
- VLM local para imagen -> UML mediante llama.cpp; CU-09 cerrado con Qwen3-VL-4B + OpenCV + Java y evidencia de calibración/aceptación consolidada
- whisper.cpp

Interoperabilidad
- UML 2.5.1
- XMI 2.1
- Enterprise Architect
```

### Aplicaciones generadas

```text
Backend
- Java 21
- Spring Boot 4.0.8
- Spring Data JPA
- Hibernate
- Jakarta Validation
- H2 default
- PostgreSQL profile mediante variables de entorno
- OpenAPI/Postman a partir de CU-15

Frontend
- Angular
- Angular Material
- cliente TypeScript generado desde OpenAPI

Mobile
- mismo Angular
- Flutter
- Android

Artefactos
- OpenAPI 3
- Postman Collection
- Domain Manifest
```

---

## 29. Testing previsto

### Backend

- JUnit 5
- Mockito
- Spring Boot Test

### Frontend

- tooling de testing de Angular

### End-to-end

- Playwright

### Generadores

Los generadores deberán disponer de pruebas que verifiquen al menos:

- archivo generado;
- sintaxis esperada;
- compilación del proyecto generado;
- relaciones correctas;
- OpenAPI generado;
- cliente Angular generado;
- comandos del asistente válidos.

---

## 30. Flujo de demostración objetivo

Ejemplo: veterinaria.

### 1. Modelado

```text
Animal
Cita
Veterinario
```

### 2. Generación

El usuario pulsa **Generar**.

ClassForge produce:

```text
output/
├── src/                      # Spring Boot generado
├── frontend/                 # Angular generado
├── mobile/                   # Flutter generado
├── openapi.yaml
├── postman_collection.json
└── domain-manifest.json
```

### 3. Ejecución

Se inicia el backend Spring Boot generado.

Se abre:

- frontend web; o
- frontend Android.

### 4. Operación por voz

Usuario:

```text
"Crea un animal llamado Luna, especie perro."
```

Resultado:

```text
Animal creado correctamente.
```

Después:

```text
"Muéstrame los últimos cinco animales."
```

La aplicación consulta el backend y presenta los resultados.

Después:

```text
"Añade una cita para Luna mañana a las cuatro de la tarde."
```

El asistente:

1. localiza a Luna;
2. obtiene su id;
3. crea la cita relacionada;
4. muestra el resultado.

Todo esto debe ser posible sin escribir manualmente endpoints específicos para Luna, animales o citas: el comportamiento se deriva del modelo y de las capacidades estándar generadas.

---

## 31. MVP prioritario

El MVP debe demostrar de extremo a extremo:

1. Crear clases manualmente.
2. Crear atributos y relaciones.
3. Mantener un modelo canónico.
4. Colaboración en tiempo real entre dos clientes.
5. Guardar/abrir proyecto.
6. Transformar UML → modelo relacional.
7. Generar Spring Boot + JPA.
8. Generar CRUD REST.
9. Generar paginación, filtros y sorting.
10. Generar OpenAPI.
11. Generar colección Postman.
12. Generar Domain Manifest.
13. Generar frontend Angular CRUD.
14. Generar y validar el frontend mobile Android mediante Flutter.
15. Ejecutar un comando textual sobre el backend generado.
16. Ejecutar el mismo comando mediante voz + STT.
17. Operar el backend generado mediante lenguaje natural.
18. Importar/exportar un subconjunto XMI compatible con Enterprise Architect.
19. Funcionar sin Internet.

La generación desde fotografía puede desarrollarse después de que este pipeline sea estable, ya que presenta más incertidumbre que la generación determinista.

---

## 32. Orden de implementacion

### Orden arquitectonico recomendado originalmente

```text
1. CanonicalUmlModel
2. Command Bus
3. Angular canvas manual
4. Persistencia local de proyectos
5. WebSocket collaboration
6. UML → RelationalModel
7. Spring Boot generator
8. Backend generado compilable
9. OpenAPI
10. Postman
11. Domain Manifest
12. Angular frontend generator
13. Generic CRUD UI
14. AssistantCommand schema
15. Text → AssistantCommand
16. Assistant execution engine
17. whisper.cpp
18. Voice → command
19. Flutter / Android
20. Enterprise Architect XMI
21. Imagen → UML
```

La regla central se mantiene: IA, vision y generacion no deben preceder a un modelo canonico solido.

### Orden realmente ejecutado — Ciclo 1

La ejecución real difirió del roadmap inicial para reducir riesgos arquitectónicos.

```text
1. CU-01 proyecto persistible
2. autenticación y ownership
3. CU-02 ProjectDocument + revisión
4. CU03-001 dominio UML tipado
5. CU03-002 JointJS
6. CU03-003 relaciones e inspector
7. CU-04 validación explícita
8. CU-05 Command Bus + Undo/Redo
9. CU06-001 autoridad STOMP backend
10. CU06-002 sincronización Angular
11. CU06-003 Undo/Redo colaborativo
12. CU07-001 presencia
13. CU08-001 texto + semantic plan + BATCH
14. CU08-002 voz + whisper.cpp
15. CU08-003 health + hardening colaborativo
```

CU-24 STT local y CU-25 IA local se adelantaron como infraestructura de CU-08.

La decisión no cambia la visión objetivo: primero se estabilizó la fuente de verdad y las rutas de mutación; generación e integraciones permanecen para ciclos posteriores.

### Arquitectura actual

```text
adaptador
   ↓
UmlCommand
   ↓
UmlCommandBus
   ↓
UmlCommandExecutor
   ↓
ProjectDocument
```

Esta capa ya es reutilizada por CU-06 colaboración y CU-08 IA/voz. Los siguientes adaptadores y generadores deben continuar utilizando la misma ruta de comandos.

No se debe introducir otra ruta que modifique directamente los arrays del modelo canonico.

---
## 33. Definición resumida del producto

**ClassForge** es una herramienta CASE colaborativa y offline-first que permite diseñar modelos UML de clases mediante edición manual, voz, imágenes o Enterprise Architect, y transformar esos modelos en aplicaciones funcionales compuestas por un backend Spring Boot/JPA, una API REST/OpenAPI, una colección Postman y un frontend Angular web y un frontend Flutter mobile con Android formalmente validado.

Las aplicaciones generadas incorporan un asistente de lenguaje natural y voz capaz de realizar operaciones sobre el dominio generado —como crear, consultar, actualizar, eliminar, filtrar, ordenar y navegar relaciones— sin requerir que dichas operaciones hayan sido programadas manualmente para cada dominio.

La generación se limita deliberadamente a comportamiento que pueda derivarse del modelo y de metadatos declarativos. La lógica empresarial no expresada en el modelo no se inventará automáticamente.

---

## 34. Nombre

**ClassForge**

### Significado

- **Class**: el modelo parte de diagramas de clases UML.
- **Forge**: el sistema transforma ese modelo en software ejecutable.

Tagline opcional:

> **Model it. Generate it. Talk to it.**

<!-- AUTH-OWNERSHIP-V1 -->
# Actualizacion de producto — Autenticacion, propiedad y colaboracion

## Identidad de usuario

ClassForge incorpora cuentas locales de usuario mediante registro e inicio de sesion.

Cada cuenta posee UUID, nombre visible, correo unico, hash de contrasena y fecha de creacion.

Las contrasenas nunca se almacenan en texto plano. El backend utiliza Spring Security y BCrypt.

## Sesion

La API utiliza autenticacion Bearer mediante JWT firmado por el backend.

El frontend puede persistir el access token en `localStorage`. Este almacenamiento representa exclusivamente la sesion del navegador; no determina la propiedad ni la existencia de proyectos.

Borrar `localStorage` implica cerrar la sesion local. Al autenticarse nuevamente con la misma cuenta, el backend vuelve a entregar los proyectos del usuario.

## Propiedad de proyectos

Cada proyecto nuevo posee un `ownerId` persistido en la base de datos.

`GET /api/projects` devuelve los proyectos propios y aquellos para los que el usuario posee `ProjectMembership` EDITOR.

El UUID por sí solo no concede acceso: la política central resuelve OWNER, EDITOR o NONE.

## Membresías e invitaciones — Ciclo 2

ClassForge conserva `Project.ownerId` como ownership explícito e incorpora `ProjectMembership` para colaboradores EDITOR.

Las invitaciones son internas y persistentes por correo normalizado; no se envían emails reales y no existen links/tokens públicos.

Flujo cerrado con C2-cu31-003:

1. OWNER introduce el correo del colaborador.
2. Se crea `ProjectInvitation` PENDING.
3. El destinatario puede registrarse después con ese mismo correo.
4. Su bandeja interna muestra la invitación.
5. Aceptar crea `ProjectMembership` EDITOR y marca la invitación ACCEPTED de forma transaccional.
6. El proyecto aparece en su biblioteca como compartido.
7. Decline/Cancel no conceden acceso.

Modelo:

- `Project -> ownerId`
- `ProjectMembership -> projectId + userId + role=EDITOR`
- `ProjectInvitation -> projectId + invitedEmail + invitedByUserId + status + timestamps`

No se incluye todavía eliminación de membership activa, VIEWER, roles personalizados, SMTP ni transferencia de ownership.

C2-cu31-003 valida OWNER/EDITOR/NONE en STOMP operations, presence y Assistant, y serializa invite/accept por proyecto para evitar duplicados concurrentes. CU-31 queda CERRADO. Posteriormente, CU-09 también se cierra dentro del Ciclo 2 mediante el pipeline visual híbrido documentado en `docs/architecture/vision-input-pipeline.md`.

## Endpoints iniciales

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/projects` autenticado
- `POST /api/projects` autenticado
- `GET /api/projects/{id}` autenticado y limitado por OWNER/EDITOR
- `POST /api/projects/{id}/invitations` OWNER
- `GET /api/projects/{id}/invitations` pendientes administrativas, OWNER
- `DELETE /api/projects/{id}/invitations/{invitationId}` cancelar PENDING, OWNER
- `GET /api/projects/{id}/collaborators` OWNER/EDITOR
- `GET /api/project-invitations` invitaciones PENDING del correo autenticado
- `POST /api/project-invitations/{id}/accept`
- `POST /api/project-invitations/{id}/decline`

## Landing publica

La ruta raiz de ClassForge es publica y presenta las capacidades centrales del producto y enlaza a registro e inicio de sesion.

<!-- PROJECT-DOCUMENT-V1 -->
# Actualizacion de producto — Documento de proyecto

ClassForge persiste cada proyecto como un `ProjectDocument` versionado.

El documento contiene dos partes independientes:

- `UmlModel`: significado UML.
- `DiagramLayout`: informacion visual.

Esta separacion garantiza que:

- la UI no define el dominio;
- JointJS puede reemplazarse sin perder modelos;
- importacion XMI, IA y voz operan sobre el mismo dominio;
- la colaboracion puede sincronizar operaciones semanticas;
- el layout puede cambiar sin reinterpretar UML.

Cada guardado del documento utiliza revision optimista y puede detectar modificaciones concurrentes.

La iconografia de ClassForge utiliza Material Symbols Rounded self-hosted mediante npm para mantener disponibilidad offline.

<!-- TYPED-UML-DOMAIN-V1 -->
# Actualizacion de producto — Modelo UML tipado

ClassForge deja de representar clases y atributos como objetos JSON genericos.

El modelo canonico incorpora tipos explicitos para clases, atributos, tipos de datos, visibilidad, identificadores, relaciones, multiplicidades y layout.

CU03-001 ofrece un editor estructurado Angular para clases y atributos. CU03-002 agregara JointJS como proyeccion del mismo ProjectDocument.

Las respuestas HTTP 400 del backend son estructuradas y contienen violations apropiadas para UI, pruebas y trazabilidad.

<!-- JOINTJS-CANVAS-V1 -->
# Actualizacion de producto — Canvas UML

CU03-002 incorpora un canvas JointJS open-source (`@joint/core`) como representacion interactiva del modelo UML canonico.

La aplicacion permite:

- visualizar clases y atributos;
- crear clases desde el canvas;
- editar clases mediante doble interaccion;
- mover elementos;
- zoom;
- pan;
- ajustar el contenido a la vista.

El layout se persiste de forma independiente a la semantica UML.

Las transformaciones de viewport no se guardan.

JointJS nunca sustituye a ProjectDocument como fuente de verdad.

<!-- UML-RELATIONSHIPS-INSPECTOR-V1 -->
# Actualizacion de producto — Relaciones UML manuales

ClassForge completa el editor manual de diagramas con:

- asociaciones;
- agregaciones;
- composiciones;
- generalizaciones;
- multiplicidades;
- inspector contextual.

La creacion de relaciones se realiza seleccionando origen y destino directamente sobre el canvas y configurando despues su semantica en Angular Material.

JointJS proyecta los links y labels, mientras ProjectDocument continua siendo la fuente de verdad.

El backend protege la integridad del modelo, incluyendo deteccion de ciclos de generalizacion.

<!-- EXPLICIT-UML-VALIDATION-V1 -->
# Actualizacion de producto — Validacion UML

ClassForge incorpora validacion explicita del modelo sin persistencia.

El usuario puede validar el draft actual y recibir:

- errores;
- advertencias;
- informacion;
- codigos estables;
- mensajes legibles;
- rutas de campo;
- UUID del elemento afectado cuando existe.

Los diagnosticos navegables seleccionan la clase o relacion correspondiente en el canvas.

El guardado conserva validacion automatica y rechaza solo errores; las advertencias no bloquean la persistencia.

<!-- COMMAND-BUS-UNDO-REDO-V1 -->
# Actualizacion de producto — Command Bus y CU-05

El editor manual despacha mutaciones tipadas al `UmlCommandBus`.

CU-05 agrega Undo/Redo local con un historial máximo inicial de 100 operaciones y shortcuts de teclado.

Los snapshots before/after son internos al historial; `UmlCommand` continúa siendo el contrato reutilizable para colaboración e IA.

<!-- IMPLEMENTATION-STATUS-CU05-V2 -->
# Estado de implementacion verificado hasta CU-05

| Caso | Estado | Evidencia principal |
|---|---|---|
| CU-01 Crear proyecto | Cerrado | API, UUID, ownership y JPA |
| CU-02 Abrir/guardar | Cerrado | ProjectDocument, revision y conflicto 409 |
| CU-03 Diagramar manualmente | Cerrado | clases, atributos, JointJS, relaciones, multiplicidades e inspector |
| CU-04 Validar UML | Cerrado | motor unico, endpoint explicito y diagnosticos navegables |
| CU-05 Undo/Redo | Cerrado | Command Bus, executor e historial local |
| CU-06+ | Pendiente | backlog PUDS |

## Fuente de verdad

```text
ProjectDocument
├── UmlModel
└── DiagramLayout
```

JointJS sigue siendo una proyeccion.

## Command Bus

La deuda arquitectonica detectada despues de CU-04 queda resuelta en CU-05.

El historial before/after pertenece a Undo/Redo; `UmlCommand` es el contrato reutilizable.

## Perfil ClassForge

`nullable` e `identifier` son metadatos de generacion asociados al atributo.

No deben presentarse academicamente como propiedades UML 2.5.1 puras.

## Subconjunto UML implementado

- Class;
- Attribute/Property;
- Visibility;
- tipos;
- Association;
- Aggregation;
- Composition;
- Generalization;
- Multiplicity;
- layout separado.

Operaciones, enums y packages siguen pendientes.

<!-- REALTIME-COLLABORATION-CU06-001-V1 -->
# Actualizacion de producto — CU06-001

ClassForge incorpora el servidor de colaboración STOMP.

Las operaciones remotas reutilizan conceptualmente el contrato Command de CU-05 y se aplican sobre el mismo `ProjectDocument` canónico.

El servidor:

1. autentica JWT;
2. verifica acceso al proyecto;
3. bloquea la fila del proyecto;
4. compara `baseRevision`;
5. ejecuta el comando;
6. valida el documento;
7. persiste;
8. incrementa revisión;
9. difunde el comando aceptado.

No se intercambia JSON de JointJS y no se introduce CRDT en esta etapa.

La integración automática Angular pertenece a CU06-002.

<!-- REALTIME-COLLABORATION-CU06-002-V1 -->
# Actualizacion de producto — CU06-002

El workspace Angular mantiene sincronización realtime con Spring mediante STOMP.

La edición es optimista: el usuario ve su cambio inmediatamente y el servidor confirma la revisión mediante broadcast.

El cliente conserva por separado:

- draft visible;
- documento confirmado;
- revisión confirmada;
- operaciones pending.

Ante gap, rechazo o intercalación conflictiva se recupera el estado autoritativo por REST en lugar de realizar merge implícito.

Si WebSocket no está disponible, la edición local y el guardado REST de CU-02 continúan disponibles.

Undo/Redo colaborativo permanece para CU06-003.

<!-- REALTIME-COLLABORATION-CU06-CLOSED-V1 -->
# Actualización de producto — CU-06 cerrado

ClassForge dispone de colaboración realtime autoritativa sobre STOMP.

El editor sincroniza operaciones `UmlCommand`, persiste cada operación aceptada y usa revisión explícita.

CU06-003 completa Undo/Redo colaborativo mediante comandos inversos.

El borrado de una clase utiliza `RESTORE_CLASS` como comando compensatorio atómico para recuperar su agregado visual/relacional sin transmitir `ProjectDocument` completo.

Las operaciones remotas invalidan el historial local para evitar deshacer intenciones antiguas sobre trabajo de terceros.

CU-07 agregará presencia efímera; la presencia no modificará la revisión ni el modelo UML.

<!-- REALTIME-PRESENCE-CU07-CLOSED-V1 -->
# Actualización de producto — CU-07 cerrado

ClassForge diferencia colaboración persistente de presencia efímera.

Las operaciones UML siguen Command Bus → Spring → revisión → JPA.

La presencia utiliza `ProjectPresenceRegistry` en memoria y no modifica `ProjectDocument`.

El workspace muestra sesiones conectadas, selección remota y cursores remotos sobre el canvas.

Los cursores son overlays y nunca JointJS cells.

El acceso multiusuario real al mismo proyecto dependerá de ProjectMembership; CU-07 ya está preparado para múltiples actores autenticados.

<!-- ASSISTANT-CU08-001-V1 -->
# Actualización producto — CU08-001

El workspace incorpora un chat del Assistant UML debajo del Inspector.

Texto libre se interpreta localmente con llama.cpp, se transforma a un plan semántico y Java lo resuelve a un BATCH validado.

El usuario revisa el plan antes de Aplicar.

Una intención compleja produce una sola operación colaborativa y una sola entrada de Undo/Redo.

La captura real de voz se añade en CU08-002.

<!-- ASSISTANT-CU08-001-RICH-INTENT -->
## Assistant UML — intención rica

El Assistant interpreta lenguaje natural en una estructura semántica independiente de la forma exacta de la frase.

Los atributos de una clase forman parte de la intención `CREATE_CLASS`; Java los expande después a comandos de dominio.

El preview muestra si el tipo de cada atributo fue explícito, inferido o predeterminado.

El runtime recomendado para la estación de trabajo de referencia es Qwen2.5-3B-Instruct Q4_K_M mediante llama.cpp con `--jinja` y native tool calling.

MCP no forma parte de CU08-001.

<!-- CU08-FIX-014 -->
### Assistant local — contrato definitivo
El Assistant usa Qwen2.5-3B-Instruct Q4_K_M mediante native function calling de llama.cpp. El modelo selecciona tools UML semánticas; ClassForge resuelve elementos existentes a UUID, preserva literales nuevos y genera preview/BATCH. Texto y voz convergen en el mismo planner y las peticiones compuestas se planifican sobre un documento efímero antes de Apply.

<!-- C2-CU09-001-IMAGE-VISION-CONTRACT -->
## Addendum CU-09 — contrato visual

C2-cu09-001 abre Imagen → UML con un contrato desacoplado del VLM. PNG/JPEG/WEBP se validan y normalizan; el modelo multimodal futuro debe devolver `VisionUmlProposal` con refs temporales y `VisionEvidence`. Java valida provenance, referencias, tipos, relaciones y multiplicidades, compila a `AssistantSemanticPlan` y conserva preview/BATCH/Command Bus como única ruta de mutación.

Este bloque describe la apertura histórica de CU-09. La decisión posterior de C2-cu09-002 seleccionó Qwen3-VL-4B-Instruct Q4_K_M sobre llama.cpp; la imagen continúa sin persistirse dentro de `ProjectDocument`.

<!-- C2-CU09-002-QWEN3-VL -->
## Addendum CU-09 — runtime visual y benchmark

C2-cu09-002 materializa el adapter multimodal sin cambiar la autoridad del dominio. `LlamaCppVisionModelGateway` envía imagen Base64 + prompt UML a llama.cpp, restringe la respuesta por JSON Schema y rechaza cualquier salida truncada o no deserializable sin repair heurístico. El prompt recibe únicamente nombres de clases/atributos existentes, nunca UUID internos.

El health del Assistant separa `readyForImage` de texto/voz y exige que el runtime de 8094 publique el modelo esperado y `modalities.vision=true`. El frontend bloquea Analizar cuando Vision no está READY.

La suite `assistantVisionBenchmark` compara planes semánticos contra ground truth en regression y holdout y produce métricas de transporte, schema, grounding, exactitud estructural, safety, latencia y memoria GPU observada. La evidencia posterior seleccionó Qwen3-VL-4B Q4_K_M y el benchmark terminó incorporando hardening focal y un executable gate antes del cierre de CU-09.


<!-- C2-CU09-003-VISION-UX-HARDENING -->
## Addendum CU-09 — experiencia visual completa y validación diferida

C2-cu09-003 completa la experiencia de Imagen → UML sin declarar prematuramente que el baseline visual es definitivo. El usuario puede seleccionar, arrastrar, pegar o capturar una imagen, realizar rotación/recorte conservador y analizarla. Cuando el VLM entrega bounding boxes, el preview visual puede resaltar la evidencia asociada a clases, atributos y relaciones.

El resultado puede ser `READY`, `NO_CHANGES` o `NO_ACTIONABLE_UML`. Solo `READY` expone un comando aplicable; los demás estados son éxitos seguros sin mutación. Conflictos con el modelo UML existente se omiten con advertencias antes que convertir Imagen → UML en una ruta de edición destructiva.

La calibración posterior cerró CU-09 el 5 de septiembre de 2026. La solución final usa Qwen3-VL-4B + OpenCV + Java, fail-closed, E2E por la autoridad colaborativa y canonicalización determinista de identificadores visuales. El hardening focal obtuvo 3/3 Exact. La validación con dos pizarras adicionales y una corrida archivada post-Cal-017 del agregador completo quedaron registradas como validación adicional diferida, no como evidencia ejecutada.
