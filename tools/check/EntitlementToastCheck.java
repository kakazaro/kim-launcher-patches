package app.morphe.extension.kim;

/**
 * Off-device check of the toast text. From the repo root:
 *   javac -cp "$ANDROID_JAR" -d .work/check extensions/kim/src/main/java/app/morphe/extension/kim/EntitlementToast.java tools/check/EntitlementToastCheck.java
 *   java -cp ".work/check:$ANDROID_JAR" app.morphe.extension.kim.EntitlementToastCheck
 */
public final class EntitlementToastCheck {
    private static final long H = 3600_000L, D = 24 * H, NOW = 1_800_000_000_000L;

    private static void expect(String got, String... parts) {
        for (String p : parts) if (!got.contains(p)) throw new AssertionError("missing [" + p + "] in: " + got);
        System.out.println("ok: " + got);
    }

    public static void main(String[] a) {
        // server answered, paid and in date
        expect(EntitlementToast.describe("paid", "active", NOW + 30 * D, NOW, null, NOW), "OK, paid/active", "until ");
        // server answered, but the vehicle has no plan
        expect(EntitlementToast.describe("free", "disabled", 0, NOW, null, NOW), "OK, free/disabled", "NOT active");
        // offline, last good check 2 days ago: 5 days left
        expect(EntitlementToast.describe("paid", "active", NOW + 30 * D, NOW - 2 * D, "UnknownHostException: x", NOW),
                "FAILED (UnknownHostException: x)", "5d 0h more");
        // offline for 8 days: past the 7-day limit
        expect(EntitlementToast.describe("paid", "active", NOW + 30 * D, NOW - 8 * D, "HTTP 0", NOW), "Older than 7 days");
        // subscription itself ran out while offline
        expect(EntitlementToast.describe("paid", "active", NOW - D, NOW - D, "HTTP 0", NOW), "NOT active");
        // long error text is cut
        expect(EntitlementToast.describe("paid", "active", NOW + D, NOW, "x".repeat(200), NOW), "...");
        // the app's real toString() format, success then failure with commas and quotes in the error
        String ok = "RemoteEntitlement(vehicleId=veh_1, plan=paid, status=active, expiresAtMs=" + (NOW + 30 * D)
                + ", fetchedAtMs=" + NOW + ", lastError=null)";
        expect(EntitlementToast.fromResult(ok, NOW), "OK, paid/active");
        String bad = "RemoteEntitlement(vehicleId=veh_1, plan=paid, status=active, expiresAtMs=" + (NOW + 30 * D)
                + ", fetchedAtMs=" + (NOW - D) + ", lastError=HTTP 401 body={\"error\":\"x, y\"})";
        expect(EntitlementToast.fromResult(bad, NOW), "FAILED (HTTP 401 body={\"error\":\"x, y\"})", "6d 0h more");
        if (EntitlementToast.fromResult("something else", NOW) != null) throw new AssertionError("should not parse");
        System.out.println("all checks passed");
    }
}
