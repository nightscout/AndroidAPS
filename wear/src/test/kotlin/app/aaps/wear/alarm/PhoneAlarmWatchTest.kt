package app.aaps.wear.alarm

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.impl.sharedPreferences.SPImpl
import app.aaps.shared.impl.sharedPreferences.defaultPreferences
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration

/**
 * The phone's alarm on the watch, when the phone's notification has buttons of its own. A Nightscout alarm came back a
 * minute after Silence on the watch, because Silence did not answer Nightscout: only its snooze buttons do. So the
 * watch shows those buttons, in the phone's order, and a swipe presses the first.
 *
 * A stop from the phone waits a moment before it takes the notification away, because the phone often posts the
 * same alarm again right after a stop (on every new BG). A cancel followed by a new post that fast made Samsung's
 * watch system swipe the new notification away, and that snoozed the alarm in Nightscout by itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class PhoneAlarmWatchTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val rxBus: RxBus = mock()
    private lateinit var sut: PhoneAlarmWatch

    private val snoozes = listOf("Snooze 15m", "Snooze 30m", "Snooze 60m")

    @Before
    fun setUp() {
        // The repeat is an exact alarm, which Android 12+ refuses unless the app may schedule them
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val sp = SPImpl(defaultPreferences(context), context)
        sut = PhoneAlarmWatch(context, sp, mock<Preferences>(), rxBus, AAPSLoggerTest())
    }

    private fun posted(): Notification = shadowOf(notificationManager).allNotifications.single()

    private fun ringing(actions: List<String>) =
        EventData.AlarmRinging(1L, "Urgent alarm", "Sensor age 8 days", canMute = false, alarmKey = 69, actions = actions)

    /** A stop sent after [ringing]: the phone stamps both with its clock. */
    private fun stopped() = EventData.AlarmStopped(2L)

    private fun sentToPhone(): EventData {
        val captor = argumentCaptor<Event>()
        verify(rxBus).send(captor.capture())
        return (captor.lastValue as EventWearToMobile).payload
    }

    @Test
    fun `the phone buttons replace Silence in the same order`() {
        sut.show(ringing(snoozes))

        assertThat(posted().actions.map { it.title.toString() }).containsExactlyElementsIn(snoozes).inOrder()
    }

    @Test
    fun `each button carries its own index`() {
        sut.show(ringing(snoozes))

        val intents = posted().actions.map { shadowOf(it.actionIntent).savedIntent }
        assertThat(intents.map { it.action }.distinct()).containsExactly(PhoneAlarmReceiver.ACTION_PHONE_BUTTON)
        assertThat(intents.map { it.getIntExtra(PhoneAlarmReceiver.EXTRA_INDEX, -1) }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `a swipe has its own intent and presses the first button`() {
        sut.show(ringing(snoozes))

        assertThat(shadowOf(posted().deleteIntent).savedIntent.action).isEqualTo(PhoneAlarmReceiver.ACTION_SWIPE)
        sut.swipe()

        val sent = sentToPhone() as EventData.AlarmAction
        assertThat(sent.alarmKey).isEqualTo(69)
        assertThat(sent.index).isEqualTo(0)
    }

    @Test
    fun `a swipe silences an alarm without buttons`() {
        sut.show(ringing(emptyList()))

        assertThat(shadowOf(posted().deleteIntent).savedIntent.action).isEqualTo(PhoneAlarmReceiver.ACTION_SWIPE)
        sut.swipe()

        assertThat(sentToPhone()).isInstanceOf(EventData.DismissAlert::class.java)
    }

    @Test
    fun `a swipe after the alarm is gone sends nothing to the phone`() {
        sut.show(ringing(snoozes))
        sut.clear()

        sut.swipe()

        verifyNoInteractions(rxBus)
    }

    @Test
    fun `a stop takes the notification away after the grace`() {
        sut.show(ringing(snoozes))

        sut.stop(stopped())
        assertThat(shadowOf(notificationManager).allNotifications).hasSize(1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `a new alarm within the grace replaces the notification instead of a cancel and a new post`() {
        sut.show(ringing(snoozes))
        sut.stop(stopped())

        sut.show(ringing(snoozes).copy(status = "Sensor age 9 days"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertThat(posted().extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Sensor age 9 days")
    }

    @Test
    fun `a stop older than the alarm shown is for an earlier alarm and is ignored`() {
        // The stop and the new alarm arrived together and ran in parallel: the new alarm came first
        sut.show(ringing(snoozes).copy(timeStamp = 10L))

        sut.stop(EventData.AlarmStopped(5L))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertThat(shadowOf(notificationManager).allNotifications).hasSize(1)
    }

    @Test
    fun `a repeat during the grace clears instead of posting again`() {
        sut.show(ringing(snoozes))
        sut.stop(stopped())

        sut.repeat()

        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `a pressed button goes to the phone with the alarm key and the notification goes away`() {
        sut.show(ringing(snoozes))

        sut.runAction(2)

        val sent = sentToPhone() as EventData.AlarmAction
        assertThat(sent.alarmKey).isEqualTo(69)
        assertThat(sent.index).isEqualTo(2)
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `a repeat keeps the buttons`() {
        sut.show(ringing(snoozes))
        notificationManager.cancelAll()

        sut.repeat()

        assertThat(posted().actions.map { it.title.toString() }).containsExactlyElementsIn(snoozes).inOrder()
    }

    @Test
    fun `a button text with a line break keeps its place on a repeat`() {
        val texts = listOf("Snooze\n15m", "", "Snooze 60m")
        sut.show(ringing(texts))
        notificationManager.cancelAll()

        sut.repeat()

        assertThat(posted().actions.map { it.title.toString() }).containsExactlyElementsIn(texts).inOrder()
    }

    @Test
    fun `a stop when nothing is shown changes nothing`() {
        sut.stop(stopped())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        verifyNoInteractions(rxBus)
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `an alarm without buttons keeps Silence`() {
        sut.show(ringing(emptyList()))

        assertThat(posted().actions.map { it.title.toString() }).containsExactly(context.getString(R.string.phone_alarm_silence))
        assertThat(shadowOf(posted().deleteIntent).savedIntent.action).isEqualTo(PhoneAlarmReceiver.ACTION_SWIPE)
    }

    @Test
    fun `the buttons are gone after the alarm is cleared`() {
        sut.show(ringing(snoozes))
        sut.clear()

        sut.show(ringing(emptyList()))

        assertThat(posted().actions).hasLength(1)
    }
}
