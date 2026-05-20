package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.keys.BooleanKey
import app.aaps.plugins.main.R
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NfcForegroundDispatchTest : TestBaseWithProfile() {

    @Mock lateinit var mockActivity: Activity
    @Mock lateinit var mockRxBus: app.aaps.core.interfaces.rx.bus.RxBus

    private lateinit var dispatch: NfcForegroundDispatch

    @BeforeEach
    fun setup() {
        whenever(mockActivity.packageName).thenReturn("app.aaps")
        dispatch = NfcForegroundDispatch(mockActivity, preferences)
    }

    // ── onResume ──────────────────────────────────────────────────────────────

    @Test
    fun `onResume does nothing when preference is off`() {
        whenever(preferences.get(BooleanKey.NfcForegroundPriority)).thenReturn(false)

        dispatch.onResume()

        verify(mockActivity, never()).getSystemService(NfcManager::class.java)
    }

    @Test
    fun `onResume does nothing when NFC manager is unavailable`() {
        whenever(preferences.get(BooleanKey.NfcForegroundPriority)).thenReturn(true)
        whenever(mockActivity.getSystemService(NfcManager::class.java)).thenReturn(null)

        dispatch.onResume() // must not throw
    }

    @Test
    fun `onResume does nothing when NFC adapter is null`() {
        whenever(preferences.get(BooleanKey.NfcForegroundPriority)).thenReturn(true)
        val nfcManager = mock<NfcManager>()
        whenever(mockActivity.getSystemService(NfcManager::class.java)).thenReturn(nfcManager)
        whenever(nfcManager.defaultAdapter).thenReturn(null)

        dispatch.onResume() // must not throw
    }

    @Test
    fun `onResume enables foreground dispatch when preference on and adapter available`() {
        whenever(preferences.get(BooleanKey.NfcForegroundPriority)).thenReturn(true)
        val nfcManager = mock<NfcManager>()
        val nfcAdapter = mock<NfcAdapter>()
        whenever(mockActivity.getSystemService(NfcManager::class.java)).thenReturn(nfcManager)
        whenever(nfcManager.defaultAdapter).thenReturn(nfcAdapter)

        // PendingIntent.getActivity() is a native static — the Android stub returns null,
        // so we verify the call happened with any PendingIntent value.
        dispatch.onResume()

        verify(nfcAdapter).enableForegroundDispatch(org.mockito.kotlin.eq(mockActivity), org.mockito.kotlin.isNull(), org.mockito.kotlin.isNull(), org.mockito.kotlin.isNull())
    }

    // ── onPause ───────────────────────────────────────────────────────────────

    @Test
    fun `onPause does nothing when dispatch was never enabled`() {
        dispatch.onPause() // must not crash or interact with activity
        verify(mockActivity, never()).getSystemService(NfcManager::class.java)
    }

    @Test
    fun `onPause disables dispatch after it was enabled`() {
        val nfcAdapter = enableDispatch()

        dispatch.onPause()

        verify(nfcAdapter).disableForegroundDispatch(mockActivity)
    }

    @Test
    fun `onPause does not disable dispatch a second time`() {
        val nfcAdapter = enableDispatch()

        dispatch.onPause()
        dispatch.onPause() // second call must not call disableForegroundDispatch again

        verify(nfcAdapter, org.mockito.kotlin.times(1)).disableForegroundDispatch(mockActivity)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Drives the dispatch into the enabled state and returns the adapter mock. */
    private fun enableDispatch(): NfcAdapter {
        whenever(preferences.get(BooleanKey.NfcForegroundPriority)).thenReturn(true)
        val nfcManager = mock<NfcManager>()
        val nfcAdapter = mock<NfcAdapter>()
        whenever(mockActivity.getSystemService(NfcManager::class.java)).thenReturn(nfcManager)
        whenever(nfcManager.defaultAdapter).thenReturn(nfcAdapter)
        dispatch.onResume()
        return nfcAdapter
    }

    // ── onNewIntent ───────────────────────────────────────────────────────────

    @Test
    fun `onNewIntent forwards NDEF_DISCOVERED intent to NfcControlActivity`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_NDEF_DISCOVERED)
        whenever(intent.extras).thenReturn(null)

        dispatch.onNewIntent(intent)

        // Verify startActivity was called (Intent(Context, Class) component not inspectable in JVM stubs)
        verify(mockActivity).startActivity(any())
    }

    @Test
    fun `onNewIntent forwards TAG_DISCOVERED intent to NfcControlActivity`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(NfcAdapter.ACTION_TAG_DISCOVERED)
        whenever(intent.extras).thenReturn(null)

        dispatch.onNewIntent(intent)

        verify(mockActivity).startActivity(any())
    }

    @Test
    fun `onNewIntent ignores null action`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(null)

        dispatch.onNewIntent(intent)

        verify(mockActivity, never()).startActivity(any())
    }

    @Test
    fun `onNewIntent ignores unrelated actions`() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn("android.intent.action.MAIN")

        dispatch.onNewIntent(intent)

        verify(mockActivity, never()).startActivity(any())
    }

    // ── observeWarning ────────────────────────────────────────────────────────
    // Use Dispatchers.Unconfined so collectors run synchronously in the calling
    // thread — StateFlow value changes are then visible immediately without
    // needing advanceUntilIdle().

    private fun unconfinedScope() = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Test
    fun `observeWarning subscribes to NfcForegroundPriority preference`() {
        val scope = unconfinedScope()
        val prefFlow = MutableStateFlow(false)
        whenever(preferences.observe(BooleanKey.NfcForegroundPriority)).thenReturn(prefFlow)

        dispatch.observeWarning(scope, mockRxBus, rh)

        verify(preferences).observe(BooleanKey.NfcForegroundPriority)
        scope.cancel()
    }

    @Test
    fun `observeWarning sends dialog when preference changes to true`() {
        whenever(rh.gs(R.string.nfc_foreground_priority_warning_title)).thenReturn("NFC Foreground Priority")
        whenever(rh.gs(R.string.nfc_foreground_priority_warning_message)).thenReturn("Warning message")
        val scope = unconfinedScope()
        val prefFlow = MutableStateFlow(false)
        whenever(preferences.observe(BooleanKey.NfcForegroundPriority)).thenReturn(prefFlow)

        dispatch.observeWarning(scope, mockRxBus, rh)
        prefFlow.value = true // Unconfined: collector runs synchronously

        verify(mockRxBus).send(any<EventShowDialog.Ok>())
        scope.cancel()
    }

    @Test
    fun `observeWarning does not send dialog when preference changes to false`() {
        val scope = unconfinedScope()
        val prefFlow = MutableStateFlow(true) // initial true is the dropped first emission
        whenever(preferences.observe(BooleanKey.NfcForegroundPriority)).thenReturn(prefFlow)

        dispatch.observeWarning(scope, mockRxBus, rh)
        prefFlow.value = false

        verify(mockRxBus, never()).send(any<EventShowDialog.Ok>())
        scope.cancel()
    }

    @Test
    fun `observeWarning does not send dialog for initial value`() {
        val scope = unconfinedScope()
        val prefFlow = MutableStateFlow(true) // dropped by drop(1), no further changes
        whenever(preferences.observe(BooleanKey.NfcForegroundPriority)).thenReturn(prefFlow)

        dispatch.observeWarning(scope, mockRxBus, rh)

        verify(mockRxBus, never()).send(any<EventShowDialog.Ok>())
        scope.cancel()
    }
}
