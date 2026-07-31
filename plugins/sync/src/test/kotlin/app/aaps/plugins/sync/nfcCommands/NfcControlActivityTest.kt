package app.aaps.plugins.sync.nfcCommands

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets
import com.google.common.truth.Truth.assertThat

class NfcControlActivityTest : TestBaseWithProfile() {
    private lateinit var pluginUnderTest: NfcCommandsPlugin
    private lateinit var nfcTagStore: NfcTagStore

    @BeforeEach
    fun setup() {
        nfcTagStore = NfcTagStore(TestSp())
        nfcTagStore.clearJustWrittenForTest()
        
        pluginUnderTest = NfcCommandsPlugin(
            context = context,
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            nfcTagStore = nfcTagStore,
            constraintChecker = constraintsChecker,
            profileFunction = profileFunction,
            profileUtil = profileUtil,
            profileRepository = profileRepository,
            activePlugin = activePlugin,
            commandQueue = mock(),
            loop = mock(),
            dateUtil = dateUtil,
            persistenceLayer = mock(),
            decimalFormatter = decimalFormatter,
            configBuilder = mock(),
            rxBus = mock(),
            uel = mock(),
            wizardBolusExecutor = mock(),
            iobCobCalculator = iobCobCalculator,
            bolusProgressData = mock(),
            glucoseStatusProvider = mock(),
            sceneAutomationApi = mock(),
            sceneIconResolver = mock(),
        )
        pluginUnderTest.setPluginEnabledBlocking(app.aaps.core.data.plugin.PluginType.SYNC, true)
        whenever(rh.gs(any<Int>())).thenReturn("Mock String")
        whenever(rh.gs(any<Int>(), any())).thenReturn("Mock String")
        whenever(rh.gsNotLocalised(any<Int>())).thenReturn("Mock String")
        whenever(rh.gsNotLocalised(any<Int>(), any())).thenReturn("Mock String")
    }

    private val fakeUid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())

    private fun process(intent: Intent?) = runBlocking { pluginUnderTest.processIntent(intent) }

    @Test
    fun `processIntent prepares execution when plugin enabled and tag registered`() {
        val uid = NfcTagStore.tagUidHex(fakeUid) ?: ""
        nfcTagStore.saveCreatedTag(NfcCreatedTag(uid, "My Tag", listOf("{\"code\":\"LOOP_STOP\"}"), 0L))
        val intent = createNfcIntent(mockNfcTag(fakeUid))

        val result = process(intent)

        assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
        val ready = result as NfcPrepareResult.Ready
        assertThat(ready.tagUid).isEqualTo(uid)
    }

    @Test
    fun `processIntent does nothing when plugin disabled`() {
        pluginUnderTest.setPluginEnabledBlocking(app.aaps.core.data.plugin.PluginType.SYNC, false)
        whenever(rh.gs(any())).thenReturn("Disabled")

        val result = process(createNfcIntent(mockNfcTag(fakeUid)))

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    @Test
    fun `processIntent returns error when tag not registered`() {
        val intent = createNfcIntent(mockNfcTag(fakeUid))

        val result = process(intent)

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    @Test
    fun `processIntent returns error when intent is null`() {
        val result = process(null)

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    @Test
    fun `processIntent returns error when intent has no NFC Tag extra`() {
        val intent = createNfcIntent(nfcTag = null)

        val result = process(intent)

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    @Test
    fun `processIntent returns error when NDEF record MIME type does not match`() {
        val nfcTag = mockNfcTag(fakeUid)
        val record = mock<NdefRecord>()
        whenever(record.tnf).thenReturn(NdefRecord.TNF_MIME_MEDIA)
        whenever(record.type).thenReturn("text/plain".toByteArray(StandardCharsets.US_ASCII))
        whenever(record.payload).thenReturn(ByteArray(0))
        val message = mock<NdefMessage>()
        whenever(message.records).thenReturn(arrayOf(record))
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_NDEF_DISCOVERED)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)).thenReturn(arrayOf(message))
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)).thenReturn(nfcTag)

        val result = process(intent)

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    @Test
    fun `processIntent works on TAG_DISCOVERED for registered UID`() {
        val uid = NfcTagStore.tagUidHex(fakeUid) ?: ""
        nfcTagStore.saveCreatedTag(NfcCreatedTag(uid, "My Tag", listOf("{\"code\":\"LOOP_STOP\"}"), 0L))

        val result = process(createTagDiscoveredIntent(mockNfcTag(fakeUid)))

        assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
    }

    @Test
    fun `processIntent ignores just-written tag`() {
        val uid = NfcTagStore.tagUidHex(fakeUid) ?: ""
        nfcTagStore.markJustWritten(uid)

        val result = process(createNfcIntent(mockNfcTag(fakeUid)))

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun mockNfcTag(uid: ByteArray): Tag {
        val tag = mock<Tag>()
        whenever(tag.id).thenReturn(uid)
        return tag
    }

    private fun createNfcIntent(nfcTag: Tag? = mockNfcTag(fakeUid)): Intent {
        val record = mock<NdefRecord>()
        whenever(record.tnf).thenReturn(NdefRecord.TNF_MIME_MEDIA)
        whenever(record.type).thenReturn(NfcTagStore.MIME_TYPE.toByteArray(StandardCharsets.US_ASCII))
        whenever(record.payload).thenReturn(ByteArray(0))
        val message = mock<NdefMessage>()
        whenever(message.records).thenReturn(arrayOf(record))
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_NDEF_DISCOVERED)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES))
            .thenReturn(arrayOf(message))
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)).thenReturn(nfcTag)
        return intent
    }

    private fun createTagDiscoveredIntent(nfcTag: Tag? = mockNfcTag(fakeUid)): Intent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_TAG_DISCOVERED)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)).thenReturn(nfcTag)
        return intent
    }
}
