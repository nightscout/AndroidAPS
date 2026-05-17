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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets

class NfcControlActivityTest : TestBaseWithProfile() {
    @Mock lateinit var nfcPlugin: NfcCommandsPlugin
    private lateinit var activity: NfcControlActivity

    @BeforeEach
    fun setup() {
        activity = spy(NfcControlActivity())
        activity.nfcPlugin = nfcPlugin
        activity.aapsLogger = aapsLogger
        activity.rh = rh
        whenever(nfcPlugin.isEnabled()).thenReturn(true)
        // Stub appendReadLogEntry to avoid SharedPreferences access in unit tests
        org.mockito.kotlin
            .doNothing()
            .whenever(activity)
            .appendReadLogEntry(any(), any())
    }

    private val fakeUid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())

    @Test
    fun `handleIntent executes commands when plugin enabled and tag registered`() {
        val mockTag = mockNfcTag(fakeUid)
        val uid = NfcTokenSupport.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Ready(uid, listOf("LOOP STOP")))
        whenever(nfcPlugin.executeCascade(listOf("LOOP STOP")))
            .thenReturn(NfcExecutionResult(true, "ok"))
        val intent = createNfcIntent(mockTag)

        activity.handleIntent(intent)

        verify(nfcPlugin).prepareExecution(uid)
        verify(nfcPlugin).executeCascade(listOf("LOOP STOP"))
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
        val uid = NfcTokenSupport.tagUidHex(fakeUid)!!
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
    fun `handleIntent calls appendReadLogEntry after successful execution`() {
        val uid = NfcTokenSupport.tagUidHex(fakeUid)!!
        whenever(nfcPlugin.prepareExecution(uid))
            .thenReturn(NfcPrepareResult.Ready(uid, listOf("LOOP STOP")))
        whenever(nfcPlugin.executeCascade(listOf("LOOP STOP")))
            .thenReturn(NfcExecutionResult(true, "ok"))
        org.mockito.kotlin
            .doNothing()
            .whenever(activity)
            .appendReadLogEntry(any(), any())
        val intent = createNfcIntent(mockNfcTag(fakeUid))

        activity.handleIntent(intent)

        verify(activity).appendReadLogEntry(org.mockito.kotlin.eq(uid), any())
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
        whenever(record.type).thenReturn(NfcTokenSupport.MIME_TYPE.toByteArray(StandardCharsets.US_ASCII))
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
}
