package info.nightscout.configuration.maintenance.formats

import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.interfaces.maintenance.PrefFormatError
import info.nightscout.interfaces.maintenance.PrefMetadata
import info.nightscout.interfaces.maintenance.Prefs
import info.nightscout.interfaces.maintenance.PrefsFormat
import info.nightscout.interfaces.maintenance.PrefsMetadataKey
import info.nightscout.interfaces.maintenance.PrefsStatus
import info.nightscout.shared.interfaces.ResourceHelper
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import java.io.File

@Suppress("SpellCheckingInspection")
class EncryptedPrefsFormatTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var file: MockedFile

    private var cryptoUtil: CryptoUtil = CryptoUtil(aapsLogger)

    // https://stackoverflow.com/questions/52344522/joseexception-couldnt-create-aes-gcm-nopadding-cipher-illegal-key-size
    // https://stackoverflow.com/questions/47708951/can-aes-256-work-on-android-devices-with-api-level-26
    // Java prior to Oracle Java 8u161 does not have policy for 256 bit AES - but Android support it
    // when test is run in Vanilla JVM without policy - Invalid key size exception is thrown
    private fun assumeAES256isSupported(cryptoUtil: CryptoUtil) {
        cryptoUtil.lastException?.message?.let { exceptionMessage ->
            Assume.assumeThat("Upgrade your testing environment Java (OpenJDK or Java 8u161) and JAVA_HOME - AES 256 is supported by Android so this exception should not happen!", exceptionMessage, CoreMatchers.not(CoreMatchers.containsString("key size")))
        }
    }

    @Before
    fun mock() {
        Mockito.`when`(rh.gs(ArgumentMatchers.anyInt())).thenReturn("mock translation")
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
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        assumeAES256isSupported(cryptoUtil)

        Assert.assertEquals(prefs.values.size, 2)
        Assert.assertEquals(prefs.values["key1"], "A")
        Assert.assertEquals(prefs.values["keyB"], "2")

        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, PrefsStatus.OK)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, PrefsFormat.FORMAT_KEY_ENC)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, PrefsStatus.OK)
    }

    @Test
    fun preferenceSavingTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = Prefs(
            mapOf(
                "key1" to "A",
                "keyB" to "2"
            ),
            mapOf(
                PrefsMetadataKey.ENCRYPTION to PrefMetadata(PrefsFormat.FORMAT_KEY_ENC, PrefsStatus.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefs, "sikret")
        aapsLogger.debug(storage.contents)
    }

    @Test
    fun importExportStabilityTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefsIn = Prefs(
            mapOf(
                "testpref1" to "--1--",
                "testpref2" to "another"
            ),
            mapOf(
                PrefsMetadataKey.ENCRYPTION to PrefMetadata(PrefsFormat.FORMAT_KEY_ENC, PrefsStatus.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefsIn, "tajemnica")
        val prefsOut = encryptedFormat.loadPreferences(getMockedFile(), "tajemnica")

        assumeAES256isSupported(cryptoUtil)

        Assert.assertEquals(prefsOut.values.size, 2)
        Assert.assertEquals(prefsOut.values["testpref1"], "--1--")
        Assert.assertEquals(prefsOut.values["testpref2"], "another")

        Assert.assertEquals(prefsOut.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, PrefsStatus.OK)
        Assert.assertEquals(prefsOut.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, PrefsFormat.FORMAT_KEY_ENC)
        Assert.assertEquals(prefsOut.metadata[PrefsMetadataKey.ENCRYPTION]?.status, PrefsStatus.OK)
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
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "it-is-NOT-right-secret")

        Assert.assertEquals(prefs.values.size, 0)

        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, PrefsStatus.OK)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, PrefsFormat.FORMAT_KEY_ENC)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, PrefsStatus.ERROR)
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
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        assumeAES256isSupported(cryptoUtil)

        // contents were not tampered and we can decrypt them
        Assert.assertEquals(prefs.values.size, 2)

        // but checksum fails on metadata, so overall security fails
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, PrefsStatus.ERROR)
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
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        Assert.assertEquals(prefs.values.size, 0)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, PrefsStatus.ERROR)
    }

    @Test
    fun missingFieldsTest() {
        val frozenPrefs = "{\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"lets get rid of metadata and security!\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        Assert.assertEquals(prefs.values.size, 0)
        Assert.assertEquals(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, PrefsStatus.ERROR)
    }

    @Test(expected = PrefFormatError::class)
    fun garbageInputTest() {
        val frozenPrefs = "whatever man, i duno care"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        encryptedFormat.loadPreferences(getMockedFile(), "sikret")
    }

    @Test(expected = PrefFormatError::class)
    fun unknownFormatTest() {
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
        val encryptedFormat = EncryptedPrefsFormat(rh, cryptoUtil, storage)
        encryptedFormat.loadPreferences(getMockedFile(), "sikret")
    }

    class MockedFile(s: String) : File(s)

    private fun getMockedFile(): File {
        Mockito.`when`(file.exists()).thenReturn(true)
        Mockito.`when`(file.canRead()).thenReturn(true)
        Mockito.`when`(file.canWrite()).thenReturn(true)
        return file
    }
}
