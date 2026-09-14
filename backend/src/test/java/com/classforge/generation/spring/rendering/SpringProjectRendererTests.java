package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.generated.*;
import com.classforge.generation.spring.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringProjectRendererTests {
    private final SpringProjectRenderer renderer = new SpringProjectRenderer(
            new SpringFreeMarkerRenderer(),
            new GeneratedProjectValidator()
    );

    @Test
    void rendersDeterministicVirtualSkeletonAndAutomaticUuidIdentifier() {
        SpringGenerationModel model = model(SpringJavaType.UUID);
        GeneratedProject first = renderer.render(model);
        GeneratedProject second = renderer.render(model);
        assertEquals(first, second);
        assertTrue(first.files().stream().map(GeneratedFile::path).toList().containsAll(List.of(
                "build.gradle", "settings.gradle", "gradlew", "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                "src/main/java/com/example/demo/DemoApplication.java",
                "src/main/java/com/example/demo/entity/Cliente.java",
                "src/main/java/com/example/demo/repository/ClienteRepository.java"
        )));
        String build = text(first, "build.gradle");
        assertTrue(build.contains("4.0.8") && build.contains("data-jpa") && !build.contains("security"));
        assertTrue(build.contains("implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.8')"));
        String entity = text(first, "src/main/java/com/example/demo/entity/Cliente.java");
        assertTrue(entity.contains("@Entity"));
        assertTrue(entity.contains("@Id"));
        assertTrue(entity.contains("UUID id"));
        assertTrue(entity.contains("@GeneratedValue(strategy = GenerationType.UUID)"));
        assertTrue(text(first, "src/main/resources/application-postgres.yml").contains("${DB_URL:"));
        for (GeneratedFile file : first.files()) {
            if (file.type() != GeneratedFileType.TEXT) continue;
            String value = new String(file.content(), StandardCharsets.UTF_8);
            assertFalse(value.contains("\r"));
            assertTrue(value.endsWith("\n"));
        }
    }

    @Test
    void selectsAutomaticStrategiesOnlyForSafeSimpleIdentifierTypes() {
        String integerEntity = text(renderer.render(model(SpringJavaType.INTEGER)),
                "src/main/java/com/example/demo/entity/Cliente.java");
        assertTrue(integerEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"));

        String longEntity = text(renderer.render(model(SpringJavaType.LONG)),
                "src/main/java/com/example/demo/entity/Cliente.java");
        assertTrue(longEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"));

        String stringEntity = text(renderer.render(model(SpringJavaType.STRING)),
                "src/main/java/com/example/demo/entity/Cliente.java");
        assertTrue(stringEntity.contains("@PrePersist"));
        assertTrue(stringEntity.contains("UUID.randomUUID().toString()"));
        assertFalse(stringEntity.contains("@GeneratedValue"));

        SpringEntityIdModel decimal = id(SpringJavaType.BIG_DECIMAL);
        assertFalse(decimal.automaticallyGenerated());
        SpringEntityIdModel composite = new SpringEntityIdModel(
                SpringIdKind.COMPOSITE, null, "ClienteId",
                List.of(
                        new SpringIdFieldModel(UUID.randomUUID(), "a", "a", SpringJavaType.INTEGER),
                        new SpringIdFieldModel(UUID.randomUUID(), "b", "b", SpringJavaType.INTEGER)
                ),
                true
        );
        assertFalse(composite.automaticallyGenerated());
    }

    private SpringGenerationModel model(SpringJavaType idType) {
        SpringEntityIdModel entityId = id(idType);
        SpringIdFieldModel id = entityId.fields().getFirst();
        SpringEntityModel entity = new SpringEntityModel(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Cliente", "Cliente", "cliente",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                entityId,
                List.of(
                        new SpringScalarFieldModel(id.sourceAttributeId(), "id", "id", "id", idType, false, true),
                        new SpringScalarFieldModel(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "nombre", "nombre", "nombre", SpringJavaType.STRING, true, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );
        return new SpringGenerationModel(
                "1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0",
                List.of(entity),
                List.of(new SpringRepositoryModel(
                        "ClienteRepository", "Cliente", idType.simpleName(), idType.qualifiedName(), false
                ))
        );
    }

    private SpringEntityIdModel id(SpringJavaType type) {
        SpringIdFieldModel field = new SpringIdFieldModel(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "id", "id", type
        );
        return new SpringEntityIdModel(SpringIdKind.SIMPLE, type, null, List.of(field), true);
    }

    private String text(GeneratedProject project, String path) {
        return new String(project.files().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow()
                .content(), StandardCharsets.UTF_8);
    }
}
