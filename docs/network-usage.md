# Network usage (1.0.2781)

Every endpoint found in the shipped code, grouped by feature. Static analysis only: these are
the URLs in the code, not observed traffic.

## Native side (dex): Morphe can block these

All internet REST in the app's own code uses `java.net.URL.openConnection()`: 38 call sites
in 16 classes (`K1/*`, `M1/h`, `N1/I`, `G6/l`, `b0.1/q`). No OkHttp or raw internet
sockets are used in app code.

| Feature | Endpoint | Class | Effect of blocking |
|---|---|---|---|
| **Cloud sync / Kim PWA tracker** (Firebase project `byd-car-tracker`): live location, trip summaries, charging and refuel sessions, event recordings, Sentry parking previews, remote commands | `byd-car-tracker-default-rtdb.asia-southeast1.firebasedatabase.app/vehicles/…` | `K1/sd`, `K1/D0`, `K1/I0`, `K1/Y3`, `K1/a1`, `K1/Da`, `K1/de` | No phone/PWA remote view, remote control or Proximity smart key relay |
| Device identity (anonymous Firebase auth) | `identitytoolkit.googleapis.com/v1/accounts:signUp`, `securetoken.googleapis.com/v1/token` | `K1/sd` | Pairing stays "pending" |
| Usage telemetry | `…cloudfunctions.net/launcherMonthlyActivity`, `launcherClaimWriterRepair` | `K1/sd` | None visible |
| Map eligibility | `…cloudfunctions.net/launcherMapboxEligibility` | `K1/sd` | Kim Map (Mapbox) may report it is unavailable |
| Subscription status | `vehicles/<id>/entitlement.json` | `K1/sd` | PRO/Trial status cannot refresh; paid features lapse when the cached status or grace period runs out. This project does not change that. |
| **Diagnostics to Telegram**: log and crash bundles, including an *automatic* trigger | `api.telegram.org/bot<token>/sendDocument` (hard-coded bot token and group chat ID, redacted here) | `K1/jc` | None for the user; stops logs leaving the car |
| In-app updates | `…cloudfunctions.net/otaAndroidLatest` | `K1/uc` | Already blocked by **Disable in-app updates** |
| Package Installer helper link | `kim.storageclbsync.cc/android/PackageInstaller.apk` | `K1/r` (also in Dart) | Opens a browser link only |
| Voice packs | `kim.storageclbsync.cc/voice-packs/catalog.json` plus pack downloads | `N1/I` | Installed packs keep working; no new downloads |
| Weather widget | `api.weatherapi.com/v1/forecast.json` (hard-coded key) | `M1/h` | No weather |
| News widget / Kim AI answers | `news.google.com/rss…` | `M1/h` | No news |
| Crypto, FX and gold widgets / Kim AI answers | `api.coingecko.com`, `api.frankfurter.dev`, `api.gold-api.com` | `M1/h` | No prices |
| Connectivity probe | `clients3.google.com/generate_204` | `K1/U3` | App considers itself offline |

## Mapbox SDK (Kim Map, navigation)

Mapbox makes its own native HTTP requests (tiles, search, directions, `events.mapbox.com`
telemetry), outside `URL.openConnection`. The SDK has an official switch,
`com.mapbox.common.OfflineSwitch.getInstance().setMapboxStackConnected(false)` (present in the
dex). With it off, only already-cached or downloaded tiles render, and online routing and search
stop.

## Dart side (`libapp.so`): Morphe cannot block these

| Feature | Endpoint |
|---|---|
| Kim search / AI search | `botbtc.online/kim-search` |
| Nearby POIs and speed limits | Overpass API (`overpass-api.de`, `overpass.kumi.systems`, `overpass.private.coffee`) |
| Charger lookup | OpenChargeMap (skipped when no API key: "Nearby V2 provider=openchargemap skipped reason=api_key_missing") |
| Package Installer link | `kim.storageclbsync.cc/android/PackageInstaller.apk` |

Dart's `HttpClient` doesn't go through Java, so the only way to block these is outside the
app: a firewall or DNS block for the hosts above, or no SIM/Wi-Fi.

## Local-only networking (keep)

These must keep working; they're why the `INTERNET` permission cannot simply be removed:

- ADB self-connection: `127.0.0.1:5555` and `_adb-tls-connect._tcp` (`K1/Y8`, `K1/q`, `K1/C`),
  used to grant the app's privileged permissions
- Local servers for the daemons and PWA event feed (`byd/KimBydDilink5ExtendedDaemon`,
  `K1/n1`, port `47888`)
- Wi-Fi dashcams (70mai and others) at `192.168.x.1` (`K1/U3`)
- `https://kim-ic.invalid/`: an in-app WebView address, never resolved
- Deep links handed to other apps (Google Maps, Waze) do no networking inside Kim
