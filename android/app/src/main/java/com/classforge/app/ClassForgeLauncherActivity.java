package com.classforge.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.google.androidbrowserhelper.trusted.LauncherActivity;

/** Obtain the shell permission before Chrome verifies and caches its delegation. */
public class ClassForgeLauncherActivity extends LauncherActivity {
    private static final int NOTIFICATION_REQUEST = 71;
    private static final String PREFERENCES = "notification-onboarding";
    private static final String SEEN = "seen";
    private static final String PENDING = "classforge.notificationPermissionPending";
    private boolean waitingForPermission;

    @Override protected boolean shouldLaunchImmediately() { return false; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (isFinishing()) return;
        waitingForPermission = state != null && state.getBoolean(PENDING);
        if (waitingForPermission) return;
        if (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(SEEN, false)) {
            launchTwa();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Notificaciones de ClassForge")
                .setMessage("Para recibir avisos de tus proyectos, permite las notificaciones de ClassForge en Android. Después podrás activar push desde la campana.")
                .setPositiveButton("Activar avisos", (dialog, which) -> {
                    waitingForPermission = true;
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
                })
                .setNegativeButton("Continuar sin avisos", (dialog, which) -> continueWithoutPrompt())
                .setOnCancelListener(dialog -> continueWithoutPrompt())
                .show();
    }

    private void continueWithoutPrompt() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(SEEN, true).apply();
        launchTwa();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != NOTIFICATION_REQUEST) return;
        waitingForPermission = false;
        continueWithoutPrompt();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(PENDING, waitingForPermission);
        super.onSaveInstanceState(state);
    }
}
