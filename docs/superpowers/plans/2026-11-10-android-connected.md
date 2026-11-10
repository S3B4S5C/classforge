# APK ClassForge conectado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Entregar un APK firmado conectado al ClassForge desplegado y verificarlo.
**Architecture:** TWA oficial, mismo HTTPS/API/WSS que web, DAL por nginx.
**Tech Stack:** Android Browser Helper2.7.4, AGP8.13.2, Gradle8.13, SDK36, Node24, PowerShell.
**Spec:** `docs/superpowers/specs/2026-11-10-android-connected-design.md`.

## Global Constraints

- Origen `https://classforge-tiago-15124.eastus.cloudapp.azure.com`, inicio `/projects`.
- Paquete `com.classforge.app`, versión1.0.0/código1, min24/target36/compile36.
- Trabajar en `tiago`, según preferencia previa; no modificar cuentas/secretos del sistema.
- Firma privada estable en `.android-release/` excluido de Git y del contenido APK.
- Ejecutar inline; el usuario delegó las decisiones y pidió continuar sin más diseño.

## Review Focus

1. Huella distinta del APK → la publicación debe rechazar la asociación inconsistente.
2. Enlaces externos/intents hostiles → solo dominio exacto de producción confiable.
3. Archivos descargados y voz → verificar capacidades reales dentro de Android.
4. Build sin clave o con secreto impreso → fallar claramente y no registrar valores.
5. nginx inválido o archivos ausentes → mantener configuración/servicio anterior.

## Task 1: APK firmado y asociación verificable

**Files:** `android/`, `scripts/android-release.mjs`, `scripts/android-release.test.mjs`,
`scripts/android-create-signing.ps1`, `scripts/android-build.ps1`, `.gitignore`.
**Interfaces:** exportar `createAssetLinks(packageName, fingerprint)` y
`normalizeFingerprint(value)`; build produce `.android-release/classforge.apk`,
checksum, `assetlinks.json` y manifiesto público de metadatos sin secretos.

- [x] Escribir tests DAL: huella válida normalizada, rechazo de huella corta/nohex,
  package inválido y origen configurado sin comodines. Ejecutar RED.
- [x] Implementar utilidades mínimas y módulo Android con launcher oficial.
- [x] Preparar firma privada, SDK local y wrapper verificado; ejecutar tests GREEN.
- [x] Build release/lint, apksigner verify, aapt dump, comprobar huella vs DAL.
- [x] Commit del módulo/scripts públicos; no agregar herramientas/secretos/artefactos.

## Task 2: Publicación Android junto al sistema existente

**Files:** `deploy/azure/vm/nginx-https.conf`, `deploy/azure/vm/install-android.sh`,
`scripts/android-publish.ps1`, `docs/runtime/android-app.md`.
**Interfaces:** consume artefactos de Task1; publica `/.well-known/assetlinks.json`
y `/android/classforge.apk`; no necesita reiniciar Java.

- [x] Validar rechazo de paquete incompleto/huellas inconsistentes antes de SSH.
- [x] Instalar archivos públicos mediante staging, respaldo y nginx -t/reload.
- [x] Verificar HTTPS200/JSON DAL, SHA del APK descargado y smoke previo de hosting.
- [x] Documentar instalación y actualizaciones; commit.

## Task 3: Comprobación Android y cierre

**Files:** evidencia `docs/evidence/parcial2/android-connected.md`, guía/README.
**Interfaces:** consume APK/URL de Tasks1–2 y cuentas sintéticas existentes.

- [x] Instalar y abrir APK en emulador con navegador compatible.
- [x] Verificar asociación TWA, login, proyecto, cambios compartidos y back.
- [x] Probar archivo de imagen y exportaciones; documentar que el micrófono físico de voz requiere una prueba en el teléfono.
- [x] Revisar diff completo con un reviewer fresco y corregir hallazgos importantes.
- [x] Smoke final, checksum/firma, commit/push `tiago`, entregar enlace APK.
