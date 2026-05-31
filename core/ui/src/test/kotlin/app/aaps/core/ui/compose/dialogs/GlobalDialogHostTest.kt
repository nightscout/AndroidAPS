package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Integration test for the [GlobalDialogHost] router: it subscribes to [EventShowDialog] on an
 * [RxBus] and renders the matching dialog, completing the per-event deferred on button press.
 *
 * Uses a lightweight in-memory [RxBus] (a [MutableSharedFlow]) to avoid depending on the
 * shared:impl RxBusImpl. The collector subscribes via repeatOnLifecycle(STARTED); the test host
 * Activity is RESUMED under Robolectric, so events sent after the first waitForIdle are delivered.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlobalDialogHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val rxBus = FakeRxBus()

    private lateinit var okLabel: String
    private lateinit var cancelLabel: String
    private lateinit var yesLabel: String
    private lateinit var dismissLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        okLabel = context.getString(R.string.ok)
        cancelLabel = context.getString(R.string.cancel)
        yesLabel = context.getString(R.string.yes)
        dismissLabel = context.getString(R.string.dismiss)
        compose.setContent {
            MaterialTheme {
                GlobalDialogHost(rxBus)
            }
        }
        // Let the LaunchedEffect run so the RxBus collector is subscribed before any event is sent.
        compose.waitForIdle()
    }

    @Test
    fun okEvent_showsOkDialog_andFiresOnOk() {
        var fired = 0
        rxBus.send(EventShowDialog.Ok(title = "Title", message = "Ok message", onOk = { fired++ }))
        compose.waitForIdle()

        compose.onNodeWithText("Ok message").assertIsDisplayed()
        compose.onNodeWithText(okLabel).performClick()
        compose.waitForIdle()

        assertThat(fired).isEqualTo(1)
    }

    @Test
    fun okCancelEvent_confirm_firesOnOk() {
        var ok = 0
        var cancel = 0
        rxBus.send(
            EventShowDialog.OkCancel(
                title = "Confirm",
                message = "Proceed?",
                onOk = { ok++ },
                onCancel = { cancel++ }
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText(okLabel).performClick()
        compose.waitForIdle()

        assertThat(ok).isEqualTo(1)
        assertThat(cancel).isEqualTo(0)
    }

    @Test
    fun yesNoCancelEvent_yes_firesOnYes() {
        var yes = 0
        rxBus.send(
            EventShowDialog.YesNoCancel(
                title = "Save?",
                message = "Save changes?",
                onYes = { yes++ }
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText(yesLabel).performClick()
        compose.waitForIdle()

        assertThat(yes).isEqualTo(1)
    }

    @Test
    fun errorEvent_dismiss_firesOnDismiss() {
        var dismiss = 0
        rxBus.send(
            EventShowDialog.Error(
                title = "Error",
                message = "Boom",
                onDismiss = { dismiss++ }
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText("Boom").assertIsDisplayed()
        compose.onNodeWithText(dismissLabel).performClick()
        compose.waitForIdle()

        assertThat(dismiss).isEqualTo(1)
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
