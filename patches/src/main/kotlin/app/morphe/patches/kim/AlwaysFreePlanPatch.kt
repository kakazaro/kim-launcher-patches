package app.morphe.patches.kim

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

/**
 * The subscription fetch (K1/sd.f0 in 1.0.2781, see docs/entitlement-check.md):
 * `f0(VehicleIdentity, String token, boolean force): EntitlementRecord`. It GETs
 * `.../vehicles/<id>/entitlement.json`, stores the parsed record in a static field, saves it and
 * tells Dart. Both strings below sit only in this method.
 */
private object EntitlementFetchFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("L", "Ljava/lang/String;", "Z"),
    strings = listOf("/entitlement.json", "VehicleLocationSync entitlement plan="),
)

/**
 * The answer every check stores instead of asking the server. Same shape the server sends
 * (read by K1/sa.l: `plan`, `status`, `expiresAt`, `paidUntil`). Only the free plan: nothing in
 * here can unlock a Trial/PRO feature.
 */
private const val FREE_PLAN_JSON = """{"plan":"free","status":"disabled","expiresAt":0,"paidUntil":0}"""

@Suppress("unused")
val alwaysFreePlanPatch = bytecodePatch(
    name = "Always free plan (skip subscription check)",
    description = "Stops Kim Launcher asking its server about the subscription: every check " +
        "immediately stores and returns the free plan. Paid (Trial/PRO) features stay locked, " +
        "even if the car has a subscription.",
    default = true,
) {
    compatibleWith(compatibility)

    execute {
        val fetch = EntitlementFetchFingerprint.method
        val owner = fetch.definingClass
        val record = fetch.returnType
        val vehicleType = fetch.parameterTypes[0].toString()
        val code = fetch.implementation!!.instructions.toList()

        fun fail(what: String): Nothing =
            throw PatchException("Unrecognized subscription fetch layout: $what; method=$fetch")

        // Every name below is read from f0's own instructions, so renamed (obfuscated) classes
        // in a later app version don't matter.
        fun methods(opcode: Opcode, keep: (MethodReference) -> Boolean) = code
            .filter { it.opcode == opcode }
            .mapNotNull { it.getReference<MethodReference>() }
            .filter(keep)
            .map { ReferenceUtil.getMethodDescriptor(it) }
            .distinct()

        fun fields(opcode: Opcode, keep: (FieldReference) -> Boolean) = code
            .filter { it.opcode == opcode }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter(keep)
            .map { ReferenceUtil.getFieldDescriptor(it) }
            .distinct()

        // parse(vehicleId, json, nowMs): the server JSON -> record (plan, status, expiresAt/paidUntil).
        val parse = methods(Opcode.INVOKE_STATIC) {
            it.returnType == record &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/lang/String;", "Lorg/json/JSONObject;", "J")
        }.singleOrNull() ?: fail("missing unique record parser")

        // The in-memory current record, saved-to-preferences call and Dart notification.
        val currentRecord = fields(Opcode.SPUT_OBJECT) {
            it.definingClass == owner && it.type == record
        }.singleOrNull() ?: fail("missing unique current-record field")
        val save = methods(Opcode.INVOKE_STATIC) {
            it.definingClass == owner && it.returnType == "V" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(record)
        }.singleOrNull() ?: fail("missing unique save call")
        val notify = methods(Opcode.INVOKE_STATIC) {
            it.definingClass == owner && it.returnType == "V" && it.parameterTypes.isEmpty()
        }.singleOrNull() ?: fail("missing unique notify call")

        // The vehicle id: the one String field f0 reads from its first parameter.
        val vehicleId = fields(Opcode.IGET_OBJECT) {
            it.definingClass == vehicleType && it.type == "Ljava/lang/String;"
        }.singleOrNull() ?: fail("missing unique vehicle id field")

        // Same ending as the original success path (store, save, notify, return), minus the
        // request: v0/v1 = now, v2 = vehicle id, v3 = the fixed free-plan JSON object (v4 holds
        // its text). f0 has 11 locals, so v0..v4 are free.
        val freePlanJson = FREE_PLAN_JSON.replace("\"", "\\\"")
        fetch.addInstructions(
            0,
            """
                invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
                move-result-wide v0
                iget-object v2, p0, $vehicleId
                new-instance v3, Lorg/json/JSONObject;
                const-string v4, "$freePlanJson"
                invoke-direct {v3, v4}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V
                invoke-static {v2, v3, v0, v1}, $parse
                move-result-object v0
                sput-object v0, $currentRecord
                invoke-static {v0}, $save
                invoke-static {}, $notify
                return-object v0
            """,
        )
    }
}
