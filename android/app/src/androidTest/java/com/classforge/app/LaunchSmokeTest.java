package com.classforge.app;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/** Release APK must cold-launch the browser rather than return home/crash. */
public class LaunchSmokeTest {
    @Test public void launchesInstalledBrowserWithoutCrashing() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String choice = InstrumentationRegistry.getArguments().getString("notificationChoice", "grant");
        context.getSharedPreferences("notification-onboarding", 0).edit().clear().commit();
        boolean needsPermission = Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        device.pressHome();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(intent);
        if (needsPermission) {
            var enable = device.wait(Until.findObject(By.text("Activar avisos")), 10000);
            assertTrue("Native permission must be requested before opening the browser", enable != null);
            if ("skip".equals(choice)) {
                device.findObject(By.text("Continuar sin avisos")).click();
            } else {
                enable.click();
                if ("rotate".equals(choice)) device.setOrientationLeft();
                String button = "deny".equals(choice) ? "permission_deny_button" : "permission_allow_button";
                var permission = device.wait(Until.findObject(By.res("com.android.permissioncontroller", button)), 10000);
                assertTrue("Android must request ClassForge's notification permission", permission != null);
                permission.click();
            }
        } else {
            assertTrue("Skip/deny/rotation fixtures need a fresh installation", "grant".equals(choice));
        }
        try {
            assertTrue("ClassForge release must open Chrome, not crash or return to launcher",
                    device.wait(Until.hasObject(By.pkg("com.android.chrome")), 20000));
            if (Build.VERSION.SDK_INT >= 33) {
                boolean expected = !"skip".equals(choice) && !"deny".equals(choice);
                assertTrue("Native permission must match the user's choice before Chrome opens",
                        (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) == expected);
            }
        } finally {
            if ("rotate".equals(choice)) device.setOrientationNatural();
        }
    }
}
