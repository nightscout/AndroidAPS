package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Robolectric tests for [registerCarelevoReceiver] — the helper the plugin uses to follow Bluetooth
 * adapter state changes. It registers a receiver for a filter, gives every matching intent to a
 * callback, and returns the receiver so the caller can unregister it when it stops.
 *
 * The helper talks to the Android framework directly (`Context.registerReceiver` /
 * `unregisterReceiver`, [BroadcastReceiver], [IntentFilter.matchAction]), so a plain JVM Mockito
 * test could only observe that *some* mock method was called. Under [RobolectricTestRunner] with a
 * REAL application [Context], registration and unregistration are asserted against Robolectric's
 * [ShadowApplication] receiver registry — i.e. against the same bookkeeping the OS would do. A mocked
 * Context would only prove that a method was called, not that the receiver actually stopped being registered.
 *
 * The registered [BroadcastReceiver] is pulled back out of the registry and its `onReceive` invoked
 * directly, exactly as the system dispatcher would. That is deliberate rather than going through
 * `context.sendBroadcast`: it keeps the test off the main-looper dispatch queue (no `idle()`
 * ordering to reason about) AND it is the only way to reach the helper's two defensive branches —
 * a null intent and a non-matching action — which a real broadcast could never deliver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoReceiverTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val action = "app.aaps.pump.carelevo.test.ACTION_STATE_CHANGED"
    private val otherAction = "app.aaps.pump.carelevo.test.SOMETHING_ELSE"

    private val registered = mutableListOf<BroadcastReceiver>()

    private val shadowApplication: ShadowApplication
        get() = Shadows.shadowOf(RuntimeEnvironment.getApplication())

    /** Every receiver currently registered for [action]. */
    private fun wrappersFor(action: String): List<ShadowApplication.Wrapper> =
        shadowApplication.registeredReceivers.filter { it.intentFilter.matchAction(action) }

    private fun isRegistered(receiver: BroadcastReceiver): Boolean =
        shadowApplication.registeredReceivers.any { it.broadcastReceiver === receiver }

    private fun register(vararg actions: String, onIntent: (Intent) -> Unit): BroadcastReceiver {
        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }
        return registerCarelevoReceiver(context, filter, onIntent).also { registered += it }
    }

    @After
    fun tearDown() {
        // Never leak a registered receiver into the next test's registry.
        registered.filter { isRegistered(it) }.forEach { context.unregisterReceiver(it) }
    }

    @Test
    fun `registering registers a receiver for the supplied filter`() {
        val receiver = register(action) {}

        val wrappers = wrappersFor(action)
        assertThat(wrappers).hasSize(1)
        assertThat(wrappers.single().broadcastReceiver).isSameInstanceAs(receiver)
        assertThat(wrappers.single().intentFilter.matchAction(otherAction)).isFalse()
    }

    @Test
    fun `the receiver stays registered until it is unregistered`() {
        val receiver = register(action) {}

        assertThat(isRegistered(receiver)).isTrue()
    }

    @Test
    fun `the receiver is registered as exported so the Bluetooth process can reach it`() {
        // The Bluetooth stack sends with its own uid, not the system uid; a not-exported receiver never gets it.
        // Robolectric delivers either way, so the flag is all a test can check.
        register(action) {}

        assertThat(wrappersFor(action).single().flags and Context.RECEIVER_EXPORTED).isEqualTo(Context.RECEIVER_EXPORTED)
        assertThat(wrappersFor(action).single().flags and Context.RECEIVER_NOT_EXPORTED).isEqualTo(0)
    }

    @Test
    fun `a matching broadcast is given to the callback`() {
        val received = mutableListOf<Intent>()
        register(action) { received += it }

        val intent = Intent(action).putExtra("state", 12)
        wrappersFor(action).single().broadcastReceiver.onReceive(context, intent)

        assertThat(received).hasSize(1)
        assertThat(received.single().action).isEqualTo(action)
        assertThat(received.single().getIntExtra("state", -1)).isEqualTo(12)
    }

    @Test
    fun `repeated broadcasts are all given to the callback of the same registration`() {
        val received = mutableListOf<Intent>()
        register(action) { received += it }
        val receiver = wrappersFor(action).single().broadcastReceiver

        receiver.onReceive(context, Intent(action).putExtra("state", 1))
        receiver.onReceive(context, Intent(action).putExtra("state", 2))
        receiver.onReceive(context, Intent(action).putExtra("state", 3))

        assertThat(received.map { it.getIntExtra("state", -1) }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `an intent whose action is outside the filter is not given to the callback`() {
        val received = mutableListOf<Intent>()
        register(action) { received += it }

        wrappersFor(action).single().broadcastReceiver.onReceive(context, Intent(otherAction))

        assertThat(received).isEmpty()
    }

    @Test
    fun `a null intent is ignored`() {
        val received = mutableListOf<Intent>()
        register(action) { received += it }

        wrappersFor(action).single().broadcastReceiver.onReceive(context, null)

        assertThat(received).isEmpty()
    }

    @Test
    fun `every action of a multi-action filter is given to the callback`() {
        val received = mutableListOf<Intent>()
        register(action, otherAction) { received += it }
        val receiver = wrappersFor(action).single().broadcastReceiver

        receiver.onReceive(context, Intent(action))
        receiver.onReceive(context, Intent(otherAction))

        assertThat(received.map { it.action }).containsExactly(action, otherAction).inOrder()
    }

    @Test
    fun `unregistering the returned receiver removes it, so nothing more is delivered`() {
        val receiver = register(action) {}
        assertThat(wrappersFor(action)).hasSize(1)

        context.unregisterReceiver(receiver)

        assertThat(isRegistered(receiver)).isFalse()
        assertThat(wrappersFor(action)).isEmpty()
    }

    @Test
    fun `each registration owns its own receiver and unregistering only removes that one`() {
        val firstReceived = mutableListOf<Intent>()
        val secondReceived = mutableListOf<Intent>()
        val first = register(action) { firstReceived += it }
        val second = register(action) { secondReceived += it }
        assertThat(wrappersFor(action)).hasSize(2)

        context.unregisterReceiver(first)

        assertThat(isRegistered(first)).isFalse()
        assertThat(isRegistered(second)).isTrue()
        val surviving = wrappersFor(action)
        assertThat(surviving).hasSize(1)
        surviving.single().broadcastReceiver.onReceive(context, Intent(action))
        assertThat(firstReceived).isEmpty()
        assertThat(secondReceived).hasSize(1)
    }
}
