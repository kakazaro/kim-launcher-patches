package app.morphe.extension.kim;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the Navigation tab show an installed map app (Google Maps, Waze, ...) instead of Kim Map.
 * The choice is made in {@link ExternalNavPickerActivity}; the patched Kim Map view factory calls
 * {@link #createView(Context)}.
 */
@SuppressWarnings("unused")
public final class ExternalNav {
    static final String TAG = "MorpheExternalNav";
    private static final String PREFS = "morphe_external_nav";
    private static final String KEY_PACKAGE = "package";
    private static final ExecutorService SHELL = Executors.newSingleThreadExecutor();

    private ExternalNav() {
    }

    /** The chosen map app, or null for Kim Map. */
    static String selectedPackage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACKAGE, null);
    }

    static void setSelectedPackage(Context context, String packageName) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PACKAGE, packageName).commit();
    }

    /** The registered "byd/native_kim_map" view factory (its class is shared with other factories). */
    private static Object kimMapFactory;

    public static void setKimMapFactory(Object factory) {
        kimMapFactory = factory;
    }

    /**
     * Called first in every create() of the Kim Map factory's class. Returns a Flutter PlatformView
     * showing the chosen app, or null to let the factory build its own view (Kim Map or other).
     */
    public static Object createView(Object factory, Context context) {
        if (factory != kimMapFactory) return null;
        String packageName = selectedPackage(context);
        if (packageName == null) return null;
        // Uninstalled since it was chosen: fall back to Kim Map.
        if (context.getPackageManager().getLaunchIntentForPackage(packageName) == null) return null;
        return new ExternalMapView(context, packageName);
    }

    /**
     * Runs a shell command through Kim Launcher's own local ADB client, the one its ADB setup guide
     * authorizes. The patch replaces this body; keep the signature (it passes p2/p3 straight on).
     * Blocks: call from a background thread.
     */
    static boolean shell(Context context, String command, Object unused, int flags) {
        return false;
    }

    static void shellAsync(Context context, String command) {
        Context app = context.getApplicationContext();
        SHELL.execute(() -> {
            boolean ok;
            try {
                ok = shell(app, command, null, 28);
            } catch (Throwable t) {
                Log.w(TAG, "shell failed: " + command, t);
                return;
            }
            Log.i(TAG, "shell ok=" + ok + ": " + command);
        });
    }

    static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
