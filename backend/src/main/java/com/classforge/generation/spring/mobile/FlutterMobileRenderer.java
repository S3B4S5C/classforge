package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stable CU-18 facade. Rendering details are grouped by generated concern. */
public final class FlutterMobileRenderer {
    public static final String ROOT = "mobile/";
    public static final String FLUTTER_CHANNEL = "stable";

    private final FlutterRenderingSupport support = new FlutterRenderingSupport();
    private final FlutterProjectFilesRenderer projectFiles = new FlutterProjectFilesRenderer();
    private final FlutterEntityFilesRenderer entityFiles = new FlutterEntityFilesRenderer();
    private final FlutterAuthFilesRenderer authFiles = new FlutterAuthFilesRenderer();
    private final FlutterAssistantFilesRenderer assistantFiles = new FlutterAssistantFilesRenderer();
    private final FlutterAndroidFilesRenderer androidFiles = new FlutterAndroidFilesRenderer();

    public List<GeneratedFile> render(DomainManifestPlan manifest, String artifactName, String basePackage, String primaryColor) {
        Objects.requireNonNull(manifest, "manifest is required");
        String packageName = support.dartPackage(artifactName) + "_mobile";
        String androidNamespace = basePackage + ".mobile";
        String color = support.normalizeColor(primaryColor);
        List<GeneratedFile> files = new ArrayList<>();

        add(files, "pubspec.yaml", projectFiles.pubspec(packageName, manifest.authentication().enabled()));
        add(files, "analysis_options.yaml", projectFiles.analysisOptions());
        add(files, "README.md", projectFiles.readme(manifest, artifactName, color));
        add(files, "lib/main.dart", projectFiles.mainDart(packageName));
        add(files, "lib/app/app.dart", projectFiles.appDart(manifest, artifactName));
        add(files, "lib/core/theme/app_theme.dart", projectFiles.theme(color));
        add(files, "lib/core/navigation/app_navigator.dart", projectFiles.appNavigator());
        add(files, "lib/core/api/page_response.dart", projectFiles.pageResponse());
        add(files, "lib/core/api/api_client.dart", projectFiles.apiClient(manifest.authentication().enabled()));
        add(files, "lib/core/widgets/reference_picker.dart", projectFiles.referencePicker());
        add(files, "lib/dashboard/dashboard_page.dart", projectFiles.dashboard(manifest));
        add(files, "lib/assistant/assistant_api.dart", assistantFiles.assistantApi(manifest.authentication().enabled()));
        add(files, "lib/assistant/assistant_page.dart", assistantFiles.assistantPage());
        add(files, "test/smoke_test.dart", projectFiles.smokeTest(packageName));

        if (manifest.authentication().enabled()) {
            add(files, "lib/core/auth/token_store.dart", authFiles.tokenStore());
            add(files, "lib/core/auth/auth_api.dart", authFiles.authApi());
            add(files, "lib/core/auth/auth_gate.dart", authFiles.authGate());
            add(files, "lib/auth/login_page.dart", authFiles.loginPage());
            add(files, "lib/auth/bootstrap_page.dart", authFiles.bootstrapPage(support.authEntity(manifest), manifest));
        }

        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String prefix = support.snake(entity.codeName());
            String path = "lib/entities/" + prefix + "/";
            add(files, path + prefix + "_model.dart", entityFiles.entityModel(entity));
            add(files, path + prefix + "_api.dart", entityFiles.entityApi(entity));
            add(files, path + prefix + "_list_page.dart", entityFiles.entityList(entity));
            add(files, path + prefix + "_detail_page.dart", entityFiles.entityDetail(entity));
            add(files, path + prefix + "_form_page.dart", entityFiles.entityForm(entity, manifest));
        }

        add(files, "android/settings.gradle.kts", androidFiles.settingsGradle());
        add(files, "android/build.gradle.kts", androidFiles.androidBuildGradle());
        add(files, "android/gradle.properties", androidFiles.gradleProperties());
        add(files, "android/gradle/wrapper/gradle-wrapper.properties", androidFiles.wrapperProperties());
        add(files, "android/app/build.gradle.kts", androidFiles.appBuildGradle(androidNamespace, manifest.authentication().enabled()));
        add(files, "android/app/src/main/AndroidManifest.xml", androidFiles.androidManifest(artifactName));
        add(files, "android/app/src/debug/AndroidManifest.xml", androidFiles.debugManifest());
        add(files, "android/app/src/profile/AndroidManifest.xml", androidFiles.debugManifest());
        add(files, "android/app/src/main/kotlin/" + androidNamespace.replace('.', '/') + "/MainActivity.kt", androidFiles.mainActivity(androidNamespace));
        return List.copyOf(files);
    }

    private void add(List<GeneratedFile> files, String path, String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n")) normalized += "\n";
        files.add(new GeneratedFile(ROOT + path, GeneratedFileType.TEXT, normalized.getBytes(StandardCharsets.UTF_8)));
    }
}
