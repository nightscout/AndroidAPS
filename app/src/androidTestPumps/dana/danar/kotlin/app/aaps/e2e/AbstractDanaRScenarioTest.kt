package app.aaps.e2e

import android.os.SystemClock
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.danar.di.DanaRAccessors
import app.aaps.pump.danar.emulator.DanaRPumpEmulator
import app.aaps.pump.danar.emulator.DanaRVariant
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The scenarios of the three DanaR drivers, on top of the ones every pump runs: the user picks the wrong DanaR driver,
 * sets a temp basal or an extended bolus on the pump itself, the pump history of every record type, a pump clock far
 * off, the daily insulin limit, and the user options. Each step is checked against what the emulated pump did.
 */
abstract class AbstractDanaRScenarioTest : AbstractPumpScenarioTest() {

    /** The driver a user could pick by mistake for this pump. */
    protected abstract val wrongDriver: String

    /** The driver the wrong one must switch to. */
    protected abstract val driverAfterTheSwitch: String

    /** The driver imports the events of the pump history. Only the DanaR v2 does. */
    protected open val importsPumpEvents: Boolean = false

    /** A pump clock off by more than 1.5 hours raises an alarm. Only the DanaR v2 does that, the others set the clock. */
    protected open val alarmsOnAFarClock: Boolean = false

    /** The driver writes the user options to the pump. The Korean DanaR driver does not. */
    protected open val writesUserOptions: Boolean = true

    private val accessors get() = testGraphs.rootGraph as DanaRAccessors
    private val emulator: DanaRPumpEmulator get() = (scenario.control as EmulatorRfcommTransport).emulator
    private val activePump get() = testGraphs.activePlugin.activePumpInternal

    private fun plugin(name: String): PluginBase =
        listOf<PluginBase>(accessors.danaRPlugin, accessors.danaRKoreanPlugin, accessors.danaRv2Plugin).first { it.javaClass.simpleName == name }

    /**
     * The user picks the wrong DanaR driver for the pump. At its next connection the driver finds the model, tells the
     * user, and switches to the right driver, which then works.
     */
    @Test
    fun aWrongDanaRDriverSwitchesToTheRightOne() {
        scenario.bringUp()
        scenario.pair()
        testGraphs.configBuilder.performPluginSwitch(plugin(wrongDriver), true, PluginType.PUMP)
        scenario.awaitTrue("$wrongDriver is the active pump") { activePump.javaClass.simpleName == wrongDriver }
        // The wrong driver connects, finds the model and switches. The read itself may fail with the switch.
        scenario.readStatus()
        scenario.awaitTrue("the driver switched to $driverAfterTheSwitch and is ready", SWITCH_TIMEOUT) {
            activePump.javaClass.simpleName == driverAfterTheSwitch && activePump.isInitialized()
        }
        scenario.checkAlarm(NotificationId.WRONG_DRIVER)
        scenario.awaitQueueIdle(PumpScenario.QUEUE_END_TIMEOUT)
        assertWithMessage("bolus with the right driver").that(scenario.bolus(BOLUS).success).isTrue()
        scenario.checkBolusRecords()
    }

    /**
     * The user changes a temp basal and an extended bolus of AAPS on the pump itself, and sets and ends others there.
     * AAPS must follow the pump: the plain and the Korean driver compare the status of the pump with AAPS, the DanaR v2
     * reads the pump history.
     */
    @Test
    fun aTempBasalAndAnExtendedBolusOnThePumpAreFollowed() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("temp basal").that(scenario.tempBasalPercent(TEMP_PERCENT, TEMP_MINUTES).success).isTrue()
        userGoesToThePump()
        onThePump("another temp basal") { emulator.tempBasalOnPump(OTHER_TEMP_PERCENT, TEMP_HOURS) }
        scenario.checkTempBasal()
        onThePump("the end of the temp basal") { emulator.stopTempBasalOnPump() }
        scenario.checkTempBasal()
        onThePump("a temp basal") { emulator.tempBasalOnPump(TEMP_PERCENT, TEMP_HOURS) }
        scenario.checkTempBasal()
        onThePump("the end of the temp basal") { emulator.stopTempBasalOnPump() }
        scenario.checkTempBasal()

        assertWithMessage("extended bolus").that(scenario.extendedBolus(EXTENDED, EXTENDED_MINUTES).success).isTrue()
        userGoesToThePump()
        onThePump("another extended bolus") { emulator.stopExtendedBolusOnPump() && emulator.extendedBolusOnPump(OTHER_EXTENDED, EXTENDED_HALF_HOURS) }
        scenario.checkExtendedBolus()
        onThePump("the end of the extended bolus") { emulator.stopExtendedBolusOnPump() }
        scenario.checkExtendedBolus()
        onThePump("an extended bolus") { emulator.extendedBolusOnPump(EXTENDED, EXTENDED_HALF_HOURS) }
        scenario.checkExtendedBolus()
        onThePump("the end of the extended bolus") { emulator.stopExtendedBolusOnPump() }
        scenario.checkExtendedBolus()
    }

    /**
     * The user gives a dual bolus and enters carbs on the pump, and changes the reservoir. The DanaR v2 driver imports
     * them from the pump history: the bolus, the extended part and its end, and the carbs. A refill, a prime and a
     * profile change on the pump make no record, but the status then shows the new reservoir.
     */
    @Test
    fun eventsMadeOnThePumpAreImported() {
        scenario.bringUp()
        assumeTrue("Only the DanaR v2 driver imports the pump events", importsPumpEvents)
        scenario.pair()
        onThePump("a dual bolus") { emulator.dualBolusOnPump(now = DUAL_NOW, extended = EXTENDED, halfHours = EXTENDED_HALF_HOURS) }
        scenario.checkBolusRecords()
        scenario.checkExtendedBolus()
        onThePump("the end of the dual bolus") { emulator.stopExtendedBolusOnPump() }
        scenario.checkExtendedBolus()

        onThePump("carbs, a refill, a prime and a profile change") {
            emulator.carbsOnPump(CARBS) && emulator.refillOnPump(REFILL) && emulator.primeOnPump(PRIME) && emulator.profileChangeOnPump(0)
        }
        val carbs = runBlocking { testGraphs.persistenceLayer.getCarbsFromTime(scenario.startedAt, true) }.map { it.amount }
        assertWithMessage("carbs entered on the pump").that(carbs).containsExactly(CARBS.toDouble())
        scenario.checkStatusShown()
        scenario.checkBolusRecords()
    }

    /**
     * The pump history screen reads each record type from the pump into the Dana history of AAPS. The daily totals reach
     * AAPS too. A Korean DanaR logs a bolus with a letter and sends no "done" at the end, as a real one did.
     */
    @Test
    fun thePumpHistoryOfEveryTypeIsRead() {
        scenario.bringUp()
        scenario.pair()
        // AAPS takes no pump record older than the time it first saw the pump, and a daily total has the time of the start
        // of its day. A pump in use for days, not one paired a minute ago.
        testGraphs.preferences.put(StringNonKey.ActivePumpType, activePump.model().description)
        testGraphs.preferences.put(StringNonKey.ActivePumpSerialNumber, activePump.serialNumber())
        testGraphs.preferences.put(LongNonKey.ActivePumpChangeTimestamp, System.currentTimeMillis() - 2 * DAY_MS)
        val korean = emulator.state.variant == DanaRVariant.DANA_R_KOREAN
        // Whole minutes: the bolus record has no seconds.
        val at = (System.currentTimeMillis() - HISTORY_AGE_MS) / MINUTE_MS * MINUTE_MS
        val history = emulator.state.reviewHistoryStore
        fun seed(type: Byte, minute: Int, value: Int, subCode: Int) = history.addEvent(type.toInt(), at + minute * MINUTE_MS, value, subCode)
        seed(RecordTypes.RECORD_TYPE_BOLUS, 0, BOLUS_HUNDREDTHS, if (korean) KOREAN_BOLUS else STANDARD_BOLUS)
        seed(RecordTypes.RECORD_TYPE_DAILY, 0, DAILY_BASAL_HUNDREDTHS, DAILY_BOLUS_HUNDREDTHS)
        seed(RecordTypes.RECORD_TYPE_GLUCOSE, 1, GLUCOSE, 0)
        ALARMS.keys.forEachIndexed { minute, letter -> seed(RecordTypes.RECORD_TYPE_ALARM, minute + 2, 0, letter.code) }
        seed(RecordTypes.RECORD_TYPE_ERROR, 7, 0, 0)
        seed(RecordTypes.RECORD_TYPE_CARBO, 8, CARBS, 0)
        seed(RecordTypes.RECORD_TYPE_REFILL, 9, REFILL_HUNDREDTHS, 0)
        seed(RecordTypes.RECORD_TYPE_SUSPEND, 10, 0, SUSPEND_ON)
        seed(RecordTypes.RECORD_TYPE_SUSPEND, 11, 0, 0)
        seed(RecordTypes.RECORD_TYPE_BASALHOUR, 12, BASAL_HOUR_HUNDREDTHS, 0)
        // A Korean history read ends only when the pump has been quiet for 10 s, so it reads two types.
        val types = if (korean) listOf(RecordTypes.RECORD_TYPE_BOLUS, RecordTypes.RECORD_TYPE_ALARM) else HISTORY_TYPES
        for (type in types) {
            assertWithMessage("history of type $type").that(runBlocking { scenario.commandQueue.loadHistory(type) }.success).isTrue()
            scenario.awaitQueueIdle(PumpScenario.QUEUE_END_TIMEOUT)
            assertWithMessage("records of type $type in AAPS").that(stored(type, at)).isNotEmpty()
        }
        assertWithMessage("the bolus").that(stored(RecordTypes.RECORD_TYPE_BOLUS, at).map { it.value }).contains(BOLUS_HUNDREDTHS / CENTI)
        assertWithMessage("the alarms").that(stored(RecordTypes.RECORD_TYPE_ALARM, at).map { it.alarm }).containsAtLeastElementsIn(ALARMS.values)
        if (korean) return
        assertWithMessage("the carbs").that(stored(RecordTypes.RECORD_TYPE_CARBO, at).map { it.value }).contains(CARBS.toDouble())
        assertWithMessage("the stops of the pump").that(stored(RecordTypes.RECORD_TYPE_SUSPEND, at).map { it.stringValue }).containsAtLeast("On", "Off")
        val total = (DAILY_BASAL_HUNDREDTHS + DAILY_BOLUS_HUNDREDTHS) / CENTI
        val tdds = runBlocking { testGraphs.persistenceLayer.getLastTotalDailyDoses(TDD_COUNT, false) }
        assertWithMessage("a daily total of $total U in AAPS, it has ${tdds.map { it.totalAmount }}").that(tdds.any { abs(it.totalAmount - total) < TOLERANCE }).isTrue()
    }

    /** A daily total is stored at the start of its day, the other records at their time. */
    private fun stored(type: Byte, from: Long) = runBlocking {
        accessors.danaHistoryRecordDao.allFromByType(if (type == RecordTypes.RECORD_TYPE_DAILY) from - DAY_MS else from, type)
    }

    /**
     * The pump clock is off by hours. The DanaR v2 driver raises an alarm and stops using the pump until the clock is
     * right. The other drivers set the pump clock when they read the settings, as after the Refresh button.
     */
    @Test
    fun aPumpClockFarOffIsHandled() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("the pump clock moves").that(scenario.control.shiftPumpClock(FAR_CLOCK_MS)).isTrue()
        if (alarmsOnAFarClock) {
            val since = System.currentTimeMillis()
            scenario.readStatusOnNewLink()
            scenario.checkUrgentAlarmSince(since)
            testGraphs.uiInteraction.stopAlarm("scenario end")
            assertWithMessage("the driver stops using the pump").that(activePump.isInitialized()).isFalse()
            // The user sets the pump clock.
            scenario.control.shiftPumpClock(-FAR_CLOCK_MS)
            scenario.readStatus()
            scenario.awaitTrue("the pump is ready again") { activePump.isInitialized() }
        } else {
            accessors.danaPump.readAllAgain()
            scenario.readStatusOnNewLink()
            val off = emulator.state.clockOffsetMs
            assertWithMessage("the pump clock is set, it is off by $off ms").that(abs(off)).isLessThan(CLOCK_SET_TOLERANCE_MS)
        }
    }

    /** Close to its daily insulin limit, the pump stops giving insulin. AAPS must warn the user before. */
    @Test
    fun theDailyInsulinLimitIsWarnedOf() {
        scenario.bringUp()
        scenario.pair()
        emulator.state.dailyTotalUnits = emulator.state.maxDailyTotalUnits * NEAR_THE_LIMIT
        scenario.readStatusOnNewLink()
        scenario.checkAlarm(NotificationId.APPROACHING_DAILY_LIMIT)
    }

    /** The user options the user changes in AAPS reach the pump, and the others stay as they were. */
    @Test
    fun userOptionsReachThePump() {
        scenario.bringUp()
        assumeTrue("The Korean DanaR driver writes no user options", writesUserOptions)
        scenario.pair()
        val before = emulator.state.glucoseUnit
        accessors.danaPump.apply {
            timeDisplayType24 = false
            beepAndAlarm = BEEP_AND_ALARM
            lcdOnTimeSec = LCD_ON_SECONDS
            shutdownHour = SHUTDOWN_HOUR
        }
        assertWithMessage("user options").that(runBlocking { scenario.commandQueue.setUserOptions() }.success).isTrue()
        scenario.awaitQueueIdle()
        val state = emulator.state
        assertWithMessage("12 h clock").that(state.timeDisplayType24).isFalse()
        assertWithMessage("beep and alarm").that(state.beepAndAlarm).isEqualTo(BEEP_AND_ALARM)
        assertWithMessage("screen on time").that(state.lcdOnTimeSec).isEqualTo(LCD_ON_SECONDS)
        assertWithMessage("shutdown hour").that(state.shutdownHour).isEqualTo(SHUTDOWN_HOUR)
        assertWithMessage("units, not changed").that(state.glucoseUnit).isEqualTo(before)
    }

    /**
     * The user takes the pump and goes through its menu, which takes more than a few seconds. The DanaR drivers take a
     * temp basal or an extended bolus that starts within 3 s of the one they know for the same one, as the start they
     * work out from the status can be a second off.
     */
    private fun userGoesToThePump() = SystemClock.sleep(USER_AT_THE_PUMP_MS)

    /** The user does [action] on the pump, and AAPS reads the pump at its next connection. */
    private fun onThePump(what: String, action: () -> Boolean) {
        assertWithMessage("on the pump: $what").that(action()).isTrue()
        scenario.readStatusOnNewLink()
    }

    private companion object {

        const val BOLUS = 0.5
        const val TEMP_PERCENT = 150
        const val OTHER_TEMP_PERCENT = 80
        const val TEMP_MINUTES = 60
        const val TEMP_HOURS = 1
        const val EXTENDED = 1.0
        const val OTHER_EXTENDED = 2.0
        const val EXTENDED_MINUTES = 30
        const val EXTENDED_HALF_HOURS = 1
        const val DUAL_NOW = 0.5
        const val USER_AT_THE_PUMP_MS = 5_000L
        const val CARBS = 20
        const val REFILL = 200.0
        const val PRIME = 0.3

        /** A driver switch connects again and reads the whole pump. */
        const val SWITCH_TIMEOUT = 90_000L

        const val MINUTE_MS = 60_000L
        const val HISTORY_AGE_MS = 2 * 60 * MINUTE_MS
        const val DAY_MS = 24 * 60 * MINUTE_MS
        const val CENTI = 100.0
        const val TOLERANCE = 0.01
        const val TDD_COUNT = 10
        const val BOLUS_HUNDREDTHS = 150
        /** The bolus type of an export DanaR: a standard bolus. */
        const val STANDARD_BOLUS = 0x80
        /** A Korean DanaR logs a bolus with the letter 'B'. */
        const val KOREAN_BOLUS = 0x42
        const val DAILY_BASAL_HUNDREDTHS = 1200
        const val DAILY_BOLUS_HUNDREDTHS = 800
        const val GLUCOSE = 120
        const val REFILL_HUNDREDTHS = 20000
        const val BASAL_HOUR_HUNDREDTHS = 50
        /** The letter 'O' of a stop that starts; any other is its end. */
        const val SUSPEND_ON = 79

        /** The alarm letters of a DanaR and what AAPS shows for them. */
        val ALARMS = mapOf('C' to "Check", 'O' to "Occlusion", 'B' to "Low Battery", 'S' to "Shutdown")
        val HISTORY_TYPES = listOf(
            RecordTypes.RECORD_TYPE_BOLUS, RecordTypes.RECORD_TYPE_DAILY, RecordTypes.RECORD_TYPE_GLUCOSE, RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_ERROR, RecordTypes.RECORD_TYPE_CARBO, RecordTypes.RECORD_TYPE_REFILL, RecordTypes.RECORD_TYPE_SUSPEND,
            RecordTypes.RECORD_TYPE_BASALHOUR
        )

        /** Two hours: more than the 1.5 hours over which the DanaR v2 driver raises its alarm. */
        const val FAR_CLOCK_MS = 2 * 60 * MINUTE_MS
        /** The Korean driver sets the clock 10 s ahead, as the pump takes whole minutes. */
        const val CLOCK_SET_TOLERANCE_MS = MINUTE_MS

        /** Over the 95 % at which the driver warns. */
        const val NEAR_THE_LIMIT = 0.97

        const val BEEP_AND_ALARM = 3
        const val LCD_ON_SECONDS = 30
        const val SHUTDOWN_HOUR = 6
    }
}
