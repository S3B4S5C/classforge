package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterAndroidFilesRenderer {

    String settingsGradle() {
        return """
                pluginManagement {
                    val flutterSdkPath = run {
                        val properties = java.util.Properties()
                        file("local.properties").inputStream().use { properties.load(it) }
                        val flutterSdkPath = properties.getProperty("flutter.sdk")
                        require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
                        flutterSdkPath
                    }
                    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }

                plugins {
                    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
                    id("com.android.application") version "9.1.0" apply false
                    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
                }

                include(":app")
                """;
    }

    String androidBuildGradle() {
        return """
                allprojects { repositories { google(); mavenCentral() } }
                val newBuildDir = rootProject.layout.buildDirectory.dir("../../build").get()
                rootProject.layout.buildDirectory.value(newBuildDir)
                subprojects {
                    val newSubprojectBuildDir = newBuildDir.dir(project.name)
                    project.layout.buildDirectory.value(newSubprojectBuildDir)
                }
                subprojects { project.evaluationDependsOn(":app") }
                tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }
                """;
    }

    String gradleProperties() {
        return """
                org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=2G -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError
                android.useAndroidX=true
                android.enableJetifier=true
                android.newDsl=false
                android.builtInKotlin=false
                """;
    }

    String wrapperProperties() {
        return """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https://services.gradle.org/distributions/gradle-9.3.1-all.zip
                networkTimeout=10000
                validateDistributionUrl=true
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """;
    }

    String appBuildGradle(String namespace, boolean auth) {
        String minSdk = "23";
        return """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("dev.flutter.flutter-gradle-plugin")
                }

                android {
                    namespace = "%1$s"
                    compileSdk = flutter.compileSdkVersion
                    ndkVersion = flutter.ndkVersion

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlin {
                        compilerOptions {
                            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                        }
                    }

                    defaultConfig {
                        applicationId = "%1$s"
                        minSdk = %2$s
                        targetSdk = flutter.targetSdkVersion
                        versionCode = flutter.versionCode
                        versionName = flutter.versionName
                    }

                    buildTypes {
                        release { signingConfig = signingConfigs.getByName("debug") }
                    }
                }

                flutter { source = "../.." }
                """.formatted(namespace, minSdk);
    }

    String androidManifest(String artifactName) {
        return """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.RECORD_AUDIO" />
                    <application android:label="%s" android:name="${applicationName}">
                        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop" android:taskAffinity="" android:theme="@android:style/Theme.Black.NoTitleBar" android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode" android:hardwareAccelerated="true" android:windowSoftInputMode="adjustResize">
                            <meta-data android:name="io.flutter.embedding.android.NormalTheme" android:resource="@android:style/Theme.Black.NoTitleBar" />
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="flutterEmbedding" android:value="2" />
                    </application>
                    <queries><intent><action android:name="android.intent.action.PROCESS_TEXT" /><data android:mimeType="text/plain" /></intent></queries>
                </manifest>
                """.formatted(escapeXml(title(artifactName)));
    }

    String debugManifest() {
        return """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
                """;
    }

    String mainActivity(String namespace) {
        return """
                package %s

                import io.flutter.embedding.android.FlutterActivity

                class MainActivity: FlutterActivity()
                """.formatted(namespace);
    }

    String title(String artifactName) {
        return java.util.Arrays.stream(artifactName.split("[-_]+"))
                .filter(s -> !s.isBlank())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
