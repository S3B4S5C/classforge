package com.classforge.generation.spring.generated;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedProjectValidatorTests {
    private final GeneratedProjectValidator validator = new GeneratedProjectValidator();

    @Test
    void acceptsCompleteSafeSkeletonWithRuntimeSpringPlaceholders() {
        assertDoesNotThrow(() -> validator.validate(validProject()));
    }

    @Test
    void rejectsUnsafePathsDuplicatesAndCrLf() {
        for (String path : List.of(
                "/etc/passwd",
                "../secret",
                "src/../secret",
                "C:/secret",
                "C:\\secret",
                "src\\main\\App.java",
                "./build.gradle",
                "src//App.java"
        )) {
            assertCode(withExtra(validProject(), file(path, "x\n")), GeneratedProjectDiagnosticCode.INVALID_PATH);
        }

        assertCode(
                withExtra(validProject(), file("README.md", "duplicate\n")),
                GeneratedProjectDiagnosticCode.DUPLICATE_PATH
        );
        assertCode(
                withExtra(validProject(), file("readme.md", "case collision\n")),
                GeneratedProjectDiagnosticCode.DUPLICATE_PATH
        );
        assertCode(
                replace(validProject(), "README.md", file("README.md", "bad\r\n")),
                GeneratedProjectDiagnosticCode.INVALID_LINE_ENDINGS
        );
    }

    @Test
    void requiresCompleteSkeletonApplicationBootstrapAndContextLoadTest() {
        assertCode(without(validProject(), "build.gradle"), GeneratedProjectDiagnosticCode.REQUIRED_SKELETON_MISSING);
        assertCode(
                replace(
                        validProject(),
                        "src/main/java/com/example/demo/DemoApplication.java",
                        file("src/main/java/com/example/demo/DemoApplication.java", "package com.example.demo;\npublic class DemoApplication { }\n")
                ),
                GeneratedProjectDiagnosticCode.APPLICATION_BOOTSTRAP_INVALID
        );
        assertCode(
                replace(
                        validProject(),
                        "src/test/java/com/example/demo/DemoApplicationTests.java",
                        file("src/test/java/com/example/demo/DemoApplicationTests.java", "package com.example.demo;\nclass DemoApplicationTests { }\n")
                ),
                GeneratedProjectDiagnosticCode.REQUIRED_SKELETON_MISSING
        );
    }

    @Test
    void validatesPackageAndTopLevelTypeAgainstJavaPath() {
        GeneratedProject wrongPackage = replace(
                validProject(),
                "src/main/java/com/example/demo/DemoApplication.java",
                file(
                        "src/main/java/com/example/demo/DemoApplication.java",
                        "package com.example.other;\n@SpringBootApplication\npublic class DemoApplication { }\n"
                )
        );
        assertCode(wrongPackage, GeneratedProjectDiagnosticCode.PACKAGE_PATH_MISMATCH);

        GeneratedProject wrongTypeName = withExtra(
                validProject(),
                file("src/main/java/com/example/demo/entity/Cliente.java", "package com.example.demo.entity;\npublic class Persona { }\n")
        );
        assertCode(wrongTypeName, GeneratedProjectDiagnosticCode.PACKAGE_PATH_MISMATCH);
    }

    @Test
    void rejectsMissingGeneratedImportsAndSamePackageEntityReferences() {
        GeneratedProject missingImport = withExtra(
                validProject(),
                file(
                        "src/main/java/com/example/demo/repository/ClienteRepository.java",
                        "package com.example.demo.repository;\nimport com.example.demo.entity.Cliente;\npublic interface ClienteRepository { }\n"
                )
        );
        assertCode(missingImport, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING);

        GeneratedProject missingRelationTarget = withExtra(
                validProject(),
                file(
                        "src/main/java/com/example/demo/entity/Prestamo.java",
                        "package com.example.demo.entity;\npublic class Prestamo {\n    private Cliente cliente;\n}\n"
                )
        );
        assertCode(missingRelationTarget, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING);
    }

    @Test
    void allowsAngularTypeScriptTemplateLiteralsWithoutWeakeningFreeMarkerChecks() {
        GeneratedProject angularLiteral = withExtra(
                validProject(),
                file(
                        "frontend/src/app/example.api.ts",
                        "const endpoint = '/api/example';\nconst url = `${endpoint}/count`;\n"
                )
        );
        assertDoesNotThrow(() -> validator.validate(angularLiteral));

        GeneratedProject unresolvedBackendTemplate = replace(
                validProject(),
                "README.md",
                file("README.md", "${model.basePackage}\n")
        );
        assertCode(unresolvedBackendTemplate, GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER);
    }

    @Test
    void allowsFlutterDartAndGradleRuntimeInterpolationWithoutWeakeningFreeMarkerChecks() {
        GeneratedProject flutterLiteral = withExtra(
                withExtra(
                        validProject(),
                        file("mobile/lib/example_api.dart", "final endpoint = '/api/example';\nfinal url = '$endpoint/count';\n")
                ),
                file("mobile/android/settings.gradle.kts", "includeBuild(\"$flutterSdkPath/packages/flutter_tools/gradle\")\n")
        );
        assertDoesNotThrow(() -> validator.validate(flutterLiteral));

        GeneratedProject unresolvedBackendTemplate = replace(
                validProject(),
                "README.md",
                file("README.md", "${model.basePackage}\n")
        );
        assertCode(unresolvedBackendTemplate, GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER);
    }

    @Test
    void allowsGeneratedAssistantRuntimeSpringPlaceholders() {
        GeneratedProject assistantRuntime = withExtra(
                validProject(),
                file(
                        "src/main/java/com/example/demo/assistant/AssistantRuntimeConfig.java",
                        "package com.example.demo.assistant;\n"
                                + "class AssistantRuntimeConfig {\n"
                                + "  String llama = \"${app.assistant.llama-url:http://127.0.0.1:8092}\";\n"
                                + "  String model = \"${app.assistant.llama-model:local-model}\";\n"
                                + "  String whisper = \"${app.assistant.whisper-url:http://127.0.0.1:8093}\";\n"
                                + "  String language = \"${app.assistant.whisper-language:es}\";\n"
                                + "  String api = \"${app.assistant.api-base-url:http://127.0.0.1:8080}\";\n"
                                + "}\n"
                )
        );
        assertDoesNotThrow(() -> validator.validate(assistantRuntime));
    }

    @Test
    void rejectsUnresolvedFreeMarkerButAllowsGeneratedRuntimeAndWrapperExpressions() {
        assertCode(
                replace(validProject(), "README.md", file("README.md", "${model.basePackage}\n")),
                GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER
        );
        assertCode(
                replace(validProject(), "README.md", file("README.md", "<#if model.entities?size gt 0>bad</#if>\n")),
                GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER
        );

        GeneratedProject allowed = replace(
                validProject(),
                "gradlew",
                file("gradlew", "echo ${JAVA_HOME}\n")
        );
        assertDoesNotThrow(() -> validator.validate(allowed));
    }

    private GeneratedProject validProject() {
        return new GeneratedProject("demo", List.of(
                file(".gitignore", ".gradle/\n"),
                file("README.md", "demo\n"),
                file("build.gradle", "plugins { id 'java' }\n"),
                file("settings.gradle", "rootProject.name = 'demo'\n"),
                file("gradlew", "#!/bin/sh\necho ${JAVA_HOME}\n"),
                file("gradlew.bat", "@echo off\necho %JAVA_HOME%\n"),
                binary("gradle/wrapper/gradle-wrapper.jar"),
                file("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https://example.invalid/gradle.zip\n"),
                file(
                        "src/main/java/com/example/demo/DemoApplication.java",
                        "package com.example.demo;\n@SpringBootApplication\npublic class DemoApplication { }\n"
                ),
                file("src/main/resources/application.yml", "spring:\n  datasource:\n    url: jdbc:h2:mem:demo\n"),
                file(
                        "src/main/resources/application-postgres.yml",
                        "spring:\n  datasource:\n    url: ${DB_URL:jdbc:postgresql://localhost/demo}\n    username: ${DB_USERNAME:postgres}\n    password: ${DB_PASSWORD:}\n"
                ),
                file(
                        "src/test/java/com/example/demo/DemoApplicationTests.java",
                        "package com.example.demo;\n@SpringBootTest\nclass DemoApplicationTests {\n    void contextLoads() { }\n}\n"
                ),
                file("src/test/resources/application.yml", "spring:\n  datasource:\n    url: jdbc:h2:mem:testdb\n")
        ));
    }

    private GeneratedProject withExtra(GeneratedProject project, GeneratedFile extra) {
        List<GeneratedFile> files = new ArrayList<>(project.files());
        files.add(extra);
        return new GeneratedProject(project.artifactName(), files);
    }

    private GeneratedProject replace(GeneratedProject project, String path, GeneratedFile replacement) {
        List<GeneratedFile> files = new ArrayList<>(project.files());
        files.removeIf(file -> file.path().equals(path));
        files.add(replacement);
        return new GeneratedProject(project.artifactName(), files);
    }

    private GeneratedProject without(GeneratedProject project, String path) {
        return new GeneratedProject(
                project.artifactName(),
                project.files().stream().filter(file -> !file.path().equals(path)).toList()
        );
    }

    private GeneratedFile file(String path, String content) {
        return new GeneratedFile(path, GeneratedFileType.TEXT, content.getBytes(StandardCharsets.UTF_8));
    }

    private GeneratedFile binary(String path) {
        return new GeneratedFile(path, GeneratedFileType.BINARY, new byte[] {0, 1, 2});
    }

    private void assertCode(GeneratedProject project, GeneratedProjectDiagnosticCode code) {
        GeneratedProjectException exception = assertThrows(
                GeneratedProjectException.class,
                () -> validator.validate(project)
        );
        assertTrue(
                exception.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == code),
                () -> "Expected " + code + " but got " + exception.diagnostics()
        );
    }
}
