package app.morphe.patches.kim

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val TOAST = "Lapp/morphe/extension/kim/EntitlementToast;"
private const val CONTEXT = "Landroid/content/Context;"
private const val OBJECT = "Ljava/lang/Object;"

/**
 * The app's subscription fetch (K1/sd.f0 in 1.0.2781): returns the cached status without a request
 * if it is under 15 minutes old, otherwise GETs vehicles/<id>/entitlement.json, saves the result and
 * returns it. On a failed request it keeps the old status and only records lastError.
 */
private object SubscriptionFetchFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("L", "Ljava/lang/String;", "Z"),
    strings = listOf(
        "VehicleLocationSync entitlement plan=",
        "VehicleLocationSync entitlement failed vehicle=",
    ),
)

/**
 * The refresh task Dart triggers (K1/t6.run, switch case 0): fetches a sign-in token and calls the
 * fetch above only if it got one. With no token (typical when offline) it silently does nothing.
 */
private object SubscriptionRefreshTaskFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    strings = listOf("VehicleLocationSync entitlement refresh error: "),
)

@Suppress("unused")
val showSubscriptionCheckPatch = bytecodePatch(
    name = "Show subscription check result",
    description = "Shows a toast with the result of Kim Launcher's own subscription check (OK, failed, " +
        "or skipped because offline) and how long the saved status keeps working offline. " +
        "Read-only: it does not change the check, the saved status or any limit.",
    default = true,
) {
    compatibleWith(compatibility)
    extendWith("extensions/kim.mpe")

    execute {
        val fetch = SubscriptionFetchFingerprint.method
        val fetchCode = fetch.implementation!!.instructions.toList()

        // The app's own Context holder, as already read by the fetch's logging code.
        val context = fetchCode.firstNotNullOfOrNull { instruction ->
            if (instruction.opcode == Opcode.SGET_OBJECT) {
                instruction.getReference<FieldReference>()?.takeIf { it.type == CONTEXT }
            } else {
                null
            }
        } ?: throw PatchException("Subscription fetch: no static Context field")
        val contextField = "${context.definingClass}->${context.name}:$CONTEXT"

        // 1) Fetch finished (success or failure): report the result it is about to return.
        val last = fetchCode.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        if (last < 0) throw PatchException("Subscription fetch: no result return")
        val result = (fetchCode[last] as OneRegisterInstruction).registerA
        if (result > 15) throw PatchException("Subscription fetch: result register v$result out of reach")
        fetch.addInstructions(
            last,
            """
                sget-object v0, $contextField
                invoke-static {v0, v$result}, $TOAST->onCheck($CONTEXT$OBJECT)V
            """,
        )

        // 2) Refresh found no token and skipped the fetch: `if-nez token, :fetch; goto :done; :fetch`.
        val task = SubscriptionRefreshTaskFingerprint.method
        val taskCode = task.implementation!!.instructions.toList()
        val call = taskCode.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_STATIC && instruction.getReference<MethodReference>()?.let {
                it.definingClass == fetch.definingClass && it.name == fetch.name &&
                    it.parameterTypes.map(CharSequence::toString) == fetch.parameterTypes.map(CharSequence::toString)
            } == true
        }
        if (call < 2 || taskCode[call - 2].opcode != Opcode.IF_NEZ ||
            taskCode[call - 1].opcode !in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
        ) throw PatchException("Subscription refresh: unexpected shape before the fetch call (index $call)")
        // The token register is null on the skip path, so it is free to hold the Context.
        val token = (taskCode[call - 2] as OneRegisterInstruction).registerA
        if (token > 15) throw PatchException("Subscription refresh: token register v$token out of reach")
        task.addInstructions(
            call - 1,
            """
                sget-object v$token, $contextField
                invoke-static {v$token}, $TOAST->onSkipped($CONTEXT)V
            """,
        )
    }
}
