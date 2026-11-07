package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What an export says about itself.
 *
 * The import screen reads these rows, and gates the import on the flavour and the version, so a key
 * that is missing or stamped under the wrong name is a backup the app will not restore. These pin
 * the six keys and then follow them through a real file and back, because being built correctly and
 * surviving the round trip are two different claims.
 */
class ExportMetadataTest {

    private val metadata = ExportMetadata.forExport(
        deviceName = "iPhone 17 Pro",
        createdAt = "2026-09-05T01:08:38.048Z",
        version = "4.0.0-dev-b-kmp",
        flavour = "aapsclient",
        deviceModel = "iPhone iOS 26.5"
    )

    @Test
    fun `every key an export is stamped with is there`() {
        assertEquals(
            setOf(
                PrefsMetadataKeyImpl.DEVICE_NAME,
                PrefsMetadataKeyImpl.CREATED_AT,
                PrefsMetadataKeyImpl.AAPS_VERSION,
                PrefsMetadataKeyImpl.AAPS_FLAVOUR,
                PrefsMetadataKeyImpl.DEVICE_MODEL,
                PrefsMetadataKeyImpl.ENCRYPTION
            ),
            metadata.keys
        )
    }

    @Test
    fun `the values land under the keys they belong to`() {
        assertEquals("iPhone 17 Pro", metadata[PrefsMetadataKeyImpl.DEVICE_NAME]?.value)
        assertEquals("2026-09-05T01:08:38.048Z", metadata[PrefsMetadataKeyImpl.CREATED_AT]?.value)
        assertEquals("4.0.0-dev-b-kmp", metadata[PrefsMetadataKeyImpl.AAPS_VERSION]?.value)
        assertEquals("aapsclient", metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.value)
        assertEquals("iPhone iOS 26.5", metadata[PrefsMetadataKeyImpl.DEVICE_MODEL]?.value)
    }

    /** A row shown in red would tell the user their own fresh backup is suspect. */
    @Test
    fun `a new export claims nothing is wrong with it`() {
        metadata.forEach { (key, entry) -> assertEquals(PrefsStatusImpl.OK, entry.status, "$key") }
    }

    /**
     * The two the import gate reads have to come back out of a real file. Everything above tests the
     * map; this tests that the map survives being written and read.
     */
    @Test
    fun `the version and flavour survive a real export`() {
        val codec = PrefsFormatCodec(
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

        val read = codec.decodeMetadata(
            codec.encode(Prefs(mapOf("a" to "1"), metadata), "password")
        )

        assertEquals("4.0.0-dev-b-kmp", read[PrefsMetadataKeyImpl.AAPS_VERSION]?.value)
        assertEquals("aapsclient", read[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.value)
        assertEquals("iPhone 17 Pro", read[PrefsMetadataKeyImpl.DEVICE_NAME]?.value)
    }
}
