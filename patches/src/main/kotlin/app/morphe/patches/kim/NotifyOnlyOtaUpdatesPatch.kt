package app.morphe.patches.kim

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The native update routine behind the `byd/update` channel (byd/md.c in 1.0.2787):
 * `c(int flags, String source, boolean install, boolean force)` checks otaAndroidLatest,
 * caches the result for the update UI, and, when `install` is true and an update exists,
 * downloads kim-launcher-update.apk, verifies it and opens the installer.
 *
 * Forcing `install` (p2) to false keeps the check and the cached "update available" status but
 * never downloads or installs. the update gate still requires an active Trial or paid PRO plan, so with
 * "Always free plan" the check simply fails and nothing is shown or downloaded.
 */
private object OtaUpdateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/util/LinkedHashMap;",
    parameters = listOf("I", "Ljava/lang/String;", "Z", "Z"),
    strings = listOf(
        "https://asia-southeast1-byd-car-tracker.cloudfunctions.net/otaAndroidLatest?vehicleId=",
        "kim-launcher-update.apk",
        "Downloaded APK checksum does not match.",
    ),
)

@Suppress("unused")
val notifyOnlyOtaUpdatesPatch = bytecodePatch(
    name = "Notify-only in-app updates",
    description = "Lets Kim Launcher check for a newer upstream version and show it, but never " +
        "downloads or installs it (the upstream update is signed differently and cannot install " +
        "over a patched build). The check needs an active Trial or paid PRO plan.",
    default = true,
) {
    compatibleWith(compatibility)

    execute {
        OtaUpdateFingerprint.method.addInstructions(0, "const/16 p2, 0x0")
    }
}
