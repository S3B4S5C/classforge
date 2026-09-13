# Modelo relacional interno

## Boundary

CU-12 transforma de forma determinista y read-only:

```text
UmlModel -> RelationalModelMapper -> RelationalModel -> CU-13 Spring Boot/JPA
```

`RelationalModel` es una IR derivada, efímera e interna en `com.classforge.generation.relational`. No pertenece a `ProjectDocument`, no se persiste, no tiene endpoint, API ni UI. `UmlModel` sigue siendo la fuente de verdad.

## IR y validación

`RelationalModel` contiene tables y relations. Una table conserva origen UML/join relationship, nombre lógico/físico, columns, PK, FKs, unique constraints e indexes. Una column conserva origen ATTRIBUTE/FOREIGN_KEY/INHERITED_PRIMARY_KEY y UUID fuente. `RelationalModelValidator` exige nombres únicos, PK existente y no nullable, FK local/target/arity/PK válida, constraints e indexes sobre columnas existentes, tables de relation existentes, owning/join table coherente y relationship IDs únicos.

## Nombres y tipos

El naming baja a `snake_case` con `Locale.ROOT`, preserva underscores y detecta fronteras camelCase/acrónimo: `LineaPedido -> linea_pedido`, `fechaPrestamo -> fecha_prestamo`, `URLExterna -> url_externa`. No pluraliza ni traduce. Toda colisión de tabla/columna falla cerrada.

| UML | Relational |
|---|---|
| STRING | VARCHAR |
| INTEGER | INTEGER |
| LONG | BIGINT |
| DECIMAL | DECIMAL |
| BOOLEAN | BOOLEAN |
| DATE | DATE |
| DATETIME | TIMESTAMP |
| UUID | UUID |

Cada root necesita identifier explícito. Uno forma PK simple; varios forman PK compuesta ordenada por nombre físico; no hay surrogate IDs. `CUSTOM` falla con `CUSTOM_TYPE_UNSUPPORTED`: enum mapping deferred until canonical UML explicitly models enum semantics.

## Relaciones

Sólo se aceptan multiplicidades `1`, `0..1`, `0..*`, `1..*`. Toda referencia `sourceClassId` o `targetClassId` desconocida, multiplicidad no soportada, relación reflexiva sin roles o colisión falla cerrada antes de devolver IR.

La FK se llama `<referenced_table>_<referenced_pk_column>`. Para PK compuesta se crea una columna por PK en correspondencia posicional con la PK objetivo, por ejemplo `tenant_country_code`, `tenant_tenant_id`. Su nulabilidad es la del extremo referenciado: `0..1` permite null; `1` no.

- 1:N: FK e índice en el extremo many.
- 1:1: FK unique. Si sólo un endpoint lower=0, ese endpoint contiene la FK. Si lower coincide, la table física lexicográficamente mayor contiene FK hacia la menor. ASSOCIATION no depende de source/target del dibujo.
- N:M ASSOCIATION: join table con nombres físicos ordenados, PK de todas las FKs y dos FKs no nullable. Aggregation N:M conserva source/target whole/part para el nombre.
- Aggregation: source=whole, target=part, storage estructural igual a asociación y `NO_ACTION`.
- Composition: source=whole, target=part; source upper debe ser <=1, FK queda en part y usa `CASCADE`. No hay N:M composition.

## Herencia JOINED

GENERALIZATION usa source=subclass y target=superclass. Cada subclass copia sólo la PK del parent como `INHERITED_PRIMARY_KEY`, la usa como PK y FK `CASCADE` hacia el parent. Esto se repite topológicamente para varios niveles. Una subclass no puede declarar identifier propio; una clase no puede tener más de un parent. Ciclos se rechazan defensivamente. Una asociación hacia subclass referencia su propia table y PK.

## Determinismo

La salida ordena tables, columns, PK, FK, constraints, indexes y relations por sus claves físicas y UUID definidos. El mapper copia las colecciones de entrada y nunca modifica clases, atributos, relaciones ni multiplicidades. CU-13 consume esta IR para generar Spring Boot/JPA; CU-12 no genera SQL, JPA ni código.
