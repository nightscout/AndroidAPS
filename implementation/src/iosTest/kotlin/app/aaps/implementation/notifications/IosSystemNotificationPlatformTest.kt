package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.alerts.IosAlertPlatform
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

        override fun play(sound: AlarmSound, ownerTag: String, breakThroughDnd: Boolean, ringtoneUri: String?) {
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
    // A swipe must not answer an alarm
    // ---------------------------------------------------------------------------------------------

    private fun notification(instanceKey: Int, withActions: Boolean) = AapsNotification(
        id = NotificationId.PUMP_ERROR,
        instanceKey = instanceKey,
        text = "test",
        level = NotificationLevel.URGENT,
        sound = AlarmSound.ALARM,
        actions = if (withActions) listOf(NotificationAction(TextRef.Literal("Snooze")) {}) else emptyList()
    )

    /**
     * The reason this guard exists.
     *
     * Every notification posted here is swipeable - the category must carry `customDismissAction` or
     * dismissals are never reported at all - and the registry turns a reported dismissal into
     * `dismiss(handle)`, which drops the notification so `refreshAlarmSound` stops the sound. On an
     * urgent Nightscout alarm that meant the swipe silenced it, threw away the card with the snooze
     * buttons, and never acknowledged Nightscout. Android forbids the gesture outright with
     * `setOngoing(true)`; iOS has no such flag, so it is refused here instead.
     */
    @Test
    fun `a swipe does not answer a notification carrying actions`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))

        assertFalse(platform.clearedByDismissal(7))
    }

    /** Nothing to lose when there is no action to lose, so the swipe means what it looks like. */
    @Test
    fun `a swipe clears a notification with no actions`() {
        platform.rememberIfUnanswered(notification(7, withActions = false))

        assertTrue(platform.clearedByDismissal(7))
    }

    /** An unknown key was never posted by this class, or was already dealt with. */
    @Test
    fun `a swipe clears a notification this class is not holding`() {
        assertTrue(platform.clearedByDismissal(99))
    }

    /** Answering it in the app cancels it, and then the guard has to let go. */
    @Test
    fun `once cancelled the guard releases the key`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))
        platform.forget(7)

        assertTrue(platform.clearedByDismissal(7))
    }

    /** "Mute all alarms" is a deliberate answer, unlike a swipe. */
    @Test
    fun `mute all releases every held key`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))
        platform.rememberIfUnanswered(notification(8, withActions = true))
        platform.forgetAll()

        assertTrue(platform.clearedByDismissal(7))
        assertTrue(platform.clearedByDismissal(8))
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
     * The booking prefix is read from the alert platform itself rather than written out here, so renaming
     * it cannot quietly re-open the hole this test exists for.
     */
    @Test
    fun `mute all leaves a booked automation reminder alone`() {
        val reminder = "${IosAlertPlatform.BOOKING_PREFIX}aaps/automation-timer"

        // The trap: a reminder still to come that "mute all" deletes never rings. The old reminder id
        // started with this class's own "aaps-" prefix; anything cruder than the key parse would hit it.
        assertEquals(listOf("aaps-42"), platform.ownIdentifiers(listOf("aaps-42", reminder, "aaps-reminder-3")))
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

    /** After a restart a card left from before goes; this run's own cards, a chime and a booking stay. */
    @Test
    fun `only the cards left from before are cleared at start`() {
        val delivered = listOf(platform.identifier(10_000), platform.identifier(10_001), "pump-alert-chime-bg/low", "alert-booking-bg/low", "aaps-loop")

        val cleared = platform.leftoverIdentifiers(delivered) { key -> key == 10_001 }

        assertEquals(listOf(platform.identifier(10_000)), cleared)
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

    // ---------------------------------------------------------------------------------------------
    // An alarm that arrives while the app is away
    // ---------------------------------------------------------------------------------------------

    /**
     * The gap these tests exist for.
     *
     * Every notification used to be posted silently, with the audio left to [AlarmSoundPlayer]. That
     * player needs the process to be running - `AVAudioSession` cannot start from a suspended app,
     * and AAPS has no `audio` background mode - so a CRITICAL alarm reaching a phone in a pocket
     * made no sound at all, which is the one case an alarm is for.
     */
    @Test
    fun `an alarm posted while the app is away carries its own sound`() {
        platform.onAppForeground(false)

        assertTrue(platform.audibleInBackground(notification(7, withActions = false)))
    }

    /** In front, the ramping player owns it, and two sounds for one alarm would be worse than one. */
    @Test
    fun `an alarm posted with a screen in front stays silent`() {
        platform.onAppForeground(true)

        assertFalse(platform.audibleInBackground(notification(7, withActions = false)))
    }

    /** Only an alarm. An ordinary notification is not promoted into one by the app being away. */
    @Test
    fun `an ordinary notification stays silent in the background`() {
        platform.onAppForeground(false)
        val plain = AapsNotification(
            id = NotificationId.PUMP_ERROR,
            instanceKey = 7,
            text = "test",
            level = NotificationLevel.NORMAL,
            sound = null
        )

        assertFalse(platform.audibleInBackground(plain))
    }

    /**
     * The other half of the same gap.
     *
     * `CommonNotificationManager.refreshAlarmSound` calls [IosSystemNotificationPlatform.setAudibleAlarm]
     * only when the top alarm changes, so an alarm whose audio was refused in the background was never
     * offered again. The user opened AAPS to a ringing alarm on screen and silence from the phone.
     */
    @Test
    fun `an alarm still ringing starts its audio when the app comes to the front`() {
        platform.onAppForeground(false)
        platform.setAudibleAlarm(7, AlarmSound.ALARM)
        alarmPlayer.calls.clear()

        platform.onAppForeground(true)

        assertEquals(listOf("play:ALARM"), alarmPlayer.calls)
    }

    /** Coming to the front with nothing ringing must not start a sound out of nowhere. */
    @Test
    fun `coming to the front with no alarm starts nothing`() {
        platform.onAppForeground(false)

        platform.onAppForeground(true)

        assertEquals(emptyList<String>(), alarmPlayer.calls)
    }

    /** An alarm that was silenced is not resurrected by the user opening the app. */
    @Test
    fun `an alarm that was silenced does not come back on the way in`() {
        platform.onAppForeground(false)
        platform.setAudibleAlarm(7, AlarmSound.ALARM)
        platform.setAudibleAlarm(null, null)
        alarmPlayer.calls.clear()

        platform.onAppForeground(true)

        assertEquals(emptyList<String>(), alarmPlayer.calls)
    }

    /**
     * `didBecomeActive` fires for far more than a real return - a system sheet closing, the control
     * centre - so only the transition starts the audio. Otherwise the ramp would restart from nothing
     * every time the user pulled the shade down, making a long alarm quieter the more they touched
     * the phone.
     */
    @Test
    fun `being told again that the app is in front does not restart the sound`() {
        platform.onAppForeground(true)
        platform.setAudibleAlarm(7, AlarmSound.ALARM)
        alarmPlayer.calls.clear()

        platform.onAppForeground(true)

        assertEquals(emptyList<String>(), alarmPlayer.calls)
    }
}
