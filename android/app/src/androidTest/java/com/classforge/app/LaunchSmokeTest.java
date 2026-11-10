package com.classforge.app;

import android.content.Intent;
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
        device.pressHome();
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName("com.classforge.app", "com.google.androidbrowserhelper.trusted.LauncherActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(intent);
        assertTrue("ClassForge release must open Chrome, not crash or return to launcher",
                device.wait(Until.hasObject(By.pkg("com.android.chrome")), 20000));
    }
}
