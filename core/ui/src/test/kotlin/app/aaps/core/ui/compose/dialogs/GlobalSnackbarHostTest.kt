package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Integration test for the [GlobalSnackbarHost] router: it subscribes to [EventShowSnackbar] on an
 * [RxBus] and surfaces the message on a shared [SnackbarHostState].
 *
 * Wrapped in [AapsTheme] (which the host's styling reads via AapsTheme.snackbarColors), so a mocked
 * [Preferences] is provided through [LocalPreferences] with a fixed light-mode value for determinism.
 * A lightweight in-memory [RxBus] avoids depending on shared:impl RxBusImpl.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlobalSnackbarHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val rxBus = FakeRxBus()

    private val preferences = mock<Preferences> {
        on { observe(StringKey.GeneralDarkMode) } doReturn MutableStateFlow("light")
    }

    @Test
    fun snackbarEvent_surfacesMessage() {
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    GlobalSnackbarHost(rxBus, hostState = SnackbarHostState())
                }
            }
        }
        // Let the LaunchedEffect run so the RxBus collector is subscribed before the event is sent.
        compose.waitForIdle()

        rxBus.send(EventShowSnackbar(message = "Saved successfully", type = EventShowSnackbar.Type.Success))
        compose.waitForIdle()

        compose.onNodeWithText("Saved successfully").assertIsDisplayed()
    }

    private class FakeRxBus : RxBus {

        private val events = MutableSharedFlow<Event>(extraBufferCapacity = 16)

        override fun send(event: Event) {
            check(events.tryEmit(event)) { "event buffer overflow" }
        }

        override fun <T : Any> toObservable(eventType: Class<T>): Observable<T> =
            throw UnsupportedOperationException("not needed in tests")

        @Suppress("UNCHECKED_CAST")
        override fun <T : Event> toFlow(eventType: Class<T>): Flow<T> =
            events.filter { eventType.isInstance(it) } as Flow<T>
    }
}
