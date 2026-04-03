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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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

    // ── existing behaviour (no rewrite) ───────────────────────────────────

    @Test
    fun `handleIntent executes commands when plugin enabled and token valid`() {
        val token = "jwt-token"
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Ready("test-token-id", listOf("LOOP STOP"), null, null))
        whenever(nfcPlugin.executeCascade(listOf("LOOP STOP")))
            .thenReturn(NfcExecutionResult(true, "ok"))
        val intent = createNfcIntent(token)

        activity.handleIntent(intent)

        verify(nfcPlugin).prepareExecution(token)
        verify(nfcPlugin).executeCascade(listOf("LOOP STOP"))
    }

    @Test
    fun `handleIntent does nothing when plugin disabled`() {
        whenever(nfcPlugin.isEnabled()).thenReturn(false)

        activity.handleIntent(createNfcIntent("ANY"))

        verify(nfcPlugin, never()).prepareExecution(any(), anyOrNull())
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent decodes payload as UTF-8`() {
        val token = "header.payload.signature"
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Ready("test-token-id", listOf("LOOP STOP"), null, null))
        whenever(nfcPlugin.executeCascade(any()))
            .thenReturn(NfcExecutionResult(true, "ok"))

        activity.handleIntent(createNfcIntent(token.toByteArray(StandardCharsets.UTF_8)))

        verify(nfcPlugin).prepareExecution(token)
    }

    @Test
    fun `handleIntent does nothing when intent is null`() {
        activity.handleIntent(null)

        verify(nfcPlugin, never()).prepareExecution(any(), anyOrNull())
    }

    @Test
    fun `handleIntent does nothing for non-NDEF intent action`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn("android.intent.action.MAIN")

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any(), anyOrNull())
    }

    @Test
    fun `handleIntent erases tag when prepareExecution returns Error with eraseTag=true`() {
        val token = "jwt-token"
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Error("blacklisted", eraseTag = true))
        val intent = createNfcIntent(token)

        activity.handleIntent(intent)

        verify(activity).erasePhysicalTag(intent)
        verify(nfcPlugin, never()).executeCascade(any())
    }

    // ── rewrite paths ──────────────────────────────────────────────────────

    @Test
    fun `handleIntent rewrites tag and executes commands when rewriteWith is set and write succeeds`() {
        val token = "jwt-token"
        val commands = listOf("LOOP STOP")
        val issuedAt = System.currentTimeMillis()
        val mockToken =
            NfcIssuedToken(
                token = "new.token.value",
                tokenId = "new-id",
                issuedAtMillis = issuedAt,
                expiresAtMillis = issuedAt + NfcTokenSupport.ONE_YEAR_MILLIS,
            )
        val oldTag =
            NfcCreatedTag(
                id = "old-id",
                name = "My Tag",
                commands = commands,
                token = token,
                createdAtMillis = issuedAt - 1000L,
                expiresAtMillis = issuedAt + NfcTokenSupport.THIRTY_DAYS_MILLIS - 1L,
            )
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Ready("old-id", commands, mockToken, oldTag))
        whenever(nfcPlugin.executeCascade(commands))
            .thenReturn(NfcExecutionResult(true, "ok"))
        // Stub writeNdefToPhysicalTag to simulate a successful write
        doReturn(true).whenever(activity).writeNdefToPhysicalTag(any(), any())
        whenever(nfcPlugin.replaceTag(any(), any())).then { }
        val mockNfcTag = mock<Tag>()
        val intent = createNfcIntent(token, mockNfcTag)

        activity.handleIntent(intent)

        verify(activity).writeNdefToPhysicalTag(any(), any())
        verify(nfcPlugin).executeCascade(commands)
    }

    @Test
    fun `handleIntent does not execute commands when rewriteWith is set but write fails`() {
        val token = "jwt-token"
        val commands = listOf("LOOP STOP")
        val issuedAt = System.currentTimeMillis()
        val mockToken =
            NfcIssuedToken(
                "new.tok",
                "new-id",
                issuedAt,
                issuedAt + NfcTokenSupport.ONE_YEAR_MILLIS,
            )
        val oldTag =
            NfcCreatedTag(
                id = "old-id",
                name = "My Tag",
                commands = commands,
                token = token,
                createdAtMillis = issuedAt - 1000L,
                expiresAtMillis = issuedAt + NfcTokenSupport.THIRTY_DAYS_MILLIS - 1L,
            )
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Ready("old-id", commands, mockToken, oldTag))
        whenever(rh.gs(R.string.nfccommands_tag_rewrite_failed))
            .thenReturn("Tag rewrite failed — please tap again")
        // Stub write to fail
        doReturn(false).whenever(activity).writeNdefToPhysicalTag(any(), any())
        val intent = createNfcIntent(token, mock<Tag>())

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent does nothing when intent has no NFC Tag extra`() {
        // An explicit intent without EXTRA_TAG cannot originate from a real NFC scan.
        val intent = createNfcIntent("jwt-token", nfcTag = null)

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any(), anyOrNull())
        verify(nfcPlugin, never()).executeCascade(any())
    }

    @Test
    fun `handleIntent does nothing when NDEF record MIME type does not match`() {
        val record = mock<NdefRecord>()
        whenever(record.tnf).thenReturn(NdefRecord.TNF_MIME_MEDIA)
        whenever(record.type).thenReturn("text/plain".toByteArray(java.nio.charset.StandardCharsets.US_ASCII))
        whenever(record.payload).thenReturn("jwt-token".toByteArray())
        val message = mock<NdefMessage>()
        whenever(message.records).thenReturn(arrayOf(record))
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_NDEF_DISCOVERED)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)).thenReturn(arrayOf(message))
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)).thenReturn(mock())

        activity.handleIntent(intent)

        verify(nfcPlugin, never()).prepareExecution(any(), anyOrNull())
    }

    // ── log tests ─────────────────────────────────────────────────────────

    @Test
    fun `handleIntent calls appendReadLogEntry after successful execution`() {
        val token = "jwt-token"
        whenever(nfcPlugin.prepareExecution(token))
            .thenReturn(NfcPrepareResult.Ready("test-token-id", listOf("LOOP STOP"), null, null))
        whenever(nfcPlugin.executeCascade(listOf("LOOP STOP")))
            .thenReturn(NfcExecutionResult(true, "ok"))
        org.mockito.kotlin
            .doNothing()
            .whenever(activity)
            .appendReadLogEntry(any(), any())
        val intent = createNfcIntent(token)

        activity.handleIntent(intent)

        verify(activity).appendReadLogEntry(eq("test-token-id"), any())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun createNfcIntent(
        payload: String,
        nfcTag: Tag? = mock(),
    ): Intent = createNfcIntent(payload.toByteArray(), nfcTag)

    private fun createNfcIntent(
        payloadBytes: ByteArray,
        nfcTag: Tag? = mock(),
    ): Intent {
        val record = mock<NdefRecord>()
        whenever(record.tnf).thenReturn(NdefRecord.TNF_MIME_MEDIA)
        whenever(record.type).thenReturn(NfcTokenSupport.MIME_TYPE.toByteArray(java.nio.charset.StandardCharsets.US_ASCII))
        whenever(record.payload).thenReturn(payloadBytes)
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
