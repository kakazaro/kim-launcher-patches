# Kim Launcher Patches

[Morphe](https://github.com/MorpheApp) patches for **Kim Launcher** (`com.kimkim`), a Flutter
launcher for BYD DiLink head units. The structure follows
[fangkampanat/gmaps-patches](https://github.com/fangkampanat/gmaps-patches).

## Patches

| Patch | Default | Description |
|---|---|---|
| Show subscription check result | on | Toast with the result of the app's own subscription check and how long the saved status works offline. Read-only. See [docs/entitlement-check.md](docs/entitlement-check.md) |
| Disable in-app updates | on | Stops the app checking for, downloading and installing its own updates, which cannot install over a patched build |

## Docs

- [APK analysis](docs/apk-analysis.md): identity, architecture (Dart vs dex), startup and
  device gate, MethodChannels, re-signing notes
- [Subscription check](docs/entitlement-check.md): how the app checks, caches and expires it
- [Network usage](docs/network-usage.md): every internet endpoint, by feature
- [Patch candidates](docs/patch-candidates.md): what can be patched and where
- [Build setup](docs/build-setup.md): GitHub Packages credentials for the Morphe plugin

Patches never change, bypass or extend the app's subscription checks or limits. The one patch that
touches that code only reports its result.

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
| 1.0.2781 (2393) | fingerprints match; not yet run on a device |

## License

GPL-3.0, see [LICENSE](LICENSE).
