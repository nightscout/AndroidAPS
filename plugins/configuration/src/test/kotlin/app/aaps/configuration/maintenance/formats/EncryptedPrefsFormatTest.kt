package app.aaps.configuration.maintenance.formats

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.implementation.protection.SecureEncryptImpl
import app.aaps.plugins.configuration.maintenance.PrefsMetadataKeyImpl
import app.aaps.plugins.configuration.maintenance.data.PrefFormatError
import app.aaps.plugins.configuration.maintenance.data.Prefs
import app.aaps.plugins.configuration.maintenance.data.PrefsFormat
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import app.aaps.plugins.configuration.maintenance.formats.EncryptedPrefsFormat
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

// https://stackoverflow.com/questions/52344522/joseexception-couldnt-create-aes-gcm-nopadding-cipher-illegal-key-size
// https://stackoverflow.com/questions/47708951/can-aes-256-work-on-android-devices-with-api-level-26
// Java prior to Oracle Java 8u161 does not have policy for 256 bit AES - but Android support it
// when test is run in Vanilla JVM without policy - Invalid key size exception is thrown
private fun assumeAES256isSupported(cryptoUtil: CryptoUtil) {
    cryptoUtil.lastException?.message?.let { exceptionMessage ->
        assume().withMessage("Upgrade your testing environment Java (OpenJDK or Java 8u161) and JAVA_HOME - AES 256 is supported by Android so this exception should not happen!")
            .that(exceptionMessage).doesNotContain("key size")
    }
}

@Suppress("SpellCheckingInspection")
open class EncryptedPrefsFormatTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var contentResolver: ContentResolver

    private var cryptoUtil: CryptoUtil = CryptoUtil(aapsLogger)

    @BeforeEach
    fun mock() {
        whenever(rh.gs(ArgumentMatchers.anyInt())).thenReturn("mock translation")
        whenever(context.contentResolver).thenReturn(contentResolver)
    }

    @Test
    fun preferenceLoadingTest() {
        val frozenPrefs = "{\n" +
            "  \"metadata\": {},\n" +
            "  \"security\": {\n" +
            "    \"salt\": \"9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9\",\n" +
            "    \"file_hash\": \"9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb\",\n" +
            "    \"content_hash\": \"23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760c\",\n" +
            "    \"algorithm\": \"v1\"\n" +
            "  },\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg==\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        val prefs = encryptedFormat.loadPreferences(frozenPrefs, "sikret")

        assumeAES256isSupported(cryptoUtil)

        assertThat(prefs.values).containsExactlyEntriesIn(
            mapOf(
                "key1" to "A",
                "keyB" to "2",
            )
        )

        assertThat(prefs.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.status).isEqualTo(PrefsStatusImpl.OK)
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.value).isEqualTo(PrefsFormat.FORMAT_KEY_ENC)
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]!!.status).isEqualTo(PrefsStatusImpl.OK)
    }

    @Test
    fun preferenceSavingTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        encryptedFormat.secureEncrypt = SecureEncryptImpl(aapsLogger, cryptoUtil)
        val prefs = Prefs(
            mapOf(
                "key1" to "A",
                "keyB" to "2"
            ),
            mapOf(
                PrefsMetadataKeyImpl.ENCRYPTION to PrefMetadata(PrefsFormat.FORMAT_KEY_ENC, PrefsStatusImpl.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefs, "sikret")
        aapsLogger.debug(storage.contents)
    }

    @Test
    fun importExportStabilityTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        encryptedFormat.secureEncrypt = SecureEncryptImpl(aapsLogger, cryptoUtil)
        val prefsIn = Prefs(
            mapOf(
                "testpref1" to "--1--",
                "testpref2" to "another"
            ),
            mapOf(
                PrefsMetadataKeyImpl.ENCRYPTION to PrefMetadata(PrefsFormat.FORMAT_KEY_ENC, PrefsStatusImpl.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefsIn, "tajemnica")
        val prefsOut = encryptedFormat.loadPreferences(storage.contents, "tajemnica")

        assumeAES256isSupported(cryptoUtil)

        assertThat(prefsOut.values).containsExactlyEntriesIn(
            mapOf(
                "testpref1" to "--1--",
                "testpref2" to "another",
            )
        )
        assertThat(prefsOut.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.status).isEqualTo(PrefsStatusImpl.OK)
        assertThat(prefsOut.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.value).isEqualTo(PrefsFormat.FORMAT_KEY_ENC)
        assertThat(prefsOut.metadata[PrefsMetadataKeyImpl.ENCRYPTION]!!.status).isEqualTo(PrefsStatusImpl.OK)
    }

    @Test
    fun wrongPasswordTest() {
        val frozenPrefs = "{\n" +
            "  \"metadata\": {},\n" +
            "  \"security\": {\n" +
            "    \"salt\": \"9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9\",\n" +
            "    \"file_hash\": \"9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb\",\n" +
            "    \"content_hash\": \"23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760c\",\n" +
            "    \"algorithm\": \"v1\"\n" +
            "  },\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg==\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        val prefs = encryptedFormat.loadPreferences(frozenPrefs, "it-is-NOT-right-secret")

        assertThat(prefs.values).isEmpty()

        assertThat(prefs.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.status).isEqualTo(PrefsStatusImpl.OK)
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.value).isEqualTo(PrefsFormat.FORMAT_KEY_ENC)
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]!!.status).isEqualTo(PrefsStatusImpl.ERROR)
    }

    @Test
    fun tamperedMetadataTest() {
        val frozenPrefs = "{\n" +
            "  \"metadata\": {" +
            "      \"created-by\":\"I am legit, trust me, no-one lies on internets!\"" +
            "  },\n" +
            "  \"security\": {\n" +
            "    \"salt\": \"9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9\",\n" +
            "    \"file_hash\": \"9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb\",\n" +
            "    \"content_hash\": \"23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760c\",\n" +
            "    \"algorithm\": \"v1\"\n" +
            "  },\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg==\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        val prefs = encryptedFormat.loadPreferences(frozenPrefs, "sikret")

        assumeAES256isSupported(cryptoUtil)

        // contents were not tampered and we can decrypt them
        assertThat(prefs.values).hasSize(2)

        // but checksum fails on metadata, so overall security fails
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]!!.status).isEqualTo(PrefsStatusImpl.ERROR)
    }

    @Test
    fun tamperedContentsTest() {
        val frozenPrefs = "{\n" +
            "  \"metadata\": {},\n" +
            "  \"security\": {\n" +
            "    \"salt\": \"9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9\",\n" +
            "    \"file_hash\": \"9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb\",\n" +
            "    \"content_hash\": \"23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760a\",\n" +
            "    \"algorithm\": \"v1\"\n" +
            "  },\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg==\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        val prefs = encryptedFormat.loadPreferences(frozenPrefs, "sikret")

        assertThat(prefs.values).isEmpty()
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]!!.status).isEqualTo(PrefsStatusImpl.ERROR)
    }

    @Test
    fun missingFieldsTest() {
        val frozenPrefs = "{\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"lets get rid of metadata and security!\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
        val prefs = encryptedFormat.loadPreferences(frozenPrefs, "sikret")

        assertThat(prefs.values).isEmpty()
        assertThat(prefs.metadata[PrefsMetadataKeyImpl.FILE_FORMAT]!!.status).isEqualTo(PrefsStatusImpl.ERROR)
    }

    @Test
    fun garbageInputTest() {
        assertFailsWith<PrefFormatError> {
            val frozenPrefs = "whatever man, i duno care"

            val storage = SingleStringStorage(frozenPrefs)
            val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
            encryptedFormat.loadPreferences(frozenPrefs, "sikret")
        }
    }

    @Test
    fun unknownFormatTest() {
        assertFailsWith<PrefFormatError> {
            val frozenPrefs = "{\n" +
                "  \"metadata\": {},\n" +
                "  \"security\": {\n" +
                "    \"salt\": \"9581d7a9e56d8127ad6b74a876fa60b192b1c6f4343d857bc07e3874589f2fc9\",\n" +
                "    \"file_hash\": \"9122fd04a4938030b62f6b9d6dda63a11c265e673c4aecbcb6dcd62327c025bb\",\n" +
                "    \"content_hash\": \"23f999f6e6d325f649b61871fe046a94e110bf1587ff070fb66a0f8085b2760c\",\n" +
                "    \"algorithm\": \"v1\"\n" +
                "  },\n" +
                "  \"format\": \"aaps_9000_new_format\",\n" +
                "  \"content\": \"DJ5+HP/gq7icRQhbG9PEBJCMuNwBssIytfEQPCNkzn7PHMfMZuc09vYQg3qzFkmULLiotg==\"\n" +
                "}"

            val storage = SingleStringStorage(frozenPrefs)
            val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage, context)
            encryptedFormat.loadPreferences(frozenPrefs, "sikret")
        }
    }

    @Mock lateinit var file: DocumentFile

    private fun getMockedFile(): DocumentFile {
        whenever(file.exists()).thenReturn(true)
        whenever(file.canRead()).thenReturn(true)
        whenever(file.canWrite()).thenReturn(true)
        return file
    }
}
