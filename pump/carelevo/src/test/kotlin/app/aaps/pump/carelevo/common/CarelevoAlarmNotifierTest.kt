package app.aaps.pump.carelevo.common

import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Robolectric unit tests for [CarelevoAlarmNotifier].
 *
 * [CarelevoAlarmNotifier] takes a real [Context] and calls into the Android framework directly —
 * `context.getString(...)`, `PendingIntent.getActivity(...)`, `NotificationCompat.Builder(...)`,
 * `HtmlCompat.fromHtml(...)`, `ProcessLifecycleOwner`, and the system [AndroidNotificationManager]
 * (via `getSystemService`). Under a plain JVM Mockito test those calls returned defaulted/null
 * values (e.g. `getString` → null → NPE), so this suite runs under [RobolectricTestRunner] with a
 * REAL application [Context]: string formatting, HTML parsing, PendingIntents and system
 * notifications all execute for real.
 *
 * Only the non-Android collaborators are mocked ([aapsLogger], [aapsSchedulers], [dateUtil], the
 * AAPS [NotificationManager] interface, [sp], [alarmActionHandler]). Assertions are made against:
 *  - the mocked AAPS [NotificationManager] (the in-app "top notification" cards),
 *  - the [CarelevoAlarmNotifier.alarms] StateFlow and the `onAlarmsUpdated` callback,
 *  - Robolectric's [ShadowNotificationManager] for the system-tray notifications and channel.
 *
 * The schedulers are stubbed to [Schedulers.trampoline] so every Rx emission is delivered
 * synchronously on the test thread, keeping the tests deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoAlarmNotifierTest {

    // REAL application context (getString / PendingIntent / NotificationCompat all work).
    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var dateUtil: DateUtil
    private lateinit var notificationManager: NotificationManager
    private lateinit var sp: SP
    private lateinit var alarmActionHandler: CarelevoAlarmActionHandler

    private lateinit var sut: CarelevoAlarmNotifier

    // Must match the private channelId in the SUT.
    private val channelId = "carelevo_alarm_channel"

    private fun alarm(
        cause: AlarmCause,
        value: Int? = null,
        id: String = "alarm-1"
    ): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = id,
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = "2026-07-16T10:00:00",
            updatedAt = "2026-07-16T10:00:00",
            isAcknowledged = false
        )

    @Before
    fun setUp() {
        aapsLogger = mock()
        aapsSchedulers = mock()
        dateUtil = mock()
        notificationManager = mock()
        sp = mock()
        alarmActionHandler = mock()

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(dateUtil.now()).thenReturn(1_000_000L)

        sut = CarelevoAlarmNotifier(
            context = context,
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            dateUtil = dateUtil,
            notificationManager = notificationManager,
            sp = sp,
            alarmActionHandler = alarmActionHandler
        )
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Verify one post() to the mocked AAPS NotificationManager (the 8-arg date/validTo overload). */
    private fun verifyPosted(level: NotificationLevel, critical: Boolean, count: Int = 1) {
        if (critical) {
            verify(notificationManager, times(count)).post(
                eq(NotificationId.CARELEVO_PATCH_ALERT), any<String>(), eq(level),
                any<Long>(), any<Long>(), eq(CoreUiR.raw.error), any<List<NotificationAction>>(), anyOrNull()
            )
        } else {
            verify(notificationManager, times(count)).post(
                eq(NotificationId.CARELEVO_PATCH_ALERT), any<String>(), eq(level),
                any<Long>(), any<Long>(), isNull(), any<List<NotificationAction>>(), anyOrNull()
            )
        }
    }

    /** Capture the `text` argument of the single post() to the AAPS NotificationManager. */
    private fun capturePostedText(): String {
        val textCaptor = argumentCaptor<String>()
        verify(notificationManager).post(
            any(), textCaptor.capture(), any(), any<Long>(), any<Long>(), anyOrNull(),
            any<List<NotificationAction>>(), anyOrNull()
        )
        return textCaptor.firstValue
    }

    private fun systemNmShadow(): ShadowNotificationManager =
        Shadows.shadowOf(context.getSystemService(AndroidNotificationManager::class.java))

    /** Create the system notification channel (needed before posting system-tray notifications). */
    private fun createChannel() {
        whenever(alarmActionHandler.observeAlarms()).thenReturn(Observable.empty<List<CarelevoAlarmInfo>>())
        sut.startObserving { }
    }

    // ---- simple state / accessors -------------------------------------------------------------

    @Test
    fun `alarms state flow starts empty`() {
        assertThat(sut.alarms.value).isEmpty()
    }

    @Test
    fun `alarmHostActive defaults to false and is settable`() {
        assertThat(sut.alarmHostActive).isFalse()
        sut.alarmHostActive = true
        assertThat(sut.alarmHostActive).isTrue()
        sut.alarmHostActive = false
        assertThat(sut.alarmHostActive).isFalse()
    }

    @Test
    fun `isInForeground can be read without crashing`() {
        // ProcessLifecycleOwner resolves under Robolectric; the exact foreground state depends on the
        // Robolectric process-lifecycle setup, so only assert the accessor returns a Boolean, no throw.
        assertThat(listOf(true, false)).contains(sut.isInForeground)
    }

    // ---- showTopNotification (in-app AAPS cards) ----------------------------------------------

    @Test
    fun `showTopNotification with an empty list only dismisses the previous cards`() {
        sut.showTopNotification(emptyList())

        verify(notificationManager).dismiss(eq(NotificationId.CARELEVO_PATCH_ALERT))
        verify(notificationManager, never()).post(
            any(), any<String>(), any(), any<Long>(), any<Long>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )
    }

    @Test
    fun `showTopNotification dismisses previous cards before re-posting`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START)))

        verify(notificationManager).dismiss(eq(NotificationId.CARELEVO_PATCH_ALERT))
    }

    @Test
    fun `showTopNotification for a critical WARNING posts URGENT with the alarm sound`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)))

        verifyPosted(NotificationLevel.URGENT, critical = true)
    }

    @Test
    fun `showTopNotification for a critical ALERT posts URGENT with the alarm sound`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)))

        verifyPosted(NotificationLevel.URGENT, critical = true)
    }

    @Test
    fun `showTopNotification for a non-critical NOTICE posts NORMAL and silent`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START)))

        verifyPosted(NotificationLevel.NORMAL, critical = false)
    }

    @Test
    fun `showTopNotification posts one card per alarm`() {
        sut.showTopNotification(
            listOf(
                alarm(AlarmCause.ALARM_NOTICE_LGS_START, id = "a"),
                alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "b")
            )
        )

        verify(notificationManager).dismiss(eq(NotificationId.CARELEVO_PATCH_ALERT))
        verify(notificationManager, times(2)).post(
            eq(NotificationId.CARELEVO_PATCH_ALERT), any<String>(), any(),
            any<Long>(), any<Long>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )
    }

    @Test
    fun `showTopNotification builds a non-blank card text from real resources`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START)))

        // With a real Context, getString + HtmlCompat resolve to actual text (no more NPE / "Stub!").
        assertThat(capturePostedText()).isNotEmpty()
    }

    @Test
    fun `showTopNotification action clears the alarm through the action handler`() {
        val info = alarm(AlarmCause.ALARM_NOTICE_LGS_START)
        sut.showTopNotification(listOf(info))

        val actionsCaptor = argumentCaptor<List<NotificationAction>>()
        verify(notificationManager).post(
            any(), any<String>(), any(), any<Long>(), any<Long>(), anyOrNull(), actionsCaptor.capture(), anyOrNull()
        )
        actionsCaptor.firstValue.first().action.invoke()

        verify(alarmActionHandler).triggerEvent(any<AlarmEvent.ClearAlarm>())
    }

    @Test
    fun `showTopNotification for low-insulin notice reads the low-insulin reminder preference`() {
        whenever(sp.getInt(eq(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key), any()))
            .thenReturn(25)

        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN)))

        verify(sp).getInt(eq(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key), eq(30))
        verifyPosted(NotificationLevel.NORMAL, critical = false)
    }

    @Test
    fun `showTopNotification for out-of-insulin alert reads the low-insulin reminder preference`() {
        whenever(sp.getInt(eq(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key), any()))
            .thenReturn(25)

        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)))

        verify(sp).getInt(eq(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key), eq(30))
        verifyPosted(NotificationLevel.URGENT, critical = true)
    }

    @Test
    fun `showTopNotification for patch-expired notice reads the patch-expiration preference and formats text`() {
        whenever(sp.getInt(eq(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key), any()))
            .thenReturn(50)

        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED)))

        // 50h -> 2 days 2 hours split fed into the description template; the formatted text is real.
        verify(sp).getInt(eq(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key), eq(116))
        val text = capturePostedText()
        assertThat(text).isNotEmpty()
        // Locale-independent: the "%s days %s hours" template embeds the digits 2 and 2.
        assertThat(text).contains("2")
    }

    @Test
    fun `showTopNotification for bg-check notice formats hours and minutes`() {
        // 125 min -> 2 hrs 5 min branch of formatBgCheckDuration; must not throw against real resources.
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 125)))

        val text = capturePostedText()
        assertThat(text).contains("2")
        assertThat(text).contains("5")
        verifyPosted(NotificationLevel.NORMAL, critical = false)
    }

    @Test
    fun `showTopNotification for bg-check notice formats whole hours`() {
        // 120 min -> whole-hours branch (2 hrs).
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 120)))

        assertThat(capturePostedText()).contains("2")
        verifyPosted(NotificationLevel.NORMAL, critical = false)
    }

    @Test
    fun `showTopNotification for bg-check notice formats minutes only`() {
        // 45 min -> minutes-only branch.
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 45)))

        assertThat(capturePostedText()).contains("45")
        verifyPosted(NotificationLevel.NORMAL, critical = false)
    }

    @Test
    fun `showTopNotification for a cause with no description args posts without reading preferences`() {
        sut.showTopNotification(listOf(alarm(AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR)))

        verify(sp, never()).getInt(any<String>(), any())
        verifyPosted(NotificationLevel.URGENT, critical = true)
    }

    // ---- startObserving / stopObserving / channel ---------------------------------------------

    @Test
    fun `startObserving creates the system notification channel`() {
        whenever(alarmActionHandler.observeAlarms()).thenReturn(Observable.empty<List<CarelevoAlarmInfo>>())

        sut.startObserving { }

        val realNm = context.getSystemService(AndroidNotificationManager::class.java)
        assertThat(realNm.getNotificationChannel(channelId)).isNotNull()
    }

    @Test
    fun `startObserving forwards emitted alarms to the callback and the state flow`() {
        val emitted = listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START))
        whenever(alarmActionHandler.observeAlarms()).thenReturn(Observable.just(emitted))
        var received: List<CarelevoAlarmInfo>? = null

        sut.startObserving { received = it }

        assertThat(received).isEqualTo(emitted)
        assertThat(sut.alarms.value).isEqualTo(emitted)
    }

    @Test
    fun `startObserving logs and does not crash when the alarm stream errors`() {
        whenever(alarmActionHandler.observeAlarms())
            .thenReturn(Observable.error<List<CarelevoAlarmInfo>>(RuntimeException("boom")))
        var callbackInvoked = false

        sut.startObserving { callbackInvoked = true }

        assertThat(callbackInvoked).isFalse()
        assertThat(sut.alarms.value).isEmpty()
        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
    }

    @Test
    fun `stopObserving disposes the subscription so later emissions are ignored`() {
        val subject = PublishSubject.create<List<CarelevoAlarmInfo>>()
        whenever(alarmActionHandler.observeAlarms()).thenReturn(subject)
        var callbackCount = 0

        sut.startObserving { callbackCount++ }
        subject.onNext(emptyList())
        assertThat(callbackCount).isEqualTo(1)

        sut.stopObserving()
        subject.onNext(emptyList())

        // No further delivery after disposal.
        assertThat(callbackCount).isEqualTo(1)
    }

    // ---- refreshAlarms ------------------------------------------------------------------------

    @Test
    fun `refreshAlarms updates the state flow from a one-shot load`() {
        val loaded = listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START))
        whenever(alarmActionHandler.getAlarmsOnce()).thenReturn(Single.just(loaded))

        sut.refreshAlarms()

        assertThat(sut.alarms.value).isEqualTo(loaded)
    }

    @Test
    fun `refreshAlarms logs and does not crash when the load errors`() {
        whenever(alarmActionHandler.getAlarmsOnce())
            .thenReturn(Single.error<List<CarelevoAlarmInfo>>(RuntimeException("boom")))

        sut.refreshAlarms()

        assertThat(sut.alarms.value).isEmpty()
        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
    }

    // ---- showNotification (system-tray, asserted via ShadowNotificationManager) ---------------

    @Test
    fun `showNotification posts a system-tray notification`() {
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }

    @Test
    fun `showNotification for out-of-insulin embeds the remaining amount without crashing`() {
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, value = 15))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }

    @Test
    fun `showNotification for patch-expired splits days and hours without crashing`() {
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED, value = 50))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }

    @Test
    fun `showNotification for bg-check hours and minutes without crashing`() {
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 125))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }

    @Test
    fun `showNotification for bg-check under an hour formats minutes only without crashing`() {
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 45))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }

    @Test
    fun `showNotification for a cause without a description still builds a notification`() {
        // ALARM_NOTICE_ATTACH_PATCH_CHECK has a null notification description -> empty body, title only.
        createChannel()

        sut.showNotification(alarm(AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK))

        assertThat(systemNmShadow().allNotifications).isNotEmpty()
    }
}
