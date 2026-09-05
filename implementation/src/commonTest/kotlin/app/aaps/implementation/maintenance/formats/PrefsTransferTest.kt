package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Settings out of the store into a file, and back in again.
 *
 * The store is a map here, so these run on every platform without a device - and they do run on
 * every platform, which is the point: an export written on one has to be an import on another, and
 * the two steps either side of the file are as easy to get subtly wrong as the file itself.
 */
class PrefsTransferTest {

    private val store = FakeKeyValueStore()

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

    /** Everything travels except the one key named as device state. */
    private val sut = PrefsTransfer(codec, store) { it != "device_only" }

    private val metadata: Map<PrefsMetadataKey, PrefMetadata> = mapOf(
        PrefsMetadataKeyImpl.AAPS_VERSION to PrefMetadata("3.4.0", PrefsStatusImpl.OK),
        PrefsMetadataKeyImpl.AAPS_FLAVOUR to PrefMetadata("full", PrefsStatusImpl.OK)
    )

    @Test
    fun `settings survive an export and an import`() {
        store.putString("units", "mmol")
        store.putString("age", "adult")

        val file = sut.exportContents(metadata, "password")
        store.clear()
        val result = assertIs<ImportDecryptResult.Success>(sut.importResult(file, "password", engineeringMode = false))
        sut.applyImported(result.prefs)

        assertEquals("mmol", store.getString("units", ""))
        assertEquals("adult", store.getString("age", ""))
    }

    /** Device state must not travel, or a restored phone claims another phone's pump. */
    @Test
    fun `a key that is not exportable never reaches the file`() {
        store.putString("units", "mmol")
        store.putString("device_only", "this phone")

        val file = sut.exportContents(metadata, "password")

        val result = assertIs<ImportDecryptResult.Success>(sut.importResult(file, "password", engineeringMode = false))
        assertTrue(result.prefs.values.containsKey("units"))
        assertFalse(result.prefs.values.containsKey("device_only"))
    }

    /**
     * Booleans have to come back as booleans. They are text inside the file, and a store that kept
     * them as text would answer the wrong type to every later read - which is the sort of thing that
     * silently turns a feature off after an import.
     */
    @Test
    fun `a boolean is restored as a boolean and not as text`() {
        store.putBoolean("use_smb", true)

        val file = sut.exportContents(metadata, "password")
        store.clear()
        sut.applyImported(assertIs<ImportDecryptResult.Success>(sut.importResult(file, "password", false)).prefs)

        assertEquals(true, store.getBoolean("use_smb", false))
        assertTrue(store.getAll()["use_smb"] is Boolean, "stored as ${store.getAll()["use_smb"]}")
    }

    /**
     * An import replaces, it does not merge. A setting the old configuration had and the imported
     * one does not must be gone, or the result is neither configuration.
     */
    @Test
    fun `an import removes what the file does not have`() {
        store.putString("units", "mmol")
        val file = sut.exportContents(metadata, "password")
        store.putString("left_over", "from before")

        sut.applyImported(assertIs<ImportDecryptResult.Success>(sut.importResult(file, "password", false)).prefs)

        assertFalse(store.getAll().containsKey("left_over"))
    }

    @Test
    fun `a wrong password is reported as a wrong password`() {
        store.putString("units", "mmol")

        val result = sut.importResult(sut.exportContents(metadata, "password"), "wrong", false)

        assertIs<ImportDecryptResult.WrongPassword>(result)
    }

    @Test
    fun `something that is not an export is an error and not a crash`() {
        val result = sut.importResult("not an export at all", "password", false)

        assertIs<ImportDecryptResult.Error>(result)
    }

    @Test
    fun `the metadata the import list shows is carried in the file`() {
        store.putString("units", "mmol")

        val result = assertIs<ImportDecryptResult.Success>(sut.importResult(sut.exportContents(metadata, "password"), "password", false))

        assertEquals("3.4.0", result.prefs.metadata[PrefsMetadataKeyImpl.AAPS_VERSION]?.value)
        assertEquals("full", result.prefs.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.value)
    }

    /** An empty file has nothing to import, so it must not be offered as though it had. */
    @Test
    fun `an export of nothing is not importable`() {
        val result = assertIs<ImportDecryptResult.Success>(sut.importResult(sut.exportContents(metadata, "password"), "password", false))

        assertFalse(result.importPossible)
    }
}

/** A preference store held in memory, so these tests need no device and leave nothing behind. */
class FakeKeyValueStore : KeyValueStore {

    private val values = mutableMapOf<String, Any>()

    override fun getAll(): Map<String, *> = values
    override fun clear() = values.clear()
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun remove(key: String) { values.remove(key) }

    override fun getString(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
    override fun getDouble(key: String, defaultValue: Double): Double = values[key] as? Double ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

    override fun putString(key: String, value: String) { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }

    override fun incInt(key: String) { values[key] = (values[key] as? Int ?: 0) + 1 }
    override fun incLong(key: String) { values[key] = (values[key] as? Long ?: 0L) + 1L }

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        object : KeyValueStore.Editor {
            override fun clear() = values.clear()
            override fun remove(key: String) { values.remove(key) }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putDouble(key: String, value: Double) { values[key] = value }
            override fun putLong(key: String, value: Long) { values[key] = value }
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putString(key: String, value: String) { values[key] = value }
        }.block()
    }
}
