package app.aaps.pump.carelevo.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.disposables.Disposable
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Robolectric tests for [CarelevoObserveReceiver] — the Rx wrapper the plugin uses to observe
 * Bluetooth adapter state changes.
 *
 * [CarelevoObserveReceiver] talks to the Android framework directly (`Context.registerReceiver` /
 * `unregisterReceiver`, [BroadcastReceiver], [IntentFilter.matchAction]), so a plain JVM Mockito
 * test could only observe that *some* mock method was called. Under [RobolectricTestRunner] with a
 * REAL application [Context], registration is asserted against Robolectric's [ShadowApplication]
 * receiver registry — i.e. against the same bookkeeping the OS would do.
 *
 * The registered [BroadcastReceiver] is pulled back out of the registry and its `onReceive` invoked
 * directly, exactly as the system dispatcher would. That is deliberate rather than going through
 * `context.sendBroadcast`: it keeps the test off the main-looper dispatch queue (no `idle()`
 * ordering to reason about) AND it is the only way to reach the wrapper's two defensive branches —
 * a null intent and a non-matching action — which a real broadcast could never deliver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoObserveReceiverTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val action = "app.aaps.pump.carelevo.test.ACTION_STATE_CHANGED"
    private val otherAction = "app.aaps.pump.carelevo.test.SOMETHING_ELSE"

    private val openSubscriptions = mutableListOf<Disposable>()

    private val shadowApplication: ShadowApplication
        get() = Shadows.shadowOf(RuntimeEnvironment.getApplication())

    /** Every receiver currently registered for [action] by the SUT. */
    private fun wrappersFor(action: String): List<ShadowApplication.Wrapper> =
        shadowApplication.registeredReceivers.filter { it.intentFilter.matchAction(action) }

    private fun observe(vararg actions: String, onNext: (Intent) -> Unit): Disposable {
        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }
        return CarelevoObserveReceiver(context, filter)
            .subscribe { intent -> onNext(intent) }
            .also { openSubscriptions += it }
    }

    @After
    fun tearDown() {
        // Never leak a registered receiver into the next test's registry.
        openSubscriptions.filterNot { it.isDisposed }.forEach { it.dispose() }
    }

    @Test
    fun `subscribing registers a receiver for the supplied filter`() {
        observe(action) {}

        val wrappers = wrappersFor(action)
        assertThat(wrappers).hasSize(1)
        assertThat(wrappers.single().broadcastReceiver).isNotNull()
        assertThat(wrappers.single().intentFilter.matchAction(otherAction)).isFalse()
    }

    @Test
    fun `the receiver is never registered as exported`() {
        // Only protected system broadcasts are consumed; nothing here may be targetable by other apps.
        observe(action) {}

        assertThat(wrappersFor(action).single().flags and Context.RECEIVER_EXPORTED).isEqualTo(0)
    }

    @Test
    fun `a matching broadcast is emitted to the observer`() {
        val received = mutableListOf<Intent>()
        observe(action) { received += it }

        val intent = Intent(action).putExtra("state", 12)
        wrappersFor(action).single().broadcastReceiver.onReceive(context, intent)

        assertThat(received).hasSize(1)
        assertThat(received.single().action).isEqualTo(action)
        assertThat(received.single().getIntExtra("state", -1)).isEqualTo(12)
    }

    @Test
    fun `repeated broadcasts are all emitted on the same subscription`() {
        val received = mutableListOf<Intent>()
        observe(action) { received += it }
        val receiver = wrappersFor(action).single().broadcastReceiver

        receiver.onReceive(context, Intent(action).putExtra("state", 1))
        receiver.onReceive(context, Intent(action).putExtra("state", 2))
        receiver.onReceive(context, Intent(action).putExtra("state", 3))

        assertThat(received.map { it.getIntExtra("state", -1) }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `an intent whose action is outside the filter is not emitted`() {
        val received = mutableListOf<Intent>()
        observe(action) { received += it }

        wrappersFor(action).single().broadcastReceiver.onReceive(context, Intent(otherAction))

        assertThat(received).isEmpty()
    }

    @Test
    fun `a null intent is ignored`() {
        val received = mutableListOf<Intent>()
        observe(action) { received += it }

        wrappersFor(action).single().broadcastReceiver.onReceive(context, null)

        assertThat(received).isEmpty()
    }

    @Test
    fun `every action of a multi-action filter is emitted`() {
        val received = mutableListOf<Intent>()
        observe(action, otherAction) { received += it }
        val receiver = wrappersFor(action).single().broadcastReceiver

        receiver.onReceive(context, Intent(action))
        receiver.onReceive(context, Intent(otherAction))

        assertThat(received.map { it.action }).containsExactly(action, otherAction).inOrder()
    }

    @Test
    fun `disposing the subscription unregisters the receiver`() {
        val disposable = observe(action) {}
        assertThat(wrappersFor(action)).hasSize(1)

        disposable.dispose()

        assertThat(disposable.isDisposed).isTrue()
        assertThat(wrappersFor(action)).isEmpty()
    }

    @Test
    fun `each subscription owns its own receiver and disposal only unregisters that one`() {
        val firstReceived = mutableListOf<Intent>()
        val secondReceived = mutableListOf<Intent>()
        val first = observe(action) { firstReceived += it }
        observe(action) { secondReceived += it }
        assertThat(wrappersFor(action)).hasSize(2)

        first.dispose()

        val surviving = wrappersFor(action)
        assertThat(surviving).hasSize(1)
        surviving.single().broadcastReceiver.onReceive(context, Intent(action))
        assertThat(firstReceived).isEmpty()
        assertThat(secondReceived).hasSize(1)
    }
}
