# How Kim Launcher checks the subscription (1.0.2781)

Static analysis only; the app was not run. This documents what the shipped code does for
reference. No patch changes the check, the saved status or any limit.

## Where to read it

JADX renames classes whose names collide on Windows, so `K1/sd` is `C0322sd.java`; the smali
files carry a `.1` suffix. Paths are under `decompiled/`.

| What | Java | Smali | Look for |
|---|---|---|---|
| **Readable rewrite of `f0` + helpers, with a name map** | `decompiled/annotated/EntitlementFetch.java` | | start here |
| **`f0` after the Always-free patch** (before/after table) | `decompiled/annotated/EntitlementFetch_patched.java` | | |
| Fetch + cache (`K1/sd.f0`) | `java/K1/C0322sd.java` (line ~3451) | `smali/K1/sd.1.smali` | `entitlement.json`, `f0(`, field `f5977z0` (= `z0`) |
| Status record + "is active" (`K1/md`) | `java/K1/C0233md.java` (`d()` ~line 57) | `smali/K1/md.1.smali` | `604800000` (7 days) |
| Vehicle/auth record (`K1/pd`) | `java/K1/C0278pd.java` | `smali/K1/pd*.smali` | `f5700a` (vehicle ID) |
| Refresh task (`K1/t6.run`) | `java/K1/RunnableC0330t6.java` (`run()` ~line 55) | `smali/K1/t6.1.smali` | null-token branch |
| Dart caller | `dart/byd_launcher/main.dart` line ~147383 | | `_refreshRemoteEntitlement`, `"refreshEntitlement"` |

`main.dart` is 37 MB: open it at the line, or `grep -n '_refreshRemoteEntitlement' decompiled/dart/byd_launcher/main.dart`.

## The status record

Kept in memory (`K1/sd.z0`) and saved in SharedPreferences `VehicleLocationSync`
as one JSON string under the key `entitlement` (fields `plan`, `status`, `expiresAt`, `fetchedAt`),
written by `K1/sd.V` and loaded back by `K1/sd.M` (when it runs, I did not trace the call) (falls back to `free/disabled` if it
cannot be read). It survives restarts; clearing app data loses it. Expiry tests use the device clock.

| Field | Meaning |
|---|---|
| `plan` | `trial`, `paid`, or anything else (`free`) |
| `status` | `active`, or anything else (`disabled`, ...) |
| `expiresAtMs` | subscription end, from the server (`expiresAt`, or `paidUntil` if later) |
| `fetchedAtMs` | when the server was **last successfully** asked |
| `lastError` | text of the last failed attempt, or null |

## The check

`GET <Firebase RTDB>/vehicles/<vehicleId>/entitlement.json?auth=<token>`, where the token is an
anonymous Firebase sign-in token (so a sign-in also needs the internet).

```
refresh task (K1/t6) ── token available? ── no ──> nothing happens (silent)
                                  │ yes
                                  v
              fetch (K1/sd.f0; force=true from Dart start/foreground/tab)
   saved status under 15 min old, same vehicle? ── yes ──> return it, no request
                                  │ no
                                  v
                       GET entitlement.json
        answered ──> parse, fetchedAt = now, lastError = null, save
        failed   ──> keep plan/status/expiresAt/fetchedAt, only set lastError
```

`force=true` skips the 15-minute shortcut and always sends the request; `force=false` uses it.

## When does the app ask the server again?

Read from the Dart assembly (`decompiled/dart/byd_launcher/main.dart`) and the native callers.
The Dart method `_refreshRemoteEntitlement({force})` sends **`refreshEntitlement`** when
`force` is true (native calls the fetch with force=true: a real request) and **`getStatus`**
when false (returns the saved status; **no request**).

| # | Trigger | Real request? |
|---|---|---|
| 1 | App start (`initState`) | **yes**, force=true |
| 2 | App comes back to the foreground (`didChangeAppLifecycleState`) | **yes**, force=true |
| 3 | Switching to one particular launcher tab (`_handleTabChanged`, tab not identified) | **yes**, force=true |
| 4 | 60-second timer while the app is open (`Timer.periodic`, `0x3938700` µs) | no, `getStatus` only |
| 5 | A timer set for the moment the saved subscription expires (`_scheduleRemoteEntitlementExpiryCheck`) | no, `getStatus` only |
| 6 | Cloud-sync uploads (trips, charge/refuel sessions, Sentry previews, event queue: `K1/a1`, `n1`, `D0`, `Ba`, `z3`, `sd.S`) call the fetch before uploading | only if the saved status is over 15 min old |
| 7 | Features that check the saved status in code without any request: Kim AI (`AI_ENTITLEMENT_REQUIRED`), Smart Key, Sentry and so on (`md.d`) | no |

So in a car that often loses signal, a real check happens each time you **start the app, wake
the head unit back into the app, or open that tab** while online. The 60-second timer does not
call the server. After a long drive without signal, the first foreground return with network
is what refreshes the 7-day clock.

Other server calls that are not plan checks but are in the same file: `launcherMapboxEligibility`
(sent when the app builds its vehicle-status map; what Dart does with a failure is *unknown*),
`launcherMonthlyActivity`, `launcherClaimWriterRepair`, and the update check (`otaAndroidLatest`,
removed by the **Disable in-app updates** patch). The Kim AI web endpoint (`botbtc.online/kim-search`)
is called from Dart; whether that server enforces the plan on its own side is *unknown*.

## Is it active? (`K1/md.d`)

All of these must hold:

1. `plan` is `trial` or `paid`
2. `status` is `active`
3. `expiresAt` is in the future
4. `fetchedAt` is unset **or** less than **7 days** ago (`0x240c8400` ms)

`entitlementGrace` (shown to Dart) just means "active, but not verified in the last 15 minutes".

## What this means for a car without signal

- A failed check **never erases** your saved status. It only records the error.
- The 7 days count from the last **successful** check, not the last attempt.
- So the car is fine as long as it gets one successful check at least every 7 days
  (a phone hotspot for a few minutes is enough), and your subscription itself hasn't ended.
- After 7 days with no successful check the app treats the status as expired ("Entitlement cache
  expired") and locks the features until a check succeeds. Nothing is lost; the next successful
  check restores it.
- With no sign-in token the refresh does nothing at all, with no error shown. That is the usual
  offline case.
- If the server answers but has no entitlement for the vehicle (`null`), the app stores
  `free/disabled`: that is "not subscribed", not an offline problem.
- Whether the Dart UI applies extra limits of its own on top of this is *unknown* (not
  readable from the snapshot).

This 7-day limit is the developer's design, and this project does not change it. If it doesn't fit
how you use the car, ask the developer for a longer offline period.
