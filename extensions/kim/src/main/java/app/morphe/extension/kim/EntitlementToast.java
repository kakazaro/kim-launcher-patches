package app.morphe.extension.kim;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only: shows the result of Kim Launcher's own subscription check as a toast. It never
 * changes the result, the saved status or any expiry. Called from the app's entitlement fetch
 * (onCheck) and from the periodic refresh when it skips the fetch for lack of a sign-in token
 * (onSkipped).
 */
@SuppressWarnings("unused")
public final class EntitlementToast {
    private static final String TAG = "KimEntitlementToast";
    // The app's own saved status (written by its fetch code): plan, status, expiresAt, fetchedAt.
    private static final String PREFS = "VehicleLocationSync";
    // Mirrors the app: a saved status stops counting as active 7 days after the last successful fetch.
    private static final long OFFLINE_LIMIT_MS = 7L * 24 * 60 * 60 * 1000;
    // RemoteEntitlement(vehicleId=.., plan=.., status=.., expiresAtMs=.., fetchedAtMs=.., lastError=..)
    private static final Pattern RESULT = Pattern.compile(
            "plan=([^,]*), status=([^,]*), expiresAtMs=(-?\\d+), fetchedAtMs=(-?\\d+), lastError=(.*)\\)\\s*$",
            Pattern.DOTALL);

    // ponytail: skips only an identical repeat of the last message (offline, the countdown text changes
    // hourly, so expect about one toast per hour); add a real rate limit if that nags.
    private static String lastMessage;

    private EntitlementToast() {
    }

    /** The app finished a subscription fetch (successful or not); result is its RemoteEntitlement. */
    public static void onCheck(Context context, Object result) {
        try {
            String message = fromResult(String.valueOf(result), System.currentTimeMillis());
            if (message != null) show(context, message);
        } catch (Throwable t) {
            Log.w(TAG, "not shown", t);
        }
    }

    /** Null when the text is not a RemoteEntitlement (for example after an app update changed it). */
    static String fromResult(String text, long now) {
        Matcher m = RESULT.matcher(text);
        if (!m.find()) return null;
        String error = m.group(5).trim();
        return describe(m.group(1), m.group(2), Long.parseLong(m.group(3)), Long.parseLong(m.group(4)),
                "null".equals(error) ? null : error, now);
    }

    /** The periodic refresh found no sign-in token, so it did not contact the server at all. */
    public static void onSkipped(Context context) {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            show(context, describe(p.getString("plan", "none"), p.getString("status", "none"),
                    p.getLong("expiresAt", 0), p.getLong("fetchedAt", 0),
                    "no sign-in token (offline?), server not contacted", System.currentTimeMillis()));
        } catch (Throwable t) {
            Log.w(TAG, "not shown", t);
        }
    }

    /** error == null means the server answered. Pure function so it can be checked off-device. */
    static String describe(String plan, String status, long expiresAt, long fetchedAt, String error, long now) {
        boolean paidPlan = "paid".equals(plan) || "trial".equals(plan);
        boolean entitled = paidPlan && "active".equals(status) && expiresAt > now;
        StringBuilder sb = new StringBuilder("Kim subscription check: ");
        if (error == null) {
            sb.append("OK, ").append(plan).append('/').append(status);
            if (expiresAt > 0) sb.append(", until ").append(date(expiresAt));
            if (!entitled) sb.append(". NOT active");
            return sb.toString();
        }
        if (error.length() > 90) error = error.substring(0, 90) + "...";
        sb.append("FAILED (").append(error).append("). Saved status: ").append(plan).append('/').append(status);
        if (expiresAt > 0) sb.append(", until ").append(date(expiresAt));
        if (!entitled) return sb.append(". NOT active").toString();
        if (fetchedAt <= 0) return sb.append(". No time limit recorded").toString();
        long left = fetchedAt + OFFLINE_LIMIT_MS - now;
        if (left <= 0) return sb.append(". Older than 7 days: locked until a check succeeds").toString();
        long minutes = left / 60000;
        return sb.append(". Stays active offline for ").append(minutes / 1440).append("d ")
                .append(minutes / 60 % 24).append("h more").toString();
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private static void show(final Context context, final String message) {
        if (context == null) return;
        synchronized (EntitlementToast.class) {
            if (message.equals(lastMessage)) return;
            lastMessage = message;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
