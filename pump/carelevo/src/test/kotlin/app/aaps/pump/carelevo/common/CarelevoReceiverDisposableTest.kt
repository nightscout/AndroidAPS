package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Robolectric tests for [CarelevoReceiverDisposable] — the teardown half of
 * [CarelevoObserveReceiver], which prevents the classic leaked-receiver crash when the plugin
 * clears its subscription in `onStop()`.
 *
 * `Context.unregisterReceiver` only has observable behaviour against a real framework registry, so
 * this runs under [RobolectricTestRunner] with a REAL application [Context] and asserts against
 * Robolectric's [ShadowApplication] receiver registry — a mocked Context would only prove that a
 * method was called, not that the receiver actually stopped being registered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoReceiverDisposableTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val action = "app.aaps.pump.carelevo.test.DISPOSABLE_ACTION"

    private val shadowApplication: ShadowApplication
        get() = Shadows.shadowOf(RuntimeEnvironment.getApplication())

    private fun isRegistered(receiver: BroadcastReceiver): Boolean =
        shadowApplication.registeredReceivers.any { it.broadcastReceiver === receiver }

    private fun registerReceiver(): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        return receiver
    }

    @Test
    fun `a fresh disposable is not disposed and leaves the receiver registered`() {
        val receiver = registerReceiver()

        val sut = CarelevoReceiverDisposable(context, receiver)

        assertThat(sut.isDisposed).isFalse()
        assertThat(isRegistered(receiver)).isTrue()

        sut.dispose()
    }

    @Test
    fun `dispose unregisters the receiver and flips isDisposed`() {
        val receiver = registerReceiver()
        val sut = CarelevoReceiverDisposable(context, receiver)

        sut.dispose()

        assertThat(sut.isDisposed).isTrue()
        assertThat(isRegistered(receiver)).isFalse()
    }

    @Test
    fun `dispose only unregisters its own receiver`() {
        val mine = registerReceiver()
        val someoneElse = registerReceiver()
        val sut = CarelevoReceiverDisposable(context, mine)

        sut.dispose()

        assertThat(isRegistered(mine)).isFalse()
        assertThat(isRegistered(someoneElse)).isTrue()

        context.unregisterReceiver(someoneElse)
    }
}
