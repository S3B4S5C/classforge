# ClassForge Backend

Backend/orquestador de ClassForge.

## Stack inicial

- Java target: 21
- Spring Boot: 4.0.8
- Gradle (Groovy DSL)
- Spring Web MVC
- Spring WebSocket
- Spring Data JPA
- Jakarta Validation
- H2 para desarrollo inicial
- PostgreSQL preparado como runtime dependency

## Ejecutar

Si IntelliJ administra Gradle, importa/abre esta carpeta como proyecto Gradle y ejecuta `ClassForgeApplication` o la tarea `bootRun`.

Desde terminal con Gradle 9.1+:

```bash
gradle bootRun
```

Con JDK 25, usa Gradle 9.1 o superior. El código se compila con `--release 21` para mantener Java 21 como target.
