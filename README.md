# Kim Launcher Patches

[Morphe](https://github.com/MorpheApp) patches for **Kim Launcher** (`com.kimkim`), a Flutter
launcher for BYD DiLink head units. The structure follows
[fangkampanat/gmaps-patches](https://github.com/fangkampanat/gmaps-patches).

## Patches

| Patch | Default | Description |
|---|---|---|
| Disable in-app updates | on | Stops the app checking for, downloading and installing its own updates, which cannot install over a patched build |

## Docs

- [APK analysis](docs/apk-analysis.md): identity, architecture (Dart vs dex), startup and
  device gate, MethodChannels, re-signing notes
- [Patch candidates](docs/patch-candidates.md): what can be patched and where
- [Build setup](docs/build-setup.md): GitHub Packages credentials for the Morphe plugin

Entitlement, PRO, Trial and licensing checks are out of scope for this project.

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
