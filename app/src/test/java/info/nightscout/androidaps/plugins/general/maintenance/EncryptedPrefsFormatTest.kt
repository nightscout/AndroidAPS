package info.nightscout.androidaps.plugins.general.maintenance

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.maintenance.formats.*
import info.nightscout.androidaps.testing.utils.SingleStringStorage
import info.nightscout.androidaps.utils.CryptoUtil
import info.nightscout.androidaps.utils.assumeAES256isSupported
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.File

@PowerMockIgnore("javax.crypto.*")
@RunWith(PowerMockRunner::class)
@PrepareForTest(File::class)

class EncryptedPrefsFormatTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var file: MockedFile

    private var cryptoUtil: CryptoUtil = CryptoUtil(aapsLogger)

    @Before
    fun mock() {
        Mockito.`when`(resourceHelper.gs(ArgumentMatchers.anyInt())).thenReturn("mock translation")
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
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        assumeAES256isSupported(cryptoUtil)

        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(2))
        Assert.assertThat(prefs.values["key1"], CoreMatchers.`is`("A"))
        Assert.assertThat(prefs.values["keyB"], CoreMatchers.`is`("2"))

        Assert.assertThat(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, CoreMatchers.`is`(PrefsStatus.OK))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, CoreMatchers.`is`(EncryptedPrefsFormat.FORMAT_KEY_ENC))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, CoreMatchers.`is`(PrefsStatus.OK))
    }

    @Test
    fun preferenceSavingTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = Prefs(
            mapOf(
                "key1" to "A",
                "keyB" to "2"
            ),
            mapOf(
                PrefsMetadataKey.ENCRYPTION to PrefMetadata(EncryptedPrefsFormat.FORMAT_KEY_ENC, PrefsStatus.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefs, "sikret")
        aapsLogger.debug(storage.contents)
    }

    @Test
    fun importExportStabilityTest() {
        val storage = SingleStringStorage("")
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefsIn = Prefs(
            mapOf(
                "testpref1" to "--1--",
                "testpref2" to "another"
            ),
            mapOf(
                PrefsMetadataKey.ENCRYPTION to PrefMetadata(EncryptedPrefsFormat.FORMAT_KEY_ENC, PrefsStatus.OK)
            )
        )
        encryptedFormat.savePreferences(getMockedFile(), prefsIn, "tajemnica")
        val prefsOut = encryptedFormat.loadPreferences(getMockedFile(), "tajemnica")

        assumeAES256isSupported(cryptoUtil)

        Assert.assertThat(prefsOut.values.size, CoreMatchers.`is`(2))
        Assert.assertThat(prefsOut.values["testpref1"], CoreMatchers.`is`("--1--"))
        Assert.assertThat(prefsOut.values["testpref2"], CoreMatchers.`is`("another"))

        Assert.assertThat(prefsOut.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, CoreMatchers.`is`(PrefsStatus.OK))
        Assert.assertThat(prefsOut.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, CoreMatchers.`is`(EncryptedPrefsFormat.FORMAT_KEY_ENC))
        Assert.assertThat(prefsOut.metadata[PrefsMetadataKey.ENCRYPTION]?.status, CoreMatchers.`is`(PrefsStatus.OK))
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
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "it-is-NOT-right-secret")

        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(0))

        Assert.assertThat(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, CoreMatchers.`is`(PrefsStatus.OK))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.value, CoreMatchers.`is`(EncryptedPrefsFormat.FORMAT_KEY_ENC))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, CoreMatchers.`is`(PrefsStatus.ERROR))
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
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        assumeAES256isSupported(cryptoUtil)

        // contents were not tampered and we can decrypt them
        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(2))

        // but checksum fails on metadata, so overall security fails
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, CoreMatchers.`is`(PrefsStatus.ERROR))
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
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(0))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status, CoreMatchers.`is`(PrefsStatus.ERROR))
    }

    @Test
    fun missingFieldsTest() {
        val frozenPrefs = "{\n" +
            "  \"format\": \"aaps_encrypted\",\n" +
            "  \"content\": \"lets get rid of metadata and security!\"\n" +
            "}"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
        val prefs = encryptedFormat.loadPreferences(getMockedFile(), "sikret")

        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(0))
        Assert.assertThat(prefs.metadata[PrefsMetadataKey.FILE_FORMAT]?.status, CoreMatchers.`is`(PrefsStatus.ERROR))
    }

    @Test(expected = PrefFormatError::class)
    fun garbageInputTest() {
        val frozenPrefs = "whatever man, i duno care"

        val storage = SingleStringStorage(frozenPrefs)
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
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
        val encryptedFormat = EncryptedPrefsFormat(resourceHelper, cryptoUtil, storage)
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
