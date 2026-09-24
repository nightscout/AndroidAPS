package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import kotlin.test.Test

/**
 * Writes REAL, importable settings files so the import can be exercised on a device.
 *
 * Not a test of anything - it is a fixture generator, kept here because this is the only place that
 * can build a file the app will actually accept: the same codec, the same envelope, the same
 * metadata. Each generator no-ops unless its environment variable names a path:
 *
 *     AAPS_WRITE_IMPORT_FIXTURE=/tmp/import.json ./gradlew :implementation:testAndroidHostTest \
 *         --tests "*GenerateImportFixture*"
 *
 * There are two shapes, and they answer different questions:
 *
 * - [writeFixture] (`AAPS_WRITE_IMPORT_FIXTURE`) writes a file in TODAY's shape, with the names this
 *   build uses. It is the happy path: everything resolves.
 * - [write33Fixture] (`AAPS_WRITE_33_IMPORT_FIXTURE`) writes a file in the shape AAPS 3.3 wrote, with
 *   the legacy names. Nothing in the app can produce one of these any more, which is exactly why it
 *   is here - see that method for what it is for.
 *
 * In `androidHostTest` rather than `commonTest` because it writes a FILE, and `commonTest` compiles
 * for iOS too, where `java.io.File` and `System.getenv` do not exist. That cost a gate run to find:
 * `jvmTest` was perfectly happy with it.
 *
 * The password is deliberately NOT the device's master password. An import whose file does not match
 * the master password asks for a decryption password instead, which is the path this exercises -
 * and it means a device's real password is never needed to test an import on it.
 */
class GenerateImportFixture {

    private val values = mutableMapOf<String, String>()

    private val store = object : KeyValueStore {
        override fun getAll(): Map<String, *> = values
        override fun clear() = values.clear()
        override fun contains(key: String) = values.containsKey(key)
        override fun remove(key: String) { values.remove(key) }
        override fun getString(key: String, defaultValue: String) = values[key] ?: defaultValue
        override fun getStringOrNull(key: String, defaultValue: String?) = values[key] ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key]?.toBoolean() ?: defaultValue
        override fun getDouble(key: String, defaultValue: Double) = values[key]?.toDouble() ?: defaultValue
        override fun getInt(key: String, defaultValue: Int) = values[key]?.toInt() ?: defaultValue
        override fun getLong(key: String, defaultValue: Long) = values[key]?.toLong() ?: defaultValue
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value.toString() }
        override fun putDouble(key: String, value: Double) { values[key] = value.toString() }
        override fun putInt(key: String, value: Int) { values[key] = value.toString() }
        override fun putLong(key: String, value: Long) { values[key] = value.toString() }
        override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)
        override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)
        override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
            object : KeyValueStore.Editor {
                override fun clear() = values.clear()
                override fun remove(key: String) { values.remove(key) }
                override fun putBoolean(key: String, value: Boolean) { values[key] = value.toString() }
                override fun putDouble(key: String, value: Double) { values[key] = value.toString() }
                override fun putLong(key: String, value: Long) { values[key] = value.toString() }
                override fun putInt(key: String, value: Int) { values[key] = value.toString() }
                override fun putString(key: String, value: String) { values[key] = value }
            }.block()
        }
    }

    private val codec = PrefsFormatCodec(
        platformCryptoPrimitives(),
        object : TextResolver {
            override fun gs(ref: TextRef): String = "t"
            override fun gs(ref: TextRef, vararg args: Any?): String = "t"
            override fun gsNotLocalised(ref: TextRef): String = "t"
            override fun shortTextMode(): Boolean = false
        },
        object : SecureEncrypt {
            override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = plaintextSecret
            override fun decrypt(encryptedSecret: String): String = encryptedSecret
            override fun isValidDataString(data: String?): Boolean = false
            override fun deleteKey(keystoreAlias: String) = Unit
        }
    )

    private fun metadataFor(version: String): Map<PrefsMetadataKey, PrefMetadata> = mapOf(
        PrefsMetadataKeyImpl.AAPS_VERSION to PrefMetadata(version, PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.AAPS_FLAVOUR to PrefMetadata("full", PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.DEVICE_NAME to PrefMetadata("fixture", PrefsStatusImpl.OK)
    )

    @Test
    fun writeFixture() {
        val target = System.getenv("AAPS_WRITE_IMPORT_FIXTURE") ?: return

        // Values chosen to be visible in the app and cheap to check afterwards, one per type the
        // applier dispatches on, and all of them settings rather than device state.
        store.putString(StringKey.GeneralUnits.key, "mmol")
        store.putBoolean(BooleanKey.OverviewShowTreatmentButton.key, false)
        store.putInt(IntKey.OverviewCageWarning.key, 24)
        // A key owned by a pump driver, so the "keep pump settings" filter has something to act on.
        // Medtrum is used because it declares ownPreferences and needs no hardware to be CONSTRUCTED -
        // and construction is all that matters here: the resolver partitions every plugin in the
        // list, enabled or not, which is deliberate. A disabled pump's configuration is still that
        // pump's configuration.
        store.putString("sn_input", "111111111")

        write(target, "4.0.0-dev-c")
    }

    /**
     * Writes a file shaped the way AAPS 3.3 wrote one, so the loss an old backup suffers can be
     * measured instead of argued about.
     *
     * Why this has to be hand written: 3.3's exporter was
     * `for ((key, value) in sp.getAll()) entries[key] = value.toString()` - no filter at all - so its
     * files carry the LEGACY names, the ones 3.3 actually stored. Today's `PreferenceKeyResolver`
     * cannot resolve them (3.3 wrote `ConfigBuilder_PUMP_VirtualPumpPlugin_Enabled` and
     * `LocalProfile_0_isf`, while the keys are now `ConfigBuilder_Enabled_` and `LocalProfile_isf_`),
     * so `PreferenceImportApplier` counts them in `unresolved` and never writes them. Nothing in the
     * tree can produce such a file any more, and no real 3.3 backup can be used as a fixture because
     * it is encrypted with its own device's master password.
     *
     * The export filter (`isExportableKey`) is in 3.4.0.0 but NOT in 3.3.2.0 or 3.3.2.1, so this
     * shape is not ancient history - it is what any backup from the whole 3.3 line looks like.
     *
     * Two things in here are deliberate and should not be "tidied":
     *
     * - `LocalProfile_profiles` IS a live exportable key, while the per-profile content names are
     *   not. That mismatch is the point: the count arrives, the content does not, and
     *   `ProfileRepositoryImpl.loadFromLegacyKeysInternal` then builds profiles out of defaults.
     * - There are TWO profiles with different names. Both fall back to the same constant default
     *   name, so a correct run must not silently collapse them into one.
     *
     * A few names that DO resolve today are included as a control. If the import reports zero
     * changes, the file itself is wrong rather than the build.
     */
    @Test
    fun write33Fixture() {
        val target = System.getenv("AAPS_WRITE_33_IMPORT_FIXTURE") ?: return

        // Everything goes in as text, because that is what the 3.3 exporter produced: it called
        // toString() on whatever SharedPreferences held, so a Boolean became "true" and an Int "45".
        // Using putString here keeps the exported text exactly under our control.

        // --- Local profiles. The highest-stakes group. ---
        // 3.3 wrote `LocalProfile_<i>_<field>` (ProfilePlugin.storeSettings); the count key is the
        // only one of these names that this build still knows.
        store.putString("LocalProfile_profiles", "2")
        putProfile(index = 0, name = "Adult", ic = "10", isf = "3", basal = "0.8", low = "5", high = "7")
        putProfile(index = 1, name = "Night", ic = "12", isf = "4", basal = "0.6", low = "5.5", high = "7.5")

        // --- Plugin selection. 3.3: "ConfigBuilder_" + type + "_" + class + "_Enabled". ---
        store.putString("ConfigBuilder_PUMP_VirtualPumpPlugin_Enabled", "true")
        store.putString("ConfigBuilder_APS_OpenAPSSMBPlugin_Enabled", "true")
        store.putString("ConfigBuilder_BGSOURCE_RandomBgPlugin_Enabled", "true")
        // The visibility flag is no longer tracked. Kept because a real file is full of them and they
        // dominate the unresolved count - so the measurement should show how much of it is noise.
        store.putString("ConfigBuilder_PUMP_VirtualPumpPlugin_Visible", "true")

        // --- Objectives. 3.3: "Objectives_" + short name + "_started". ---
        store.putString("Objectives_config_started", "1700000000000")
        store.putString("Objectives_config_accomplished", "1700000600000")
        store.putString("Objectives_usage_started", "1700001000000")

        // --- Loop mode. Losing this leaves no RM row, and RM.DEFAULT_MODE is DISABLED_LOOP. ---
        store.putString("aps_mode", "CLOSED")

        // --- Temp target presets, with values well away from the defaults so a silent reset shows. ---
        store.putString("eatingsoon_target", "100.0")
        store.putString("eatingsoon_duration", "30")
        store.putString("activity_target", "150.0")
        store.putString("activity_duration", "120")
        store.putString("hypo_target", "170.0")
        store.putString("hypo_duration", "45")

        // --- Overview graph range. Superseded on dev, so it no longer resolves. ---
        store.putString("rangetodisplay", "12")

        // --- Low value, but they exercise the two remaining raw-key loops. ---
        store.putString("Monitor_Overview_total", "123456")
        store.putString("appwidget_use_black_0", "true")

        // --- Control group: names this build DOES resolve. ---
        store.putString(StringKey.GeneralUnits.key, "mmol")
        store.putString(BooleanKey.OverviewShowTreatmentButton.key, "false")
        store.putString(IntKey.OverviewCageWarning.key, "24")

        write(target, "3.3.2.1-objectives")
    }

    /** One profile in the 3.3 spelling. The schedules are single-block arrays, which is enough shape. */
    private fun putProfile(index: Int, name: String, ic: String, isf: String, basal: String, low: String, high: String) {
        val prefix = "LocalProfile_${index}_"
        store.putString(prefix + "name", name)
        store.putString(prefix + "mgdl", "false")
        store.putString(prefix + "dia", "6.0")
        store.putString(prefix + "ic", schedule(ic))
        store.putString(prefix + "isf", schedule(isf))
        store.putString(prefix + "basal", schedule(basal))
        store.putString(prefix + "targetlow", schedule(low))
        store.putString(prefix + "targethigh", schedule(high))
    }

    private fun schedule(value: String) = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":$value}]"

    /** `isExportable` is always true here, which is what makes this an unfiltered 3.3-style export. */
    private fun write(target: String, version: String) {
        val transfer = PrefsTransfer(codec, store) { true }
        val contents = transfer.exportContents(metadataFor(version), FIXTURE_PASSWORD)

        java.io.File(target).writeText(contents)
        println("Wrote import fixture to $target (${contents.length} chars), password '$FIXTURE_PASSWORD', version '$version'")
        println("Keys: ${values.keys.sorted()}")
    }

    private companion object {

        const val FIXTURE_PASSWORD = "fixture-password"
    }
}
