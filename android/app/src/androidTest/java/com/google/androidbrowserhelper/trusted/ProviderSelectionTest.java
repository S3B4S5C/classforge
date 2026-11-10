package com.google.androidbrowserhelper.trusted;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Device preferences must not switch the APK to an unverified push provider. */
public class ProviderSelectionTest {
    @Test public void keepsChromeWhenAutomaticSelectionExcludesIt() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var metadata = LauncherActivityMetadata.parse(context);
        TwaProviderPicker.restrictToPackageForTesting("com.sec.android.app.sbrowser");
        try {
            var launcher = new TwaLauncher(context, metadata.launchingBrowser, 96375,
                    new SharedPreferencesTokenStore(context));
            assertEquals("Device browser preferences must not replace the verified push provider",
                    "com.android.chrome", launcher.getProviderPackage());
            launcher.destroy();
        } finally {
            TwaProviderPicker.restrictToPackageForTesting(null);
        }
    }
}
