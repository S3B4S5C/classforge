package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.spring.generated.*;
import com.classforge.generation.spring.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringProjectRendererTests {
    private final SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), new GeneratedProjectValidator());
    @Test void rendersDeterministicVirtualSkeletonAndSimpleEntity() {
        GeneratedProject first = renderer.render(model()), second = renderer.render(model()); assertEquals(first, second);
        assertTrue(first.files().stream().map(GeneratedFile::path).toList().containsAll(List.of("build.gradle", "settings.gradle", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "src/main/java/com/example/demo/DemoApplication.java", "src/main/java/com/example/demo/entity/Cliente.java", "src/main/java/com/example/demo/repository/ClienteRepository.java")));
        String build = text(first, "build.gradle");
        assertTrue(build.contains("4.0.8") && build.contains("data-jpa") && !build.contains("security"));
        assertTrue(build.contains("implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.8')"));
        String entity = text(first, "src/main/java/com/example/demo/entity/Cliente.java"); assertTrue(entity.contains("@Entity") && entity.contains("@Id") && entity.contains("UUID id") && !entity.contains("GeneratedValue"));
        assertTrue(text(first, "src/main/resources/application-postgres.yml").contains("${DB_URL:"));
        for (GeneratedFile file : first.files()) if (file.type() == GeneratedFileType.TEXT) { assertFalse(new String(file.content(), StandardCharsets.UTF_8).contains("\r")); assertTrue(new String(file.content(), StandardCharsets.UTF_8).endsWith("\n")); }
    }
    private SpringGenerationModel model() {
        SpringIdFieldModel id = new SpringIdFieldModel(UUID.randomUUID(), "id", "id", SpringJavaType.UUID);
        SpringEntityIdModel entityId = new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(id), true);
        SpringEntityModel entity = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()), entityId, List.of(new SpringScalarFieldModel(id.sourceAttributeId(), "id", "id", "id", SpringJavaType.UUID, false, true), new SpringScalarFieldModel(UUID.randomUUID(), "nombre", "nombre", "nombre", SpringJavaType.STRING, true, false)), List.of(), List.of(), List.of(), List.of());
        return new SpringGenerationModel("1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0", List.of(entity), List.of(new SpringRepositoryModel("ClienteRepository", "Cliente", "UUID", "java.util.UUID", false)));
    }
    private String text(GeneratedProject project, String path) { return new String(project.files().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow().content(), StandardCharsets.UTF_8); }
}
