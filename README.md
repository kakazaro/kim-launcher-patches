# Kim Launcher Patches

[Morphe](https://github.com/MorpheApp) patches for **Kim Launcher** (`com.kimkim`), a Flutter
launcher for BYD DiLink head units. The structure follows
[fangkampanat/gmaps-patches](https://github.com/fangkampanat/gmaps-patches).

## Patches

| Patch | Default | Description |
|---|---|---|
| Notify-only in-app updates | on | Lets the app check for and show a newer upstream version but never downloads or installs it (it cannot install over a patched build). The check needs an active Trial or paid plan |
| Always free plan (skip subscription check) | on | Every subscription check returns the free plan without contacting the server. Trial/PRO features stay locked. |
| External navigation app | off | The Navigation tab shows an installed map app (Google Maps, Waze, ...) instead of Kim Map. See [External navigation app](docs/external-navigation.md). |

## Docs

- [APK analysis](docs/apk-analysis.md): identity, architecture (Dart vs dex), startup and
  device gate, MethodChannels, re-signing notes
- [Subscription check](docs/entitlement-check.md): how the app checks, caches and expires it
- [Network usage](docs/network-usage.md): every internet endpoint, by feature
- [Patch candidates](docs/patch-candidates.md): what can be patched and where
- [External navigation app](docs/external-navigation.md): how to use it, how it works, limits
- [Decompiled code](docs/decompiled-code.md): where the important app code is and how to regenerate it
- [Build setup](docs/build-setup.md): GitHub Packages credentials for the Morphe plugin

Patches never change, bypass or extend the app's subscription checks or limits.

## Layout

```
patches/src/main/kotlin/app/morphe/patches/kim/   patch sources (KimCompatibility.kt = target app)
docs/                                             analysis and setup
refer-apk/                                        reference APK (git-ignored)
.work/                                            local decompile output (git-ignored)
```

## Build

Needs Java 21+ and `gpr.user`/`gpr.key` in `%USERPROFILE%\.gradle\gradle.properties`
(see [docs/build-setup.md](docs/build-setup.md)).

```bash
./gradlew build
```

The bundle is written to `patches/build/libs/patches-<version>.mpp`.

## Tested

| App version | Status |
|---|---|
| 1.0.2785 | fingerprints match, patched APK builds; not yet run on a device |
| 1.0.2781 (2393) | fingerprints match; not yet run on a device |

## License

GPL-3.0, see [LICENSE](LICENSE).
