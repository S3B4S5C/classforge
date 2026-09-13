package com.classforge.generation.spring.rendering;

import com.classforge.generation.spring.api.SpringApiEntityModel;
import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.api.SpringApiGenerationPlanner;
import com.classforge.generation.spring.api.contract.SpringApiContract;
import com.classforge.generation.spring.api.contract.SpringApiContractPlanner;
import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.domain.DomainManifestPlanner;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.frontend.AngularFrontendRenderer;
import com.classforge.generation.spring.mobile.FlutterMobileRenderer;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnostic;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnosticCode;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.SpringDirectRelationKind;
import com.classforge.generation.spring.model.SpringEntityModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringInheritanceKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SpringProjectRenderer {
    private final SpringFreeMarkerRenderer templates;
    private final GeneratedProjectValidator validator;
    private final SpringApiGenerationPlanner apiPlanner = new SpringApiGenerationPlanner();
    private final SpringApiArtifactsRenderer apiArtifactsRenderer = new SpringApiArtifactsRenderer();
    private final SpringApiContractPlanner apiContractPlanner = new SpringApiContractPlanner();
    private final DomainManifestPlanner domainManifestPlanner = new DomainManifestPlanner();
    private final DomainManifestRenderer domainManifestRenderer = new DomainManifestRenderer();
    private final AngularFrontendRenderer angularFrontendRenderer = new AngularFrontendRenderer();
    private final FlutterMobileRenderer flutterMobileRenderer = new FlutterMobileRenderer();

    public SpringProjectRenderer(SpringFreeMarkerRenderer templates, GeneratedProjectValidator validator) {
        this.templates = templates;
        this.validator = validator;
    }

    public GeneratedProject render(SpringGenerationModel model) {
        return render(model, SpringBootGenerationOptions.strict());
    }

    public GeneratedProject render(SpringGenerationModel model, SpringBootGenerationOptions options) {
        Objects.requireNonNull(model, "model is required");
        SpringApiGenerationPlan api = apiPlanner.plan(model, options);
        Map<String, SpringApiEntityModel> apiByClass = api.entities().stream()
                .collect(Collectors.toMap(SpringApiEntityModel::className, Function.identity()));
        List<GeneratedFile> files = new ArrayList<>();
        String packagePath = model.basePackage().replace('.', '/');
        Map<String, Object> projectContext = Map.of("model", model, "api", api);

        add(files, "project/build.gradle.ftl", "build.gradle", projectContext);
        add(files, "project/settings.gradle.ftl", "settings.gradle", projectContext);
        add(files, "project/application.java.ftl", "src/main/java/" + packagePath + "/" + model.applicationClassName() + ".java", projectContext);
        add(files, "project/application.yml.ftl", "src/main/resources/application.yml", projectContext);
        add(files, "project/application-postgres.yml.ftl", "src/main/resources/application-postgres.yml", projectContext);
        add(files, "project/test-application.yml.ftl", "src/test/resources/application.yml", projectContext);
        add(files, "project/application-test.java.ftl", "src/test/java/" + packagePath + "/" + model.applicationClassName() + "Tests.java", projectContext);
        add(files, "project/README.md.ftl", "README.md", projectContext);
        add(files, "project/gitignore.ftl", ".gitignore", Map.of());
        add(files, "project/gradle-wrapper.properties.ftl", "gradle/wrapper/gradle-wrapper.properties", Map.of());
        staticFile(files, "gradlew", "generation/spring/static/gradlew", GeneratedFileType.TEXT);
        staticFile(files, "gradlew.bat", "generation/spring/static/gradlew.bat", GeneratedFileType.TEXT);
        staticFile(files, "gradle/wrapper/gradle-wrapper.jar", "generation/spring/static/gradle/wrapper/gradle-wrapper.jar", GeneratedFileType.BINARY);

        for (SpringEntityModel entity : model.entities()) {
            SpringApiEntityModel apiEntity = apiByClass.get(entity.className());
            add(files, "jpa/entity.java.ftl", "src/main/java/" + packagePath + "/entity/" + entity.className() + ".java",
                    Map.of("model", model, "entity", entity, "imports", imports(entity), "api", api, "apiEntity", apiEntity == null ? "" : apiEntity));
            if (entity.id().kind() == SpringIdKind.COMPOSITE && entity.id().declaredByEntity()) {
                add(files, "jpa/id-class.java.ftl", "src/main/java/" + packagePath + "/entity/" + entity.id().idClassName() + ".java", Map.of("model", model, "entity", entity));
            }
        }
        for (var repository : model.repositories()) {
            add(files, "jpa/repository.java.ftl", "src/main/java/" + packagePath + "/repository/" + repository.interfaceName() + ".java", Map.of("model", model, "repository", repository));
        }

        if (api.enabled()) {
            add(files, "api/page-response.java.ftl", "src/main/java/" + packagePath + "/api/PageResponse.java", projectContext);
            add(files, "api/crud-support.java.ftl", "src/main/java/" + packagePath + "/api/CrudSupport.java", projectContext);
            add(files, "api/api-not-found.java.ftl", "src/main/java/" + packagePath + "/api/ApiNotFoundException.java", projectContext);
            add(files, "api/api-exception-handler.java.ftl", "src/main/java/" + packagePath + "/api/ApiExceptionHandler.java", projectContext);
            for (SpringApiEntityModel entity : api.entities()) {
                Map<String, Object> context = Map.of("model", model, "api", api, "entity", entity);
                add(files, "api/request.java.ftl", "src/main/java/" + packagePath + "/dto/" + entity.className() + "Request.java", context);
                add(files, "api/response.java.ftl", "src/main/java/" + packagePath + "/dto/" + entity.className() + "Response.java", context);
                add(files, "api/service.java.ftl", "src/main/java/" + packagePath + "/service/" + entity.className() + "Service.java", context);
                add(files, "api/controller.java.ftl", "src/main/java/" + packagePath + "/controller/" + entity.className() + "Controller.java", context);
            }
        }
        if (api.authEnabled()) {
            SpringApiEntityModel auth = api.authEntity();
            Map<String, Object> authContext = Map.of("model", model, "api", api, "auth", auth);
            add(files, "auth/login-request.java.ftl", "src/main/java/" + packagePath + "/security/LoginRequest.java", authContext);
            add(files, "auth/login-response.java.ftl", "src/main/java/" + packagePath + "/security/LoginResponse.java", authContext);
            add(files, "auth/jwt-service.java.ftl", "src/main/java/" + packagePath + "/security/JwtService.java", authContext);
            add(files, "auth/jwt-filter.java.ftl", "src/main/java/" + packagePath + "/security/JwtAuthenticationFilter.java", authContext);
            add(files, "auth/security-config.java.ftl", "src/main/java/" + packagePath + "/security/SecurityConfig.java", authContext);
            add(files, "auth/auth-controller.java.ftl", "src/main/java/" + packagePath + "/controller/AuthController.java", authContext);
        }

        files.addAll(apiArtifactsRenderer.render(model, api));
        if (api.enabled()) {
            SpringApiContract apiContract = apiContractPlanner.plan(api);
            DomainManifestPlan manifest = domainManifestPlanner.plan(model, api, apiContract);
            files.add(domainManifestRenderer.render(manifest));
            files.addAll(angularFrontendRenderer.render(
                    manifest,
                    model.artifactName(),
                    options.primaryColor()
            ));
            files.addAll(flutterMobileRenderer.render(
                    manifest,
                    model.artifactName(),
                    model.basePackage(),
                    options.primaryColor()
            ));
            staticFile(files, "mobile/android/gradlew", "generation/spring/static/gradlew", GeneratedFileType.TEXT);
            staticFile(files, "mobile/android/gradlew.bat", "generation/spring/static/gradlew.bat", GeneratedFileType.TEXT);
            staticFile(files, "mobile/android/gradle/wrapper/gradle-wrapper.jar", "generation/spring/static/gradle/wrapper/gradle-wrapper.jar", GeneratedFileType.BINARY);
        }

        GeneratedProject project = new GeneratedProject(model.artifactName(), files);
        validator.validate(project);
        return project;
    }

    private void add(List<GeneratedFile> files, String template, String path, Map<String, Object> context) {
        files.add(templates.render(template, path, context));
    }

    private void staticFile(List<GeneratedFile> files, String path, String resource, GeneratedFileType type) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new GeneratedProjectException(List.of(new GeneratedProjectDiagnostic(
                        GeneratedProjectDiagnosticCode.STATIC_RESOURCE_NOT_FOUND,
                        resource,
                        "Static resource is missing."
                )));
            }
            byte[] bytes = input.readAllBytes();
            files.add(new GeneratedFile(
                    path,
                    type,
                    type == GeneratedFileType.TEXT
                            ? SpringFreeMarkerRenderer.normalizeGeneratedText(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)
                            : bytes
            ));
        } catch (IOException exception) {
            throw new GeneratedProjectException(List.of(new GeneratedProjectDiagnostic(
                    GeneratedProjectDiagnosticCode.STATIC_RESOURCE_NOT_FOUND,
                    resource,
                    exception.getMessage()
            )), exception);
        }
    }

    private Set<String> imports(SpringEntityModel entity) {
        Set<String> result = new TreeSet<>();
        result.add("jakarta.persistence.Column");
        result.add("jakarta.persistence.Entity");
        result.add("jakarta.persistence.Table");
        if (entity.id().declaredByEntity()) result.add("jakarta.persistence.Id");
        if (entity.id().kind() == SpringIdKind.COMPOSITE && entity.id().declaredByEntity()) result.add("jakarta.persistence.IdClass");
        if (entity.inheritance().kind() == SpringInheritanceKind.JOINED_ROOT) {
            result.add("jakarta.persistence.Inheritance");
            result.add("jakarta.persistence.InheritanceType");
        }
        if (entity.inheritance().kind() == SpringInheritanceKind.JOINED_SUBCLASS) {
            result.add("jakarta.persistence.PrimaryKeyJoinColumn");
            result.add("jakarta.persistence.PrimaryKeyJoinColumns");
        }
        for (var field : entity.scalarFields()) {
            if (!field.javaType().qualifiedName().startsWith("java.lang")) result.add(field.javaType().qualifiedName());
        }
        for (var relation : entity.directRelations()) {
            result.add("jakarta.persistence.FetchType");
            result.add("jakarta.persistence.JoinColumn");
            if (relation.joinColumns().size() > 1) result.add("jakarta.persistence.JoinColumns");
            result.add(relation.kind() == SpringDirectRelationKind.MANY_TO_ONE
                    ? "jakarta.persistence.ManyToOne" : "jakarta.persistence.OneToOne");
            if (relation.onDeleteCascade()) {
                result.add("org.hibernate.annotations.OnDelete");
                result.add("org.hibernate.annotations.OnDeleteAction");
            }
        }
        for (var relation : entity.manyToManyRelations()) {
            result.add("jakarta.persistence.FetchType");
            result.add("jakarta.persistence.JoinColumn");
            result.add("jakarta.persistence.JoinTable");
            result.add("jakarta.persistence.ManyToMany");
            result.add("java.util.LinkedHashSet");
            result.add("java.util.Set");
        }
        if (!entity.uniqueConstraints().isEmpty()) result.add("jakarta.persistence.UniqueConstraint");
        if (!entity.indexes().isEmpty()) result.add("jakarta.persistence.Index");
        return result;
    }
}
