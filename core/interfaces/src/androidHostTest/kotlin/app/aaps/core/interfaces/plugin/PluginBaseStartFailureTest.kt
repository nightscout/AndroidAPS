package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.seconds

/**
 * That a plugin whose [PluginBase.onStart] throws does not take the app down with it.
 *
 * Before this the throw reached no handler: it left the plugin's scope dead, so every later launch on it
 * was a silent no-op, and on the startup path it killed the process. Swallowing it alone would be worse
 * again - the plugin would stay marked ENABLED, half built and quiet - so the failure has to stay visible
 * both to the user and to the code that asks whether a pump is usable.
 */
class PluginBaseStartFailureTest {

    /** Records what was posted instead of mocking it, so the level and the sound can be asserted. */
    private class RecordingNotifications : NotificationManager {

        data class Posted(val id: NotificationId, val level: NotificationLevel, val sound: AlarmSound?)

        val posted = mutableListOf<Posted>()
        val dismissed = mutableListOf<NotificationId>()

        override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())

        override fun cleanUp() {}

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            validMinutes: Int,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle {
            posted += Posted(id, level, sound)
            return NotificationHandle(posted.size)
        }

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle {
            posted += Posted(id, level, sound)
            return NotificationHandle(posted.size)
        }

        override fun post(
            id: NotificationId,
            textRef: TextRef,
            level: NotificationLevel,
            validMinutes: Int,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle {
            posted += Posted(id, level, sound)
            return NotificationHandle(posted.size)
        }

        override fun dismiss(id: NotificationId) {
            dismissed += id
        }

        override fun dismiss(handle: NotificationHandle) {}

        override fun muteAllAlarms() {}
    }

    /** A mock would hand back null for [TextResolver.gs], and the notification text may not be null. */
    private class FixedText : TextResolver {

        override fun gs(ref: TextRef): String = "text"
        override fun gs(ref: TextRef, vararg args: Any?): String = "text"
        override fun gsNotLocalised(ref: TextRef): String = "text"
        override fun shortTextMode(): Boolean = false
    }

    private class TestPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager
    ) : PluginBase(PluginDescription().mainType(PluginType.GENERAL), aapsLogger, rh, notificationManager) {

        var failStart = false
        var failStop = false

        /** Set to hold [onStart] open, so "scheduled" and "finished" can be told apart. */
        var startGate: CompletableDeferred<Unit>? = null

        val events = mutableListOf<String>()

        override suspend fun onStart() {
            startGate?.await()
            events += "start"
            if (failStart) throw IllegalStateException("onStart boom")
        }

        override suspend fun onStop() {
            events += "stop"
            if (failStop) throw IllegalStateException("onStop boom")
        }

        /** [pluginScope] is protected, and this is what the drivers do with it. */
        fun launchOwnWork(block: suspend () -> Unit): Job = pluginScope.launch { block() }
    }

    private val notifications = RecordingNotifications()
    private fun plugin() = TestPlugin(mock<AAPSLogger>(), FixedText(), notifications)

    @Test
    fun `a failing onStart does not throw out of the transition`() = runBlocking {
        val sut = plugin()
        sut.failStart = true

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(sut.events).containsExactly("start")
        assertThat(sut.lastStartFailed).isTrue()
    }

    @Test
    fun `a failing onStart is put in front of the user at the alarm tier`() = runBlocking {
        val sut = plugin()
        sut.failStart = true

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(notifications.posted).hasSize(1)
        val posted = notifications.posted.single()
        assertThat(posted.id).isEqualTo(NotificationId.PLUGIN_START_FAILED)
        // URGENT is the only tier that is allowed to make a sound, and a sound is only played when both hold.
        assertThat(posted.level).isEqualTo(NotificationLevel.URGENT)
        assertThat(posted.sound).isEqualTo(AlarmSound.ALARM)
    }

    /**
     * The point of the supervisor job: with a plain [kotlinx.coroutines.Job] the first failure cancelled the
     * scope for good, and every transition after it was a silent no-op.
     */
    @Test
    fun `the plugin can still be started after a failed start`() = runBlocking {
        val sut = plugin()
        sut.failStart = true
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        sut.failStart = false
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(sut.events).containsExactly("start", "stop", "start").inOrder()
    }

    /** A clean start takes back both the flag and the card, or the warning would never go away. */
    @Test
    fun `a clean start clears the failure`() = runBlocking {
        val sut = plugin()
        sut.failStart = true
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        sut.failStart = false
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        // Ends on a void-returning assertion on purpose: a `runBlocking` test whose last expression has a
        // value is not void, and JUnit 5 skips it without a word. `containsExactly` returns `Ordered`.
        assertThat(notifications.dismissed).containsExactly(NotificationId.PLUGIN_START_FAILED)
        assertThat(sut.lastStartFailed).isFalse()
    }

    /** Nothing is dismissed when there was no failure, so a plugin cannot clear another one's card. */
    @Test
    fun `a clean start dismisses nothing when nothing had failed`() = runBlocking {
        val sut = plugin()

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(notifications.dismissed).isEmpty()
    }

    /**
     * A stop that throws is survived too, but it raises no alarm and sets no flag: the plugin is on its way
     * out, and [PluginBase.lastStartFailed] answers a question about starting.
     */
    @Test
    fun `a failing onStop is survived quietly`() = runBlocking {
        val sut = plugin()
        sut.failStop = true
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }

        assertThat(sut.events).containsExactly("start", "stop").inOrder()
        assertThat(sut.lastStartFailed).isFalse()
        assertThat(notifications.posted).isEmpty()
    }

    /**
     * `ConfigBuilderImpl.processOnEnabledCategoryChanged` disables the others and enables one in the same
     * pass, and nothing else orders them. A stop must not run while the start it undoes is still going.
     */
    @Test
    fun `a stop cannot overtake the start it undoes`() = runBlocking {
        val sut = plugin()
        val gate = CompletableDeferred<Unit>()
        sut.startGate = gate

        val startJob = sut.setPluginEnabled(PluginType.GENERAL, true)
        val stopJob = sut.setPluginEnabled(PluginType.GENERAL, false)
        delay(200)
        assertThat(sut.events).isEmpty()   // the start is still held, so the stop must be waiting too

        gate.complete(Unit)
        withTimeout(5.seconds) {
            startJob!!.join()
            stopJob!!.join()
        }

        assertThat(sut.events).containsExactly("start", "stop").inOrder()
    }

    /**
     * The other half of the same problem, and the reason [PluginBase.pluginScope] carries a supervisor job:
     * with a plain job the first failing child cancelled the scope for good, and every later `launch` on it
     * was a no-op that said nothing. The drivers launch their polling and their queue work there.
     *
     * The failing child prints a stack trace to stderr - that is the default handler doing its job, not the
     * test failing.
     */
    @Test
    fun `work the plugin launched that fails does not kill the plugin scope`() = runBlocking {
        val sut = plugin()
        withTimeout(5.seconds) { sut.launchOwnWork { throw IllegalStateException("child boom") }.join() }

        var ran = false
        withTimeout(5.seconds) { sut.launchOwnWork { ran = true }.join() }

        assertThat(ran).isTrue()
    }

    /**
     * The test-only entry point is deliberately not routed through the catch: about thirty unit tests make
     * `onStart` throw on purpose and assert on it, and they must keep seeing the throw.
     */
    @Test
    fun `the blocking form still lets the throw out`() {
        val sut = plugin()
        sut.failStart = true

        assertThrows<IllegalStateException> { sut.setPluginEnabledBlocking(PluginType.GENERAL, true) }
        assertThat(notifications.posted).isEmpty()
    }
}
