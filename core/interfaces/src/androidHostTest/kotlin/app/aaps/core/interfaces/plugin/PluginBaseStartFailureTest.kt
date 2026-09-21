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

        data class Posted(val id: NotificationId, val text: String, val level: NotificationLevel, val sound: AlarmSound?, val handle: NotificationHandle)

        val posted = mutableListOf<Posted>()
        val dismissed = mutableListOf<NotificationId>()
        val dismissedHandles = mutableListOf<NotificationHandle>()

        /** What is still on screen: posted, minus anything dismissed by id or by handle. */
        val live: List<Posted>
            get() = posted.filterNot { it.id in dismissed || it.handle in dismissedHandles }

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
        ): NotificationHandle = record(id, text, level, sound)

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = record(id, text, level, sound)

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
        ): NotificationHandle = record(id, "ref", level, sound)

        private var nextKey = 10_000

        /**
         * Mirrors `CommonNotificationManager.postInternal`, and the mirroring is the point: without
         * [NotificationId.allowMultiple] the real manager keys the card by `id.ordinal` and REPLACES any
         * card already carrying that id. A fake that always hands out a fresh handle would make this test
         * pass against the very bug it exists to catch.
         */
        private fun record(id: NotificationId, text: String, level: NotificationLevel, sound: AlarmSound?): NotificationHandle {
            val handle: NotificationHandle
            if (id.allowMultiple) {
                handle = NotificationHandle(nextKey++)
            } else {
                handle = NotificationHandle(id.ordinal)
                posted.removeAll { it.id == id }
            }
            posted += Posted(id, text, level, sound, handle)
            return handle
        }

        override fun dismiss(id: NotificationId) {
            dismissed += id
        }

        override fun dismiss(handle: NotificationHandle) {
            dismissedHandles += handle
        }

        override fun muteAllAlarms() {}
    }

    /**
     * A mock would hand back null for [TextResolver.gs], and the notification text may not be null.
     * The arguments are kept in the result so a test can tell which plugin a card is about.
     */
    private class FixedText : TextResolver {

        override fun gs(ref: TextRef): String = "text"
        override fun gs(ref: TextRef, vararg args: Any?): String = "failed: " + args.joinToString()
        override fun gsNotLocalised(ref: TextRef): String = "text"
        override fun shortTextMode(): Boolean = false
    }

    private class TestPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager,
        override val name: String = "Test plugin"
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

        /** [pluginScope] is protected, and this is what the drivers do with it. No handler here on purpose:
         * the one on [pluginScope] is what is being tested. */
        fun launchOwnWork(block: suspend () -> Unit): Job = pluginScope.launch { block() }
    }

    private val notifications = RecordingNotifications()
    private fun plugin(name: String = "Test plugin") = TestPlugin(mock<AAPSLogger>(), FixedText(), notifications, name)

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
        assertThat(notifications.live).isEmpty()
        assertThat(sut.lastStartFailed).isFalse()
    }

    /**
     * By handle, never by id. `dismiss(id)` removes every card carrying that id, and
     * [NotificationId.PLUGIN_START_FAILED] is shared by every plugin - see the two-plugin test below.
     */
    @Test
    fun `a clean start dismisses its own card by handle`() = runBlocking {
        val sut = plugin()
        sut.failStart = true
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }
        val card = notifications.posted.single().handle

        sut.failStart = false
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }
        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(notifications.dismissedHandles).containsExactly(card)
        assertThat(notifications.dismissed).isEmpty()
    }

    /** Nothing is dismissed when this plugin never failed. */
    @Test
    fun `a clean start dismisses nothing when nothing had failed`() = runBlocking {
        val sut = plugin()

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(notifications.dismissed).isEmpty()
        assertThat(notifications.dismissedHandles).isEmpty()
    }

    /**
     * The one that matters, and the one the first version of this file got wrong.
     *
     * `ConfigBuilderImpl` and `PluginStore` start every elected plugin in the same pass, so a systemic cause
     * - a revoked Bluetooth permission, say - takes down more than one. Each failure must keep its own card,
     * and a plugin that recovers must not take down the alarm of one that has not.
     */
    @Test
    fun `two failed plugins keep separate cards and one recovering leaves the other's alone`() = runBlocking {
        val pump = plugin("Pump driver")
        val bgSource = plugin("BG source")
        pump.failStart = true
        bgSource.failStart = true

        withTimeout(5.seconds) { pump.setPluginEnabledAwaiting(PluginType.GENERAL, true) }
        withTimeout(5.seconds) { bgSource.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        // Two failures, two cards, each naming its own plugin.
        assertThat(notifications.live.map { it.text }).containsExactly("failed: Pump driver", "failed: BG source")

        // The BG source is fixed and restarted; the pump is still broken.
        bgSource.failStart = false
        withTimeout(5.seconds) { bgSource.setPluginEnabledAwaiting(PluginType.GENERAL, false) }
        withTimeout(5.seconds) { bgSource.setPluginEnabledAwaiting(PluginType.GENERAL, true) }

        assertThat(pump.lastStartFailed).isTrue()
        assertThat(bgSource.lastStartFailed).isFalse()
        // The pump's alarm survives. Losing it would leave a pump that refuses to dose and says nothing.
        // isEqualTo, not containsExactly: the latter returns Ordered, which would make JUnit skip this test.
        assertThat(notifications.live.single().text).isEqualTo("failed: Pump driver")
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
     * The other half: the scope surviving is not enough if the failure is silent.
     *
     * Without a handler on [PluginBase.pluginScope] the throw reaches the thread's default handler, which
     * on Android ends the process - and in a test JVM it is collected by kotlinx-coroutines-test and
     * reported against an unrelated `runTest` somewhere else in the module, which is how this file first
     * broke `ChunkedOnQuietPeriodTest` on CI.
     */
    @Test
    fun `work the plugin launched that fails is reported, not lost`() = runBlocking {
        val sut = plugin("Pump driver")

        withTimeout(5.seconds) { sut.launchOwnWork { throw IllegalStateException("child boom") }.join() }

        val card = notifications.live.single()
        assertThat(card.id).isEqualTo(NotificationId.PLUGIN_WORK_FAILED)
        assertThat(card.text).isEqualTo("failed: Pump driver")
        assertThat(card.level).isEqualTo(NotificationLevel.URGENT)
        assertThat(card.sound).isEqualTo(AlarmSound.ALARM)
        // It did NOT start badly - that is a different state, and the pump gate must not be tripped by this.
        assertThat(sut.lastStartFailed).isFalse()
    }

    /** Repeated failures replace this plugin's own card rather than piling up. */
    @Test
    fun `repeated launched-work failures leave one card`() = runBlocking {
        val sut = plugin()

        withTimeout(5.seconds) { sut.launchOwnWork { throw IllegalStateException("one") }.join() }
        withTimeout(5.seconds) { sut.launchOwnWork { throw IllegalStateException("two") }.join() }

        assertThat(notifications.live).hasSize(1)
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
