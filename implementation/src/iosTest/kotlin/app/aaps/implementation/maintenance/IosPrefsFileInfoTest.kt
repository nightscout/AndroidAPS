package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import app.aaps.implementation.maintenance.formats.PrefsFormatCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSTemporaryDirectory

/**
 * The two answers the maintenance and import screens read.
 *
 * The relative wording is not asserted on: it comes from `NSRelativeDateTimeFormatter` and is
 * different in every language, so a test on the words would only describe this machine. What is
 * checked instead is which of the two forms comes back, and that the directory answer is a real
 * look at the file system rather than a fixed true.
 */
class IosPrefsFileInfoTest {

    private class RecordingLogger : AAPSLogger {

        val debugs = mutableListOf<String>()

        override fun debug(tag: LTag, message: String) {
            debugs.add(message)
        }

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private val logger = RecordingLogger()
    private val temporaryDirectory = NSTemporaryDirectory()

    private val text = object : TextResolver {
        override fun gs(ref: TextRef): String = "t"
        override fun gs(ref: TextRef, vararg args: Any?): String = "t"
        override fun gsNotLocalised(ref: TextRef): String = "t"
        override fun shortTextMode(): Boolean = false
    }

    private val plainKeychain = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = plaintextSecret
        override fun decrypt(encryptedSecret: String): String = encryptedSecret
        override fun isValidDataString(data: String?): Boolean = false
        override fun deleteKey(keystoreAlias: String) = Unit
    }

    private val files = FakePrefsFileAccess()
    private val lister = PrefsFileLister(files, plainKeychain, text)
    private val codec = PrefsFormatCodec(platformCryptoPrimitives(), text, plainKeychain)

    private fun sut(directory: String? = temporaryDirectory) = IosPrefsFileInfo(logger, lister, directory)

    @Test
    fun `an old export is named by its date`() {
        assertEquals("2001-01-01", sut().formatExportedAgo("2001-01-01T12:00:00.000Z"))
    }

    @Test
    fun `a broken timestamp is shown as it stands instead of throwing`() {
        assertEquals("nonsense", sut().formatExportedAgo("nonsense"))
        assertEquals(1, logger.debugs.size)
    }

    /**
     * Only that something comes back, and that it is not the date form. The words themselves are
     * the system's and change with the device language.
     */
    @Test
    fun `a recent export is described in words rather than by its date`() {
        val today = kotlin.time.Clock.System.now().toString()

        val text = sut().formatExportedAgo(today)

        assertTrue(text.isNotEmpty())
        assertFalse(text == ExportedAgo.datePart(today))
    }

    @Test
    fun `a directory that exists and can be written counts as granted`() {
        assertTrue(sut().isDirectoryAccessGranted())
    }

    @Test
    fun `a directory that is not there does not`() {
        assertFalse(sut("/no/such/place/for/aaps").isDirectoryAccessGranted())
    }

    @Test
    fun `no directory at all is a no - with a line saying so`() {
        assertFalse(sut(directory = null).isDirectoryAccessGranted())
        assertEquals(1, logger.debugs.size)
    }

    /**
     * The setup wizard asks this before it offers to import, so an empty answer means a new user is
     * never shown their own backup. It used to be empty always.
     */
    @Test
    fun `a real export is listed`() {
        files.write("2026-09-05_010838_full.json", codec.encode(Prefs(mapOf("units" to "mmol"), emptyMap()), "password"))

        assertEquals(1, sut().listPreferenceFiles().size)
    }

    /** Other json in the same directory belongs to somebody else and must not be offered. */
    @Test
    fun `json that is not an export is not listed`() {
        files.write("something-else.json", """{"not":"ours"}""")

        assertTrue(sut().listPreferenceFiles().isEmpty())
    }

    @Test
    fun `an export is listed with the metadata the row shows`() {
        val prefs = Prefs(mapOf("units" to "mmol"), mapOf(PrefsMetadataKeyImpl.AAPS_FLAVOUR to PrefMetadata("full", PrefsStatusImpl.OK)))
        files.write("2026-09-05_010838_full.json", codec.encode(prefs, "password"))

        val listed = sut().listPreferenceFiles().single()

        assertEquals("full", listed.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.value)
    }

    /** Writes to a map, so the test never depends on what is lying in the simulator's directory. */
    private class FakePrefsFileAccess : PrefsFileAccess {

        private val written = mutableMapOf<String, String>()

        override fun newExportName(flavour: String): String = "2026-09-05_010838_$flavour.json"
        override fun write(name: String, contents: String) { written[name] = contents }
        override fun list(): List<Pair<String, String>> = written.entries.map { it.key to it.value }
    }
}
