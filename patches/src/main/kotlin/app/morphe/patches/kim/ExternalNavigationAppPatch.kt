package app.morphe.patches.kim

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

private const val EXT = "Lapp/morphe/extension/kim/ExternalNav;"
private const val EXT_VIEW = "Lapp/morphe/extension/kim/ExternalMapView;"
private const val PICKER = "app.morphe.extension.kim.ExternalNavPickerActivity"

/**
 * Where KimLauncherActivity registers every Flutter channel and platform view
 * (configureFlutterEngine). The Kim Map view factory is registered right after the
 * "byd/native_kim_map" string.
 */
private object FlutterEngineSetupFingerprint : Fingerprint(
    strings = listOf("byd/native_kim_map", "byd/navigation_vd"),
)

/**
 * The launcher of Kim's old embedded navigation (a merged lambda `run()`, K1/v in 1.0.2781 and
 * 1.0.2785). It calls Kim's local ADB shell client (K1/Y8.m in 1.0.2781, K1/p9.m in 1.0.2785),
 * `m(Context, String command, Function0 cancel, int defaultsMask): ShellResult`, and reads the
 * result's `ok` and exit code. The client's own strings moved into a lambda in 1.0.2785, so it is
 * found through this caller.
 */
private object AdbShellCallerFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("NavigationVD shell launch ok="),
)

private val pickerActivityPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val app = doc.getElementsByTagName("application").item(0)
            val activity = doc.createElement("activity").apply {
                setAttribute("android:name", PICKER)
                setAttribute("android:label", "Kim navigation app")
                setAttribute("android:exported", "true")
                setAttribute("android:excludeFromRecents", "true")
                setAttribute("android:taskAffinity", "")
                setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
            }
            val filter = doc.createElement("intent-filter")
            filter.appendChild(doc.createElement("action").apply {
                setAttribute("android:name", "android.intent.action.MAIN")
            })
            filter.appendChild(doc.createElement("category").apply {
                setAttribute("android:name", "android.intent.category.LAUNCHER")
            })
            activity.appendChild(filter)
            app.appendChild(activity)
        }
    }
}

@Suppress("unused")
val externalNavigationAppPatch = bytecodePatch(
    name = "External navigation app",
    description = "Lets the Navigation tab show an installed map app (Google Maps, Waze, ...) " +
        "instead of Kim Map. Choose the app, or go back to Kim Map, with the new " +
        "\"Kim navigation app\" icon in the car's app list. Needs Kim's ADB setup.",
    default = false,
) {
    compatibleWith(compatibility)
    dependsOn(pickerActivityPatch)
    extendWith("extensions/kim.mpe")

    execute {
        fun fail(what: String): Nothing = throw PatchException("External navigation app: $what")

        // 1. ExternalNav.shell -> Kim's ADB client; true when it ran and exited 0.
        val caller = AdbShellCallerFingerprint.method.implementation!!.instructions
        val adb = caller.filter { it.opcode == Opcode.INVOKE_STATIC }
            .mapNotNull { it.getReference<MethodReference>() }
            .filter {
                it.returnType.startsWith("L") && it.parameterTypes.map(CharSequence::toString).let { p ->
                    p.size == 4 && p[0] == "Landroid/content/Context;" && p[1] == "Ljava/lang/String;" && p[3] == "I"
                }
            }
            .distinctBy { ReferenceUtil.getMethodDescriptor(it) }.singleOrNull()
            ?: fail("no unique ADB shell call")
        fun resultField(opcode: Opcode) = caller.filter { it.opcode == opcode }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass == adb.returnType }
            .map { ReferenceUtil.getFieldDescriptor(it) }
            .distinct().singleOrNull() ?: fail("no unique $opcode result field of ${adb.returnType}")
        val ok = resultField(Opcode.IGET_BOOLEAN)
        val exitCode = resultField(Opcode.IGET)

        // Only p-registers: the stub may be compiled with no locals. p2 must hold null, not an
        // Object-typed value, to pass the verifier as the Function0 argument.
        mutableClassDefBy(EXT).methods.single { it.name == "shell" }.addInstructionsWithLabels(
            0,
            """
                const/4 p2, 0x0
                invoke-static {p0, p1, p2, p3}, ${ReferenceUtil.getMethodDescriptor(adb)}
                move-result-object p0
                iget-boolean p1, p0, $ok
                if-eqz p1, :fail
                iget p1, p0, $exitCode
                if-nez p1, :fail
                const/4 p1, 0x1
                return p1
                :fail
                const/4 p1, 0x0
                return p1
            """,
        )

        // 2. The Kim Map factory: the constructor called right after "byd/native_kim_map".
        val setup = FlutterEngineSetupFingerprint.method.implementation!!.instructions.toList()
        val nameAt = setup.indexOfFirst {
            (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                it.getReference<StringReference>()?.string == "byd/native_kim_map"
        }
        val initAt = (nameAt + 1 until minOf(nameAt + 12, setup.size)).firstOrNull {
            setup[it].opcode == Opcode.INVOKE_DIRECT &&
                setup[it].getReference<MethodReference>()?.name == "<init>"
        } ?: fail("no factory constructor after byd/native_kim_map")
        val init = setup[initAt].getReference<MethodReference>()!!
        val factory = mutableClassDefBy(init.definingClass)
        val create = factory.methods.singleOrNull { it.name == "create" && it.parameterTypes.size == 3 }
            ?: fail("no create(Context, int, Object) in ${init.definingClass}")
        val platformView = create.returnType

        // R8 merged several factories into one class (switching on an int field), so remember
        // which instance is the Kim Map one, right after it is constructed.
        val factoryReg = (setup[initAt] as FiveRegisterInstruction).registerC
        FlutterEngineSetupFingerprint.method.addInstructions(
            initAt + 1,
            "invoke-static/range {v$factoryReg .. v$factoryReg}, $EXT->setKimMapFactory(Ljava/lang/Object;)V",
        )
        if (create.implementation!!.registerCount - 4 < 1) fail("create() has no free register")
        create.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0, p1}, $EXT->createView(Ljava/lang/Object;Landroid/content/Context;)Ljava/lang/Object;
                move-result-object v0
                if-eqz v0, :kim_map
                check-cast v0, $platformView
                return-object v0
            """,
            ExternalLabel("kim_map", create.getInstruction(0)),
        )

        // 3. Our view becomes a PlatformView. Every abstract method must already exist on it.
        val viewClass = mutableClassDefBy(EXT_VIEW)
        val missing = classDefBy(platformView).methods
            .filter { AccessFlags.ABSTRACT.isSet(it.accessFlags) }
            .filter { m ->
                val sig = ReferenceUtil.getMethodDescriptor(m).substringAfter("->")
                viewClass.methods.none { ReferenceUtil.getMethodDescriptor(it).substringAfter("->") == sig }
            }
        if (missing.isNotEmpty()) fail("ExternalMapView lacks ${missing.map { it.name }} of $platformView")
        viewClass.interfaces.add(platformView)
    }
}
