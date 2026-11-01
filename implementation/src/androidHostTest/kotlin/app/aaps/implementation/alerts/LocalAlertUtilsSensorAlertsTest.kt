package app.aaps.implementation.alerts

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CgmSensorAlertProvider
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The CGM sensor alarms (`checkSensorAlerts`): one per sensor state, raised once per episode, only
 * for a BG source that reports its sensor state.
 */
class LocalAlertUtilsSensorAlertsTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var config: Config
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var notificationManager: NotificationManager

    private lateinit var sut: LocalAlertUtilsImpl
    private val now = 1_800_000_000_000L
    private val state = MutableStateFlow<CgmSensorAlertState>(CgmSensorAlertState.Ok)
    private val provider: BgSource = mock(extraInterfaces = arrayOf(CgmSensorAlertProvider::class))

    /** The episode keys really keep their value, like the stored preferences do. */
    private val stored = mutableMapOf<LocalAlertLongKey, Long>()

    private val leadHours = 12
    private val delayMinutes = 10

    /** A new instance is what an app restart gives: the stored episode keys stay, the memory is new. */
    private fun newSut() = LocalAlertUtilsImpl(
        aapsLogger, preferences, rh, activePlugin, profileFunction, profileUtil, smsCommunicator, config,
        persistenceLayer, dateUtil, notificationManager, CoroutineScope(Dispatchers.Unconfined)
    )

    @BeforeEach
    fun setup() {
        sut = newSut()
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.timeString(any<Long>())).thenReturn("t")
        whenever(dateUtil.dateAndTimeString(any<Long>())).thenReturn("d t")
        whenever((provider as CgmSensorAlertProvider).sensorAlertState).thenReturn(state)
        whenever(activePlugin.activeBgSource).thenReturn(provider)
        LocalAlertLongKey.entries.forEach { key ->
            whenever(preferences.get(key)).thenAnswer { stored[key] ?: 0L }
            whenever(preferences.put(eq(key), any<Long>())).thenAnswer { stored[key] = it.getArgument(1); Unit }
        }
        SWITCHES.forEach { whenever(preferences.get(it)).thenReturn(true) }
        whenever(preferences.get(IntKey.AlertCgmExpiringLeadHours)).thenReturn(leadHours)
        whenever(preferences.get(IntKey.AlertCgmSignalLostMinutes)).thenReturn(delayMinutes)
    }

    private fun verifyPosted(id: NotificationId, level: NotificationLevel, sound: AlarmSound?, times: Int = 1) {
        verify(notificationManager, times(times)).post(eq(id), any<TextRef>(), eq(level), any(), any(), any(), eq(sound), any(), anyOrNull())
    }

    private fun verifyNothingPosted() {
        verify(notificationManager, never()).post(any<NotificationId>(), any<TextRef>(), any<NotificationLevel>(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    private suspend fun check(newState: CgmSensorAlertState) {
        state.value = newState
        sut.checkSensorAlerts()
    }

    // region each state raises the right alarm

    @Test
    fun `a failed sensor is an urgent alarm with sound`() = runTest {
        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM)
    }

    @Test
    fun `an ended sensor is an urgent alarm with sound`() = runTest {
        check(CgmSensorAlertState.Expired(now - 1_000))
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRED, NotificationLevel.URGENT, AlarmSound.ALARM)
    }

    @Test
    fun `a sensor ending soon is important without sound`() = runTest {
        check(CgmSensorAlertState.Expiring(now + T.hours(1).msecs()))
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRING, NotificationLevel.IMPORTANT, null)
    }

    @Test
    fun `a lost signal is important without sound`() = runTest {
        check(CgmSensorAlertState.SignalLost(now - T.mins(20).msecs()))
        verifyPosted(NotificationId.CGM_SIGNAL_LOST, NotificationLevel.IMPORTANT, null)
    }

    @Test
    fun `warm-up done is normal without sound and ends after 30 min`() = runTest {
        val at = now - T.mins(1).msecs()
        check(CgmSensorAlertState.WarmupDone(at))
        val validTo = argumentCaptor<Long>()
        verify(notificationManager).post(
            eq(NotificationId.CGM_WARMUP_DONE), any<TextRef>(), eq(NotificationLevel.NORMAL), any(), any(), validTo.capture(), eq(null), any(), anyOrNull()
        )
        assertThat(validTo.firstValue).isEqualTo(at + T.mins(30).msecs())
    }

    @Test
    fun `a ready second sensor is normal without sound`() = runTest {
        check(CgmSensorAlertState.SecondSensorReady(now - 1_000))
        verifyPosted(NotificationId.CGM_SECOND_SENSOR_READY, NotificationLevel.NORMAL, null)
    }

    @Test
    fun `ok raises nothing`() = runTest {
        check(CgmSensorAlertState.Ok)
        verifyNothingPosted()
    }

    @Test
    fun `the notification ids have the agreed levels`() {
        assertThat(NotificationId.CGM_SENSOR_FAILED.defaultLevel).isEqualTo(NotificationLevel.URGENT)
        assertThat(NotificationId.CGM_SENSOR_EXPIRED.defaultLevel).isEqualTo(NotificationLevel.URGENT)
        assertThat(NotificationId.CGM_SENSOR_EXPIRING.defaultLevel).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(NotificationId.CGM_SIGNAL_LOST.defaultLevel).isEqualTo(NotificationLevel.IMPORTANT)
        assertThat(NotificationId.CGM_WARMUP_DONE.defaultLevel).isEqualTo(NotificationLevel.NORMAL)
        assertThat(NotificationId.CGM_SECOND_SENSOR_READY.defaultLevel).isEqualTo(NotificationLevel.NORMAL)
    }

    @Test
    fun `the six switches default to on and the two delays to 12 h and 10 min`() {
        SWITCHES.forEach { assertThat(it.defaultValue).isTrue() }
        assertThat(IntKey.AlertCgmExpiringLeadHours.defaultValue).isEqualTo(12)
        assertThat(IntKey.AlertCgmExpiringLeadHours.min).isEqualTo(1)
        assertThat(IntKey.AlertCgmExpiringLeadHours.max).isEqualTo(48)
        assertThat(IntKey.AlertCgmSignalLostMinutes.defaultValue).isEqualTo(10)
        assertThat(IntKey.AlertCgmSignalLostMinutes.min).isEqualTo(5)
        assertThat(IntKey.AlertCgmSignalLostMinutes.max).isEqualTo(60)
    }

    // endregion

    // region switches, episodes, clearing

    @Test
    fun `a switched off alarm raises nothing`() = runTest {
        whenever(preferences.get(BooleanKey.AlertCgmSensorFailed)).thenReturn(false)

        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))

        verifyNothingPosted()
    }

    @Test
    fun `an alarm is raised once per episode`() = runTest {
        val failed = CgmSensorAlertState.Expired(now - 1_000)
        check(failed)
        check(failed)
        check(failed)

        verifyPosted(NotificationId.CGM_SENSOR_EXPIRED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 1)
    }

    @Test
    fun `a new episode of the same state is raised again`() = runTest {
        check(CgmSensorAlertState.SignalLost(now - T.mins(30).msecs()))
        check(CgmSensorAlertState.SignalLost(now - T.mins(20).msecs()))

        verifyPosted(NotificationId.CGM_SIGNAL_LOST, NotificationLevel.IMPORTANT, null, times = 2)
    }

    @Test
    fun `an alarm is cleared when the state changes, and comes back with the next episode`() = runTest {
        check(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY))
        assertThat(stored[LocalAlertLongKey.CgmSensorFailedEpisode]).isNotEqualTo(0L)

        check(CgmSensorAlertState.Ok)

        verify(notificationManager).dismiss(NotificationId.CGM_SENSOR_FAILED)
        assertThat(stored[LocalAlertLongKey.CgmSensorFailedEpisode]).isEqualTo(0L)

        check(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY))
        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 2)
    }

    @Test
    fun `another failure reason is a new episode`() = runTest {
        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
        check(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER))

        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 2)
    }

    @Test
    fun `the warm-up message is not taken away by a change, it ends by itself`() = runTest {
        check(CgmSensorAlertState.WarmupDone(now - 1_000))
        check(CgmSensorAlertState.Ok)

        verify(notificationManager, never()).dismiss(NotificationId.CGM_WARMUP_DONE)
    }

    @Test
    fun `an old warm-up end is not news`() = runTest {
        check(CgmSensorAlertState.WarmupDone(now - T.mins(30).msecs()))
        verifyNothingPosted()
    }

    @Test
    fun `switching an alarm off takes it away`() = runTest {
        check(CgmSensorAlertState.Expired(now - 1_000))
        whenever(preferences.get(BooleanKey.AlertCgmSensorExpired)).thenReturn(false)

        check(CgmSensorAlertState.Expired(now - 1_000))

        verify(notificationManager).dismiss(NotificationId.CGM_SENSOR_EXPIRED)
    }

    /**
     * Mute all alarms and the Do Not Disturb override are applied by the notification manager to
     * every urgent alarm, like the missed readings alarm. What this check must do is not raise the
     * same alarm again after the user muted it.
     */
    @Test
    fun `a muted alarm is not raised again in the same episode`() = runTest {
        val failed = CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR)
        check(failed)
        notificationManager.muteAllAlarms()

        check(failed)

        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 1)
    }

    // endregion

    // region lead time and delay

    @Test
    fun `ending soon warns exactly at the lead time`() = runTest {
        check(CgmSensorAlertState.Expiring(now + T.hours(leadHours.toLong()).msecs()))
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRING, NotificationLevel.IMPORTANT, null)
    }

    @Test
    fun `ending soon does not warn before the lead time`() = runTest {
        check(CgmSensorAlertState.Expiring(now + T.hours(leadHours.toLong()).msecs() + 1))
        verifyNothingPosted()
    }

    @Test
    fun `a lost signal warns exactly at the delay`() = runTest {
        check(CgmSensorAlertState.SignalLost(now - T.mins(delayMinutes.toLong()).msecs()))
        verifyPosted(NotificationId.CGM_SIGNAL_LOST, NotificationLevel.IMPORTANT, null)
    }

    @Test
    fun `a lost signal does not warn before the delay`() = runTest {
        check(CgmSensorAlertState.SignalLost(now - T.mins(delayMinutes.toLong()).msecs() + 1))
        verifyNothingPosted()
    }

    /** A short drop out must not clear the other alarms and raise them again when the link is back. */
    @Test
    fun `a lost signal shorter than the delay changes nothing`() = runTest {
        val expiring = CgmSensorAlertState.Expiring(now + T.hours(1).msecs())
        check(expiring)
        check(CgmSensorAlertState.SignalLost(now - T.mins(1).msecs()))
        check(expiring)

        verify(notificationManager, never()).dismiss(NotificationId.CGM_SENSOR_EXPIRING)
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRING, NotificationLevel.IMPORTANT, null, times = 1)
    }

    /** After the link is back, "ending soon" must not be raised again for the same end. */
    @Test
    fun `a lost signal keeps the ending soon episode`() = runTest {
        val expiring = CgmSensorAlertState.Expiring(now + T.hours(1).msecs())
        check(expiring)
        check(CgmSensorAlertState.SignalLost(now - T.mins(30).msecs()))
        check(expiring)

        verifyPosted(NotificationId.CGM_SIGNAL_LOST, NotificationLevel.IMPORTANT, null, times = 1)
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRING, NotificationLevel.IMPORTANT, null, times = 1)
        verify(notificationManager, never()).dismiss(NotificationId.CGM_SENSOR_EXPIRING)
        assertThat(stored[LocalAlertLongKey.CgmSensorExpiringEpisode]).isEqualTo(expiring.endsAtEpochMs)
        // The link is back: the lost signal alarm goes away.
        verify(notificationManager).dismiss(NotificationId.CGM_SIGNAL_LOST)
    }

    @Test
    fun `a lost signal keeps a ready second sensor message`() = runTest {
        val ready = CgmSensorAlertState.SecondSensorReady(now - T.mins(5).msecs())
        check(ready)
        check(CgmSensorAlertState.SignalLost(now - T.mins(30).msecs()))
        check(ready)

        verifyPosted(NotificationId.CGM_SECOND_SENSOR_READY, NotificationLevel.NORMAL, null, times = 1)
    }

    @Test
    fun `a state other than a lost signal still clears ending soon`() = runTest {
        check(CgmSensorAlertState.Expiring(now + T.hours(1).msecs()))
        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))

        verify(notificationManager).dismiss(NotificationId.CGM_SENSOR_EXPIRING)
        assertThat(stored[LocalAlertLongKey.CgmSensorExpiringEpisode]).isEqualTo(0L)
    }

    // endregion

    // region app restart

    @Test
    fun `after a restart an urgent alarm still going on sounds once more, then not again`() = runTest {
        val expired = CgmSensorAlertState.Expired(now - 1_000)
        check(expired)

        sut = newSut()
        check(expired)
        check(expired)

        verifyPosted(NotificationId.CGM_SENSOR_EXPIRED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 2)
    }

    @Test
    fun `after a restart a failed sensor sounds once more`() = runTest {
        val failed = CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR)
        check(failed)

        sut = newSut()
        check(failed)
        check(failed)

        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 2)
    }

    @Test
    fun `after a restart a non urgent alarm comes back without sound`() = runTest {
        val expiring = CgmSensorAlertState.Expiring(now + T.hours(1).msecs())
        val lost = CgmSensorAlertState.SignalLost(now - T.mins(20).msecs())
        val ready = CgmSensorAlertState.SecondSensorReady(now - 1_000)
        check(expiring)
        sut = newSut()
        check(expiring)
        check(expiring)
        verifyPosted(NotificationId.CGM_SENSOR_EXPIRING, NotificationLevel.IMPORTANT, null, times = 2)

        check(lost)
        sut = newSut()
        check(lost)
        check(lost)
        verifyPosted(NotificationId.CGM_SIGNAL_LOST, NotificationLevel.IMPORTANT, null, times = 2)

        check(ready)
        sut = newSut()
        check(ready)
        check(ready)
        verifyPosted(NotificationId.CGM_SECOND_SENSOR_READY, NotificationLevel.NORMAL, null, times = 2)
    }

    @Test
    fun `a restart after the episode has ended raises nothing`() = runTest {
        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
        check(CgmSensorAlertState.Ok)

        sut = newSut()
        check(CgmSensorAlertState.Ok)

        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 1)
    }

    // endregion

    // region sources

    @Test
    fun `a source that does not report its sensor state gets nothing`() = runTest {
        whenever(activePlugin.activeBgSource).thenReturn(mock<BgSource>())

        sut.checkSensorAlerts()

        verifyNothingPosted()
    }

    @Test
    fun `a change of the BG source takes the sensor alarms away`() = runTest {
        check(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
        whenever(activePlugin.activeBgSource).thenReturn(mock<BgSource>())

        sut.checkSensorAlerts()

        verify(notificationManager).dismiss(NotificationId.CGM_SENSOR_FAILED)
        assertThat(stored[LocalAlertLongKey.CgmSensorFailedEpisode]).isEqualTo(0L)
    }

    @Test
    fun `a change to another reporting source starts over`() = runTest {
        val failed = CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR)
        check(failed)
        val other: BgSource = mock(extraInterfaces = arrayOf(CgmSensorAlertProvider::class))
        whenever((other as CgmSensorAlertProvider).sensorAlertState).thenReturn(MutableStateFlow(failed))
        whenever(activePlugin.activeBgSource).thenReturn(other)

        sut.checkSensorAlerts()

        verify(notificationManager).dismiss(NotificationId.CGM_SENSOR_FAILED)
        verifyPosted(NotificationId.CGM_SENSOR_FAILED, NotificationLevel.URGENT, AlarmSound.ALARM, times = 2)
    }

    // endregion

    private companion object {

        val SWITCHES = listOf(
            BooleanKey.AlertCgmSensorFailed,
            BooleanKey.AlertCgmSensorExpired,
            BooleanKey.AlertCgmSensorExpiring,
            BooleanKey.AlertCgmSignalLost,
            BooleanKey.AlertCgmWarmupDone,
            BooleanKey.AlertCgmSecondSensorReady
        )
    }
}
