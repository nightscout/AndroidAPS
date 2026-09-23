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
 * Writes a REAL, importable settings file so the import can be exercised on a device.
 *
 * Not a test of anything - it is a fixture generator, kept here because this is the only place that
 * can build a file the app will actually accept: the same codec, the same envelope, the same
 * metadata. It no-ops unless `AAPS_WRITE_IMPORT_FIXTURE` names a path:
 *
 *     AAPS_WRITE_IMPORT_FIXTURE=/tmp/import.json ./gradlew :implementation:testAndroidHostTest \
 *         --tests "*GenerateImportFixture*"
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

    private val metadata: Map<PrefsMetadataKey, PrefMetadata> = mapOf(
        PrefsMetadataKeyImpl.AAPS_VERSION to PrefMetadata("4.0.0-dev-c", PrefsStatusImpl.OK),
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

        val transfer = PrefsTransfer(codec, store) { true }
        val contents = transfer.exportContents(metadata, FIXTURE_PASSWORD)

        java.io.File(target).writeText(contents)
        println("Wrote import fixture to $target (${contents.length} chars), password '$FIXTURE_PASSWORD'")
        println("Keys: ${values.keys.sorted()}")
    }

    private companion object {

        const val FIXTURE_PASSWORD = "fixture-password"
    }
}
