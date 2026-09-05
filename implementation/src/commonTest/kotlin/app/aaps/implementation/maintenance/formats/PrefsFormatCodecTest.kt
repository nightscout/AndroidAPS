package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The export format, read and written the same way on every platform.
 *
 * The file at the centre of this - [FROZEN_ENCRYPTED] - is not made up. It is the same export the
 * Android test has pinned since long before any of this was multiplatform, password and all. Reading
 * it here is the only assertion that proves the shared codec speaks the format AAPS already writes,
 * rather than a new one that merely looks similar. Everything else could pass while every real
 * export stayed unreadable.
 *
 * These run on Android, on the desktop and on iOS from this one file, so the platforms cannot drift.
 */
class PrefsFormatCodecTest {

    /** Enough of a resolver to read: these strings are shown to a user, never compared against. */
    private val textResolver = object : TextResolver {
        override fun gs(ref: TextRef): String = "translation"
        override fun gs(ref: TextRef, vararg args: Any?): String = "translation"
        override fun gsNotLocalised(ref: TextRef): String = "translation"
        override fun shortTextMode(): Boolean = false
    }

    /**
     * A stand-in for the keystore. A password is "wrapped" by a prefix here, which is enough to show
     * the codec unwraps one before encrypting - the real wrapping is the platform's and is tested
     * where it lives.
     */
    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "$WRAPPED$plaintextSecret"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix(WRAPPED)
        override fun isValidDataString(data: String?): Boolean = data?.startsWith(WRAPPED) == true
        override fun deleteKey(keystoreAlias: String) = Unit
    }

    private val sut = PrefsFormatCodec(platformCryptoPrimitives(), textResolver, secureEncrypt)

    @Test
    fun `the file Android froze years ago still opens`() {
        val prefs = sut.decode(FROZEN_ENCRYPTED, "sikret")

        assertEquals(mapOf("key1" to "A", "keyB" to "2"), prefs.values)
    }

    @Test
    fun `the frozen file reports itself as secure`() {
        val prefs = sut.decode(FROZEN_ENCRYPTED, "sikret")

        assertEquals(PrefsStatusImpl.OK, prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
    }

    /** A typo is an ordinary event, so it is an answer with a reason on it and never an exception. */
    @Test
    fun `a wrong password is reported and does not throw`() {
        val prefs = sut.decode(FROZEN_ENCRYPTED, "not the password")

        assertEquals(PrefsStatusImpl.ERROR, prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
        assertTrue(prefs.values.isEmpty())
    }

    /**
     * The file hash is what notices an edited file. The edit here is a single space added outside
     * the encrypted blob, so the contents still decrypt perfectly and only the hash disagrees -
     * which is the case the hash exists for and the one a content check alone would miss.
     */
    @Test
    fun `an edited file is reported as modified`() {
        val tampered = FROZEN_ENCRYPTED.replace("\"metadata\": {}", "\"metadata\": { }")

        val prefs = sut.decode(tampered, "sikret")

        assertEquals(PrefsStatusImpl.ERROR, prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
    }

    @Test
    fun `metadata can be read without the password`() {
        val metadata = sut.decodeMetadata(FROZEN_ENCRYPTED)

        assertEquals(PrefsStatusImpl.OK, metadata[PrefsMetadataKeyImpl.FILE_FORMAT]?.status)
    }

    @Test
    fun `a file this reader wrote can be read back`() {
        val prefs = Prefs(mapOf("units" to "mmol", "age" to "adult"), emptyMap())

        val written = sut.encode(prefs, "passphrase")
        val read = sut.decode(written, "passphrase")

        assertEquals(prefs.values, read.values)
        assertEquals(PrefsStatusImpl.OK, read.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
    }

    /**
     * A written file has to pass its own hash check. This is what catches a writer that lays the
     * JSON out one way and blanks the hash field with a pattern that no longer matches it.
     */
    @Test
    fun `a written file passes its own integrity check`() {
        val written = sut.encode(Prefs(mapOf("a" to "1"), emptyMap()), "passphrase")

        val read = sut.decode(written, "passphrase")

        assertEquals(null, read.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.info)
    }

    @Test
    fun `an edited file that this reader wrote is caught too`() {
        val written = sut.encode(Prefs(mapOf("a" to "1"), emptyMap()), "passphrase")

        val read = sut.decode(written.replace("\"algorithm\": \"v1\"", "\"algorithm\":  \"v1\""), "passphrase")

        assertEquals(PrefsStatusImpl.ERROR, read.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
    }

    /**
     * Written without a password, a file says `algorithm: none` and is then refused by its own
     * reader.
     *
     * That is not a bug introduced here - it is what AAPS does today, and this pins it rather than
     * quietly changing it. `savePreferences` always stamps `format: aaps_encrypted` whether or not
     * anything was encrypted, and `loadPreferences` reads that stamp as "this file is encrypted",
     * so it then finds `none` where it wants `v1` and reports the wrong algorithm. In practice the
     * export path always has a master password, which is why nobody meets this. If the unencrypted
     * export is ever made real, the format stamp is what has to change - and this test will say so.
     */
    @Test
    fun `an unencrypted file is written but its own reader refuses it`() {
        val written = sut.encode(Prefs(mapOf("a" to "1"), emptyMap()), null)

        assertTrue(written.contains("\"algorithm\": \"none\""))
        val read = sut.decode(written, null)
        assertEquals(PrefsStatusImpl.ERROR, read.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
        assertTrue(read.values.isEmpty())
    }

    @Test
    fun `metadata written into a file comes back out of it`() {
        val prefs = Prefs(
            mapOf("a" to "1"),
            mapOf(PrefsMetadataKeyImpl.AAPS_VERSION to PrefMetadata("3.4.0", PrefsStatusImpl.OK))
        )

        val read = sut.decode(sut.encode(prefs, "passphrase"), "passphrase")

        assertEquals("3.4.0", read.metadata[PrefsMetadataKeyImpl.AAPS_VERSION]?.value)
    }

    @Test
    fun `our own files are recognised and other json is not`() {
        assertTrue(sut.looksLikePreferences(FROZEN_ENCRYPTED))
        assertTrue(sut.looksLikePreferences(sut.encode(Prefs(mapOf("a" to "1"), emptyMap()), "p")))
        assertTrue(!sut.looksLikePreferences("""{"something":"else"}"""))
        assertTrue(!sut.looksLikePreferences("not json at all"))
    }

    @Test
    fun `a file with no encryption metadata still parses`() {
        val metadata = sut.decodeMetadata("""{"nonsense":true}""")

        assertNotNull(metadata[PrefsMetadataKeyImpl.FILE_FORMAT])
        assertEquals(PrefsStatusImpl.ERROR, metadata[PrefsMetadataKeyImpl.FILE_FORMAT]?.status)
    }


    /**
     * A master password kept for the user is stored wrapped by the keychain, so what reaches the
     * writer can be the wrapper rather than the password. The file still has to open with the
     * password the user actually knows.
     */
    @Test
    fun `a wrapped master password is unwrapped before encrypting`() {
        val written = sut.encode(Prefs(mapOf("a" to "1"), emptyMap()), WRAPPED + "realpassword")

        val read = sut.decode(written, "realpassword")

        assertEquals(mapOf("a" to "1"), read.values)
        assertEquals(PrefsStatusImpl.OK, read.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status)
    }

    private companion object {

        private const val WRAPPED = "wrapped:"

        /**
         * A real AAPS export, copied from `EncryptedPrefsFormatTest`, which has guarded the Android
         * reader for years. Password `sikret`; it holds `key1=A` and `keyB=2`.
         */
        private val FROZEN_ENCRYPTED = """
            {
              "metadata": {},
              "security": {
                "salt": "9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9",
                "file_hash": "9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb",
                "content_hash": "23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760c",
                "algorithm": "v1"
              },
              "format": "aaps_encrypted",
              "content": "DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg=="
            }
        """.trimIndent()
    }
}
