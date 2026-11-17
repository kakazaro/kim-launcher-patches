# Patch candidates

These are ranked by how well each is supported by evidence in [apk-analysis.md](apk-analysis.md).
Entitlement, PRO, Trial and licensing checks are out of scope
(see analysis section 7).

| # | Patch | Default | Target (dex) | Status |
|---|---|---|---|---|
| 1 | **Allow non-BYD devices**: skip the "available only on BYD head units" screen (emulator or bench testing) | off | `r2.a.a(Context)Z` returns `true` | Hook located; ready to implement |
| 2 | **Disable in-app updates**: the upstream OTA cannot install over a re-signed build | on | `K1/uc.c(I,String,Z,Z)LinkedHashMap` | **Implemented**: `DisableOtaUpdatesPatch.kt` |
| 4 | **Show subscription check result**: toast with the check result and offline time left | on | end of `K1/sd.f0`, null-token branch of `K1/t6.run` | **Implemented**: `ShowSubscriptionCheckPatch.kt` (see [entitlement-check.md](entitlement-check.md)) |
| 3 | **Telegram debug channel**: decide whether to disable it after reading what it sends | ? | `byd/telegram_debug` handler | Needs investigation |

## 1. Allow non-BYD devices

- Fingerprint: a class whose `<clinit>` holds the strings `android.hardware.BYDAutoManager`
  and `com.byd.carsettings`; method `a`, `public final`, params `[Landroid/content/Context;]`,
  returns `Z`.
- Edit: prepend `const/4 v0, 0x1` and `return v0`.
- Caveats: on non-BYD hardware every `BYDAUTO_*` API is absent, so vehicle features will not
  work, and the bootstrap may log failures. The gate result is also used by the Sentry
  services and receivers and by the accessibility service. Test that these do not crash-loop
  when the BYD framework classes are missing.

## 2. Disable in-app updates (implemented)

Native call path (1.0.2781):

```
Dart --"byd/update"--> A0/g.onMethodCall (switch id 0x16) --> A0/g.r(call, result)
   "check"        -> K1/qc runnable --+
   "install"      -> K1/rc runnable --+--> K1/D0.g() on K1/uc.c executor --> K1/uc.c(...)
   "cachedStatus" -> K1/uc.b() (SharedPreferences "KimLauncherUpdates", no network)
background check  -> K1/d9 (catch-all) ------------------------------> K1/uc.c(...)
```

`K1/uc.c(int flags, String source, boolean install, boolean force)` does the whole job: it
calls `.../otaAndroidLatest?vehicleId=…&versionCode=2393`, downloads and checks
`kim-launcher-update.apk` (size, SHA-256, package), then opens the package installer. It already
reports failure by throwing. `K1/D0.g()` catches the exception and returns `result.error` to
Dart (`K1/M5`), and `K1/d9` swallows it.

- Fingerprint: `public static`, `(I, Ljava/lang/String;, Z, Z) -> Ljava/util/LinkedHashMap;`,
  strings `/otaAndroidLatest?vehicleId=`, `kim-launcher-update.apk`,
  `Downloaded APK checksum does not match.`. Unique in 1.0.2781.
- Edit: at index 0, `throw new IllegalStateException("In-app updates are disabled in this
  patched build.")` (`.locals 29`, so v0 and v1 are free). The edited class assembles to dex.
- Not covered: Dart also references `https://kim.storageclbsync.cc/android/PackageInstaller.apk`.
  That is a separate helper download that Morphe cannot reach.
- Not yet verified on a device: what the update screen shows for the error.

## 3. `byd/telegram_debug`

- *Unknown:* what data is sent and when. Read the handler before deciding.
