package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AlarmState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A notification replaces the one with the same id, and its sound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyServiceAlarmTest {

    private val rh = mock(ResourceHelper::class.java)
    private val notificationManager = mock(NotificationManager::class.java)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java), rh, mock(Preferences::class.java, RETURNS_SMART_NULLS), mock(DateUtil::class.java),
        mock(PumpSync::class.java), mock(TemporaryBasalStorage::class.java), mock(BolusProgressData::class.java)
    )
    private val service = AaruyService()

    @Before
    fun setUp() {
        whenever(rh.gs(any<Int>())).thenAnswer { "s${it.arguments[0]}" }
        whenever(rh.gs(any<Int>(), anyVararg<Any>())).thenAnswer { "s${it.arguments[0]}:${it.arguments.drop(1).joinToString()}" }
        service.rh = rh
        service.notificationManager = notificationManager
        service.aaruyPump = pump
        service.pumpSync = mock(PumpSync::class.java)
    }

    private class Posted(val id: NotificationId, val text: String, val level: NotificationLevel)

    private fun posted(): List<Posted> = mockingDetails(notificationManager).invocations
        .filter { it.method.name == "post" }
        .map { Posted(it.arguments[0] as NotificationId, it.arguments[1].toString(), it.arguments[2] as NotificationLevel) }

    private fun alarm(state: AlarmState) {
        // As the driver does: an error goes into the active alarms before it is announced.
        if (AlarmState.isHighAlarm(state) || AlarmState.isLowAlarm(state)) pump.addAlarm(state)
        runBlocking { service.notifyPumpWarning(state) }
    }

    /** An occlusion the user had not seen yet was removed by a later low battery, and so was its sound. */
    @Test
    fun aLaterErrorDoesNotRemoveAnEarlierOne() {
        alarm(AlarmState.PUMP_MOTOR_BLOCK)
        alarm(AlarmState.PUMP_LOW_POWER)
        val last = posted().last()
        assertEquals(NotificationId.PUMP_ERROR, last.id)
        assertTrue(last.text, last.text.contains(pump.alarmStateToString(AlarmState.PUMP_MOTOR_BLOCK)))
        assertTrue(last.text, last.text.contains(pump.alarmStateToString(AlarmState.PUMP_LOW_POWER)))
    }

    /** Seen on a real pump: it beeps once after 15 minutes stopped. A later reminder made the urgent alarm a quiet one. */
    @Test
    fun stoppedTooLongHasItsOwnNotification() {
        alarm(AlarmState.PUMP_STOP_TOO_LONG)
        val stopped = posted().single()
        assertEquals(NotificationId.PUMP_SUSPENDED, stopped.id)
        assertEquals(NotificationLevel.URGENT, stopped.level)
        val reminder = AlarmState.entries.first { AlarmState.isRemindAlarm(it) && it != AlarmState.PUMP_STOP_TOO_LONG }
        alarm(reminder)
        assertTrue("the reminder has another id", posted().last().id != NotificationId.PUMP_SUSPENDED)
    }
}
