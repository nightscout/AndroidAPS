package app.aaps.plugins.main.general.nfcCommands

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import app.aaps.plugins.main.R
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets

class NfcControlActivityTest : TestBaseWithProfile() {
    @Mock lateinit var nfcPlugin: NfcCommandsPlugin
    private lateinit var activity: NfcControlActivity

    @BeforeEach
    fun setup() {
        NfcTagStore.clearJustWrittenForTest()
        activity = NfcControlActivity()
        activity.nfcPlugin = nfcPlugin
        activity.aapsLogger = aapsLogger
        activity.rh = rh
        whenever(nfcPlugin.isEnabled()).thenReturn(true)
    }

    private val fakeUid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())

    @Test
    fun `handleIntent executes commands when plugin enabled and tag registered`() {
        val mockTag = mockNfcTag(fakeUid)
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Ready(uid, "My Tag", listOf("LOOP STOP")))
        whenever(nfcPlugin.executeWithFeedback(any(), any(), any()))
            .thenReturn(NfcExecutionResult(true, "ok"))
        val intent = createNfcIntent(mockTag)

        activity.handleIntent(intent)

        verify(nfcPlugin).prepareExecution(uid)
        verify(nfcPlugin).executeWithFeedback(listOf("LOOP STOP"), "My Tag", "READ")
    }

    @Test
    fun `handleIntent does nothing when plugin disabled`() {
        whenever(nfcPlugin.isEnabled()).thenReturn(false)

        activity.handleIntent(createNfcIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin, never()).prepareExecution(any())
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent shows error when tag not registered`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Error("not registered"))

        activity.handleIntent(createNfcIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent does nothing when intent is null`() {
        activity.handleIntent(null)

        verify(nfcPlugin, never()).prepareExecution(any())
    }

    @Test
    fun `handleIntent does nothing for non-NDEF intent action`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn("android.intent.action.MAIN")

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any())
    }

    @Test
    fun `handleIntent does nothing when intent has no NFC Tag extra`() {
        val intent = createNfcIntent(nfcTag = null)

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any())
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent does nothing when NDEF record MIME type does not match`() {
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

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any())
    }

    @Test
    fun `handleIntent delegates log and feedback to plugin executeWithFeedback`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Ready(uid, "My Tag", listOf("LOOP STOP")))
        whenever(nfcPlugin.executeWithFeedback(any(), any(), any()))
            .thenReturn(NfcExecutionResult(true, "ok"))
        val intent = createNfcIntent(mockNfcTag(fakeUid))

        activity.handleIntent(intent)

        verify(nfcPlugin).executeWithFeedback(listOf("LOOP STOP"), "My Tag", "READ")
    }

    @Test
    fun `handleIntent executes commands on TAG_DISCOVERED for registered UID`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Ready(uid, "My Tag", listOf("LOOP STOP")))
        whenever(nfcPlugin.executeWithFeedback(any(), any(), any()))
            .thenReturn(NfcExecutionResult(true, "ok"))

        activity.handleIntent(createTagDiscoveredIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin).prepareExecution(uid)
        verify(nfcPlugin).executeWithFeedback(listOf("LOOP STOP"), "My Tag", "READ")
    }

    @Test
    fun `handleIntent does nothing silently on TAG_DISCOVERED for unregistered UID`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Error("not registered"))

        activity.handleIntent(createTagDiscoveredIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin).prepareExecution(uid)
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent does nothing on TAG_DISCOVERED without physical tag extra`() {
        activity.handleIntent(createTagDiscoveredIntent(nfcTag = null))

        verify(nfcPlugin, never()).prepareExecution(any())
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent silently ignores just-written tag on NDEF_DISCOVERED`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        NfcTagStore.markJustWritten(uid)

        activity.handleIntent(createNfcIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin, never()).prepareExecution(any())
        verify(nfcPlugin, never()).executeWithFeedback(any(), any(), any())
    }

    @Test
    fun `handleIntent silently ignores just-written tag on TAG_DISCOVERED`() {
        val uid = NfcTagStore.tagUidHex(fakeUid)!!
        NfcTagStore.markJustWritten(uid)

        activity.handleIntent(createTagDiscoveredIntent(mockNfcTag(fakeUid)))

        verify(nfcPlugin, never()).prepareExecution(any())
        verify(nfcPlugin, never()).executeWithFeedback(any(), any(), any())
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
