package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.implementation.alerts.IosReminderScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The identifier round trip, which is the one piece of real logic in the iOS platform.
 *
 * Everything else here hands work to `UNUserNotificationCenter` and cannot be checked without a
 * person swiping a notification away. This part can: a dismissal arrives as the identifier string
 * that was posted, and turning it back into an instance key is what connects the two. Get it wrong
 * and dismissals are silently ignored, because the registry is asked to drop a key that never
 * existed.
 */
class IosSystemNotificationPlatformTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    /** Records what the alarm player was asked to do, in order. */
    private class RecordingAlarmPlayer : AlarmSoundPlayer {

        val calls = mutableListOf<String>()

        override fun play(sound: AlarmSound, ownerTag: String, postedAtElapsedRealtime: Long) {
            calls.add("play:${sound.name}")
        }

        override fun stop(ownerTag: String) {
            calls.add("stop")
        }
    }

    private val alarmPlayer = RecordingAlarmPlayer()

    /** What `AlertOverrideDoNotDisturb` says. Its own default is on. */
    private var overrideDnd = true
    private val platform = IosSystemNotificationPlatform(SilentLogger, alarmPlayer) { overrideDnd }

    @Test
    fun `an instance key survives the round trip`() {
        assertEquals(42, platform.instanceKeyOf(platform.identifier(42)))
    }

    /** Instance keys for the multi-instance ids start at 10000 and climb. */
    @Test
    fun `a large instance key survives the round trip`() {
        assertEquals(10_001, platform.instanceKeyOf(platform.identifier(10_001)))
    }

    /** Another app's notification, or one this class never posted, must not map to a key. */
    @Test
    fun `an identifier without our prefix is not ours`() {
        assertNull(platform.instanceKeyOf("42"))
        assertNull(platform.instanceKeyOf("other-app-42"))
    }

    /** The prefix alone, or a non-numeric tail, is not a key either. */
    @Test
    fun `a malformed identifier is not a key`() {
        assertNull(platform.instanceKeyOf("aaps-"))
        assertNull(platform.instanceKeyOf("aaps-abc"))
    }

    // ---------------------------------------------------------------------------------------------
    // Breaking through a Focus mode
    // ---------------------------------------------------------------------------------------------

    /**
     * The Focus half of `AlertOverrideDoNotDisturb`, which nothing read before.
     *
     * On iOS the one setting has to be answered in two places: the audio session category covers the
     * Ring/Silent switch, and the interruption level covers Focus. Only the second one lives here.
     */
    @Test
    fun `an urgent alarm breaks through Focus while the override is on`() {
        overrideDnd = true

        assertTrue(platform.breaksThroughFocus(NotificationLevel.URGENT))
    }

    @Test
    fun `an urgent alarm respects Focus once the override is turned off`() {
        overrideDnd = false

        assertFalse(platform.breaksThroughFocus(NotificationLevel.URGENT))
    }

    /** The override widens what an alarm may do; it does not promote ordinary notifications. */
    @Test
    fun `a non urgent notification never breaks through Focus`() {
        overrideDnd = true

        assertFalse(platform.breaksThroughFocus(NotificationLevel.NORMAL))
        assertFalse(platform.breaksThroughFocus(NotificationLevel.INFO))
    }

    // ---------------------------------------------------------------------------------------------
    // What "mute all alarms" is allowed to remove
    // ---------------------------------------------------------------------------------------------

    /**
     * The reminder prefix is read from the scheduler itself rather than written out here, so renaming
     * it cannot quietly re-open the hole this test exists for.
     */
    @Test
    fun `mute all leaves a scheduled automation reminder alone`() {
        val reminder = "${IosReminderScheduler.IDENTIFIER_PREFIX}3"

        // The trap: the reminder id starts with this class's own "aaps-" prefix, so anything cruder
        // than the key parse - a prefix match, or the removeAll* pair this used to call - deletes a
        // reminder the user is still waiting on, and it never rings.
        assertEquals(listOf("aaps-42"), platform.ownIdentifiers(listOf("aaps-42", reminder)))
    }

    /** `IosLoopNotifier.NOTIFICATION_ID`, spelled out because it lives in another module. */
    @Test
    fun `mute all leaves the loop notification alone`() {
        assertEquals(emptyList<String>(), platform.ownIdentifiers(listOf("aaps-loop")))
    }

    @Test
    fun `mute all removes the alarms this class posted`() {
        val ours = listOf(platform.identifier(1), platform.identifier(10_001))

        assertEquals(ours, platform.ownIdentifiers(ours))
    }

    @Test
    fun `mute all leaves another app's notification alone`() {
        assertEquals(emptyList<String>(), platform.ownIdentifiers(listOf("other-app-42", "42")))
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm audio
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an alarm owner starts the sound`() {
        platform.setAudibleAlarm(7, AlarmSound.ERROR)

        assertEquals(listOf("play:ERROR"), alarmPlayer.calls)
    }

    /**
     * The reason the owner key is tracked at all.
     *
     * The registry recomputes the owner after every change, so the same alarm is offered again and
     * again. Restarting the sound each time would reset the volume ramp and make an alarm quieter
     * the more the registry churns - the opposite of what a ramp is for.
     */
    @Test
    fun `the same owner offered twice does not restart the sound`() {
        platform.setAudibleAlarm(7, AlarmSound.ERROR)
        platform.setAudibleAlarm(7, AlarmSound.ERROR)

        assertEquals(listOf("play:ERROR"), alarmPlayer.calls)
    }

    @Test
    fun `a different owner takes the sound over`() {
        platform.setAudibleAlarm(7, AlarmSound.ERROR)
        platform.setAudibleAlarm(8, AlarmSound.ALARM)

        assertEquals(listOf("play:ERROR", "play:ALARM"), alarmPlayer.calls)
    }

    @Test
    fun `no owner silences the alarm`() {
        platform.setAudibleAlarm(7, AlarmSound.ERROR)
        platform.setAudibleAlarm(null, null)

        assertEquals(listOf("play:ERROR", "stop"), alarmPlayer.calls)
    }
}
