package app.morphe.patches.kim

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

internal const val OTA_DISABLED_MESSAGE = "In-app updates are disabled in this patched build."

/**
 * The native update routine behind the `byd/update` channel (K1/uc.c in 1.0.2781): checks
 * otaAndroidLatest, downloads kim-launcher-update.apk, verifies it and opens the installer.
 * It serves the channel's `check` and `install` calls and a background check. Every caller
 * already reports a thrown exception (to Dart as result.error, or swallowed in the background).
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
val disableOtaUpdatesPatch = bytecodePatch(
    name = "Disable in-app updates",
    description = "Stops Kim Launcher checking for, downloading and installing its own updates. " +
        "The upstream update is signed differently and cannot install over a patched build.",
    default = true,
) {
    compatibleWith(compatibility)

    execute {
        // The method has 29 locals, so v0 and v1 are free at entry.
        OtaUpdateFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Ljava/lang/IllegalStateException;
                const-string v1, "$OTA_DISABLED_MESSAGE"
                invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V
                throw v0
            """,
        )
    }
}
