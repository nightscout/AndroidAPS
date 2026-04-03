package app.aaps.plugins.main.general.nfcCommands

import android.nfc.NfcAdapter
import android.nfc.Tag
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NfcBuildActivityTest : TestBaseWithProfile() {
    @Mock lateinit var mockAdapter: NfcAdapter
    private lateinit var activity: NfcBuildActivity

    private val mockIssuedToken =
        NfcIssuedToken(
            token = "header.payload.sig",
            tokenId = "token-id-1",
            issuedAtMillis = 1_700_000_000_000L,
            expiresAtMillis = 1_700_000_000_000L + NfcTokenSupport.ONE_YEAR_MILLIS,
        )

    @BeforeEach
    fun setup() {
        // Use CALLS_REAL_METHODS to skip AppCompatActivity constructor (requires main looper)
        // while still being able to stub specific open methods.
        activity = Mockito.mock(NfcBuildActivity::class.java, Mockito.CALLS_REAL_METHODS)
        activity.aapsLogger = aapsLogger
        activity.nfcAdapter = mockAdapter
        // Stub Android-framework methods that cannot run outside the Activity lifecycle
        doNothing().whenever(activity).showWriteDialog(any())
        doNothing().whenever(activity).scheduleReaderModeDisable()
        doReturn("written").whenever(activity).buildWriteMessage(eq(true))
        doReturn("error").whenever(activity).buildWriteMessage(eq(false))
        doNothing().whenever(activity).persistWriteAttempt(any(), any(), any())
        doNothing().whenever(activity).persistWrittenTag(any(), any(), any())
    }

    // ── onTagDiscovered ────────────────────────────────────────────────────

    @Test
    fun `onTagDiscovered when not in write mode does nothing`() {
        val tag = mock<Tag>()

        activity.onTagDiscovered(tag)

        verify(activity, never()).issueToken(any())
        verify(activity, never()).buildAndWriteNdef(any(), any())
    }

    // ── write path via startWriteMode + onTagDiscovered ────────────────────

    @Test
    fun `writeTag persists failure and does not persist tag when buildAndWriteNdef returns false`() {
        doReturn(mockIssuedToken).whenever(activity).issueToken(any())
        doReturn(false).whenever(activity).buildAndWriteNdef(any(), any())

        activity.startWriteMode(listOf("LOOP STOP"), "My Tag")
        activity.onTagDiscovered(mock<Tag>())

        verify(activity).buildAndWriteNdef(any(), eq(mockIssuedToken.token))
        verify(activity).persistWriteAttempt(eq("My Tag"), eq(false), eq("error"))
        verify(activity, never()).persistWrittenTag(any(), any(), any())
    }

    @Test
    fun `writeTag persists success and tag when buildAndWriteNdef returns true`() {
        doReturn(mockIssuedToken).whenever(activity).issueToken(any())
        doReturn(true).whenever(activity).buildAndWriteNdef(any(), any())

        activity.startWriteMode(listOf("LOOP STOP"), "My Tag")
        activity.onTagDiscovered(mock<Tag>())

        verify(activity).buildAndWriteNdef(any(), eq(mockIssuedToken.token))
        verify(activity).persistWriteAttempt(eq("My Tag"), eq(true), eq("written"))
        verify(activity).persistWrittenTag(eq(mockIssuedToken), eq("My Tag"), eq(listOf("LOOP STOP")))
    }

    @Test
    fun `writeTag uses first command as name when tagName is blank`() {
        doReturn(mockIssuedToken).whenever(activity).issueToken(any())
        doReturn(false).whenever(activity).buildAndWriteNdef(any(), any())

        activity.startWriteMode(listOf("LOOP STOP")) // no tagName
        activity.onTagDiscovered(mock<Tag>())

        verify(activity).persistWriteAttempt(eq("LOOP STOP"), any(), any())
    }

    // ── disableWritingMode ─────────────────────────────────────────────────

    @Test
    fun `disableWritingMode immediate calls disableReaderMode on adapter`() {
        activity.disableWritingMode()

        verify(mockAdapter).disableReaderMode(activity)
        verify(activity, never()).scheduleReaderModeDisable()
    }

    @Test
    fun `disableWritingMode delayed schedules reader mode disable without immediate call`() {
        activity.disableWritingMode(delayReaderModeDisable = true)

        verify(mockAdapter, never()).disableReaderMode(any())
        verify(activity).scheduleReaderModeDisable()
    }

    @Test
    fun `disableWritingMode with null adapter does not throw`() {
        activity.nfcAdapter = null

        activity.disableWritingMode() // should return without error
    }
}
