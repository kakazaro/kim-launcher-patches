package app.morphe.extension.kim;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * "Kim navigation app": picks the app shown in the Navigation tab, or Kim Map. Kim's Settings tab
 * is Dart code and cannot be patched, so this is a separate launcher entry. Restarts Kim to apply.
 */
public final class ExternalNavPickerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        apps.removeIf(ri -> ri.activityInfo.packageName.equals(getPackageName()));
        apps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        String current = ExternalNav.selectedPackage(this);
        List<String> packages = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        packages.add(null);
        labels.add("Kim Map (default)");
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (packages.contains(pkg)) continue;
            packages.add(pkg);
            labels.add(ri.loadLabel(pm).toString());
        }
        int checked = Math.max(packages.indexOf(current), 0);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Navigation tab app")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    ExternalNav.setSelectedPackage(this, packages.get(which));
                    restartKim();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    /** The Navigation tab only builds its map view at start, so relaunch Kim to apply. */
    private void restartKim() {
        Intent start = new Intent().setClassName(this, "com.kimkim.KimDeviceGateActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, start,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
        ((AlarmManager) getSystemService(ALARM_SERVICE))
                .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, pi);
        finishAffinity();
        System.exit(0);
    }
}
