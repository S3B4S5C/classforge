package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stable CU-17 facade. Rendering details are grouped by generated concern. */
public final class AngularFrontendRenderer {
    public static final String ANGULAR_VERSION = "22.1.0";
    private static final String ROOT = "frontend/";

    private final AngularRenderingSupport support = new AngularRenderingSupport();
    private final AngularProjectFilesRenderer projectFiles = new AngularProjectFilesRenderer();
    private final AngularEntityFilesRenderer entityFiles = new AngularEntityFilesRenderer();
    private final AngularAuthFilesRenderer authFiles = new AngularAuthFilesRenderer();
    private final AngularAssistantFilesRenderer assistantFiles = new AngularAssistantFilesRenderer();

    public List<GeneratedFile> render(DomainManifestPlan manifest, String artifactName, String primaryColor) {
        Objects.requireNonNull(manifest, "manifest is required");
        String color = support.normalizeColor(primaryColor);
        List<GeneratedFile> files = new ArrayList<>();

        add(files, "package.json", projectFiles.packageJson(artifactName));
        add(files, "angular.json", projectFiles.angularJson(artifactName));
        add(files, "tsconfig.json", projectFiles.tsconfig());
        add(files, "tsconfig.app.json", projectFiles.tsconfigApp());
        add(files, "proxy.conf.json", projectFiles.proxy());
        add(files, "README.md", projectFiles.readme(manifest, artifactName, color));
        add(files, "src/index.html", projectFiles.indexHtml(artifactName));
        add(files, "src/main.ts", projectFiles.mainTs(manifest.authentication().enabled()));
        add(files, "src/styles.css", projectFiles.styles(color));
        add(files, "src/app/app.component.ts", projectFiles.appComponent(manifest));
        add(files, "src/app/app.routes.ts", projectFiles.routes(manifest));
        add(files, "src/app/core/api/page-response.ts", projectFiles.pageResponse());
        add(files, "src/app/core/api/reference-data.service.ts", projectFiles.referenceDataService());
        add(files, "src/app/dashboard/dashboard.component.ts", projectFiles.dashboard(manifest));
        add(files, "src/app/assistant/assistant.service.ts", assistantFiles.assistantService());
        add(files, "src/app/assistant/browser-wav-recorder.service.ts", assistantFiles.browserWavRecorder());
        add(files, "src/app/assistant/assistant.component.ts", assistantFiles.assistantComponent());

        if (manifest.authentication().enabled()) {
            DomainManifestPlan.Entity authEntity = manifest.entities().stream()
                    .filter(entity -> entity.id().equals(manifest.authentication().entityId()))
                    .findFirst().orElseThrow();
            add(files, "src/app/core/auth/auth.service.ts", authFiles.authService());
            add(files, "src/app/core/auth/auth.interceptor.ts", authFiles.authInterceptor());
            add(files, "src/app/core/auth/auth.guard.ts", authFiles.authGuard());
            add(files, "src/app/auth/login.component.ts", authFiles.loginComponent());
            add(files, "src/app/auth/bootstrap.component.ts", entityFiles.bootstrapComponent(authEntity, manifest));
        }

        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String prefix = support.kebab(entity.codeName());
            String path = "src/app/entities/" + prefix + "/";
            add(files, path + prefix + ".models.ts", entityFiles.entityModels(entity));
            add(files, path + prefix + ".api.ts", entityFiles.entityApi(entity));
            add(files, path + prefix + "-list.component.ts", entityFiles.entityList(entity));
            add(files, path + prefix + "-detail.component.ts", entityFiles.entityDetail(entity));
            add(files, path + prefix + "-form.component.ts", entityFiles.entityForm(entity, manifest));
        }
        return List.copyOf(files);
    }

    private void add(List<GeneratedFile> files, String path, String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n")) normalized += "\n";
        files.add(new GeneratedFile(ROOT + path, GeneratedFileType.TEXT, normalized.getBytes(StandardCharsets.UTF_8)));
    }
}
