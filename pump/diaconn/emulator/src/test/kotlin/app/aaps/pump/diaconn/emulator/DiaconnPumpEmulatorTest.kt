package app.aaps.pump.diaconn.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedExtendedBolus
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The pump on its own, without the link: requests in, answers and reports out. The message codes and offsets are written
 * here again from the protocol, not taken from the emulator, so a wrong code in the emulator fails a test.
 */
class DiaconnPumpEmulatorTest {

    private lateinit var state: DiaconnPumpState
    private lateinit var emulator: DiaconnPumpEmulator
    private val reports = CopyOnWriteArrayList<ByteArray>()
    private var seq = 0

    @BeforeEach
    fun setUp() {
        // The real G8 whose answers these tests copy.
        state = DiaconnPumpState().apply { serialNumber = REAL_SERIAL }
        emulator = DiaconnPumpEmulator(state)
        emulator.speed = EmulatorSpeed.FAST
        emulator.reportSink = { report -> reports += report }
    }

    // --- Status ---

    @Test
    fun theMainInfoCarriesThePumpState() {
        state.basalRates[7] = 1.25
        val before = LocalDateTime.now().withNano(0)
        val answer = ask(BIG_MAIN_INFO)
        val after = LocalDateTime.now()

        assertThat(answer.size).isEqualTo(182)
        assertThat(answer.u8(0)).isEqualTo(0xED)
        assertThat(answer.u8(1)).isEqualTo(0xB3)
        assertThat(answer.u8(2)).isEqualTo(seq)
        assertThat(DiaconnPacket.crc(answer, 181)).isEqualTo(answer[181])
        assertThat(answer.u8(4)).isEqualTo(16)
        assertThat(answer.u16(5)).isEqualTo(20000)
        assertThat(answer.u8(7)).isEqualTo(90)
        assertThat(answer.u8(8)).isEqualTo(1)
        assertThat(answer.u8(9)).isEqualTo(2)
        assertThat(answer.u8(14)).isEqualTo(2)
        val clock = LocalDateTime.of(2000 + answer.u8(15), answer.u8(16), answer.u8(17), answer.u8(18), answer.u8(19), answer.u8(20))
        assertThat(clock).isAtLeast(before)
        assertThat(clock).isAtMost(after)
        assertThat(answer.u8(21)).isEqualTo('1'.code)
        assertThat(answer.u16(27)).isEqualTo(64004)
        assertThat(answer.u8(29)).isEqualTo(2)
        assertThat(answer.u8(30)).isEqualTo(50)
        assertThat(answer.u16(31)).isEqualTo(0)
        assertThat(answer.u8(34)).isEqualTo(5)
        assertThat(answer.u8(35)).isEqualTo(2)
        assertThat(answer.u8(57)).isEqualTo(5)
        assertThat(answer.u8(58)).isEqualTo(2)
        assertThat(answer.u8(64)).isEqualTo(2)
        assertThat(answer.u8(73)).isEqualTo(2)
        assertThat((0 until 24).map { answer.u16(121 + 2 * it) }).containsExactlyElementsIn(List(24) { if (it == 7) 125 else 100 }).inOrder()
        assertThat(answer.drop(169).take(12).all { it == 0xFF.toByte() }).isTrue()
    }

    @Test
    fun theApsMainInfoHasTheLimitsAndSettingsAtTheEnd() {
        state.firmwareMajor = 3
        state.firmwareMinor = 60
        val answer = ask(BIG_APS_MAIN_INFO)

        assertThat(answer.u8(1)).isEqualTo(0x94)
        assertThat(answer.u8(30)).isEqualTo(60)
        assertThat(answer.u8(47)).isEqualTo(2)
        assertThat(answer.u8(52)).isEqualTo(5)
        assertThat((0 until 24).map { answer.u16(101 + 2 * it) }).containsExactlyElementsIn(List(24) { 100 })
        assertThat(answer.u16(149)).isEqualTo(300)
        assertThat(answer.u16(152)).isEqualTo(3000)
        assertThat(answer.u16(154)).isEqualTo(9000)
        assertThat(answer.u8(159)).isEqualTo(3)
        assertThat(answer.u8(160)).isEqualTo(0xFF)
    }

    @Test
    fun theLimitAnswersAreTheOnesOfTheRealPump() {
        assertThat(hex(ask(BASAL_LIMIT).copyOfRange(4, 7))).isEqualTo("10 2c 01")
        assertThat(hex(ask(SNACK_LIMIT).copyOfRange(4, 9))).isEqualTo("10 b8 0b 28 23")
    }

    @Test
    fun aClockThatWasNeverSetAnswersMinutesPastTheHour() {
        state.clockNeverSet = true
        state.poweredOnAt = System.currentTimeMillis() - (65 * 60 + 21) * 1000L

        val answer = ask(TIME)

        assertThat(hex(answer.copyOfRange(5, 10))).isEqualTo("19 03 13 00 41")
        assertThat(answer.u8(10)).isIn(listOf(21, 22))
    }

    @Test
    fun theClockIsTheWholeSecondOfThePhone() {
        val before = LocalDateTime.now().withNano(0)
        val answer = ask(TIME)
        val after = LocalDateTime.now()

        val clock = LocalDateTime.of(2000 + answer.u8(5), answer.u8(6), answer.u8(7), answer.u8(8), answer.u8(9), answer.u8(10))
        assertThat(clock).isAtLeast(before)
        assertThat(clock).isAtMost(after)
    }

    @Test
    fun aRequestWithAWrongCrcGetsTheCrcError() {
        val request = DiaconnPacket.build(TIME, 7, ByteArray(0))
        request[19] = (request[19] + 1).toByte()

        val answer = checkNotNull(emulator.receive(request).answer)

        assertThat(answer.u8(1)).isEqualTo(0x8F)
        assertThat(answer.u8(2)).isEqualTo(7)
        assertThat(answer.u8(4)).isEqualTo(17)
    }

    // --- Settings and confirms ---

    @Test
    fun aSettingIsDoneOnlyAtItsConfirm() {
        val answer = ask(BOLUS_SPEED_SETTING) { u8(3) }

        assertThat(answer.u8(1)).isEqualTo(0x85)
        assertThat(answer.u8(4)).isEqualTo(0)
        assertThat(answer.i32(5)).isAtLeast(100_000)
        assertThat(answer.i32(5)).isAtMost(999_999)
        assertThat(ask(BOLUS_SPEED).u8(5)).isEqualTo(5)

        val confirm = checkNotNull(send(APP_CONFIRM) { u8(BOLUS_SPEED_SETTING).u32(answer.i32(5).toLong()) }.answer)

        assertThat(confirm.u8(1)).isEqualTo(0xB7)
        assertThat(confirm.u8(4)).isEqualTo(0)
        assertThat(ask(BOLUS_SPEED).u8(5)).isEqualTo(3)
    }

    @Test
    fun aConfirmWithAWrongOtpIsRefusedAndDropsTheSetting() {
        val otp = ask(BOLUS_SPEED_SETTING) { u8(3) }.i32(5)

        assertThat(send(APP_CONFIRM) { u8(BOLUS_SPEED_SETTING).u32(otp + 1L) }.answer?.u8(4)).isEqualTo(10)
        assertThat(send(APP_CONFIRM) { u8(BOLUS_SPEED_SETTING).u32(otp.toLong()) }.answer?.u8(4)).isEqualTo(10)
        assertThat(ask(BOLUS_SPEED).u8(5)).isEqualTo(5)
    }

    @Test
    fun aConfirmWithNoSettingIsRefused() {
        assertThat(send(APP_CONFIRM) { u8(BOLUS).u32(123456) }.answer?.u8(4)).isEqualTo(10)
    }

    @Test
    fun aRefusedSettingHasNoOtp() {
        state.suspended = true

        val answer = ask(BOLUS) { u16(100) }

        assertThat(answer.u8(4)).isEqualTo(9)
        assertThat(answer.copyOfRange(5, 9).all { it == 0xFF.toByte() }).isTrue()
    }

    @Test
    fun aBolusOverTheLimitIsRefused() {
        assertThat(ask(BOLUS) { u16(3001) }.u8(4)).isEqualTo(13)
    }

    @Test
    fun aTimeSettingSetsAClockThatWasNeverSet() {
        state.clockNeverSet = true
        val now = LocalDateTime.now()

        val confirm = set(TIME_SETTING) { u8(now.year - 2000).u8(now.monthValue).u8(now.dayOfMonth).u8(now.hour).u8(now.minute).u8(now.second) }

        assertThat(confirm?.u8(4)).isEqualTo(0)
        assertThat(state.clockNeverSet).isFalse()
        val answer = ask(TIME)
        val clock = LocalDateTime.of(2000 + answer.u8(5), answer.u8(6), answer.u8(7), answer.u8(8), answer.u8(9), answer.u8(10))
        assertThat(Duration.between(clock, LocalDateTime.now()).abs().seconds).isAtMost(2)
    }

    @Test
    fun aBasalProfileTakesFourGroupsAndOneConfirm() {
        var otp = 0
        for (group in 1..4) {
            val answer = ask(BASAL_SETTING) {
                u8(1).u8(group)
                repeat(6) { u16(50 + group) }
            }
            assertThat(answer.u8(4)).isEqualTo(0)
            otp = answer.i32(5)
        }

        assertThat(send(APP_CONFIRM) { u8(BASAL_SETTING).u32(otp.toLong()) }.answer?.u8(4)).isEqualTo(0)
        assertThat(emulator.snapshot(linkUp = false).basal).isEqualTo(List(24) { (51 + it / 6) / 100.0 })
    }

    /**
     * Real G8 fw 2.50: a basal setting report about 23.5 s after the confirm of a basal write, and the start of the basal
     * refused with 15 right after it; it was taken 30.5 s after the confirm. All shorter at FAST.
     */
    @Test
    fun theStartOfAWrittenBasalWaitsUntilThePumpIsReady() {
        var otp = 0
        for (group in 1..4) otp = ask(BASAL_SETTING) { u8(1).u8(group).u16(70).u16(70).u16(70).u16(70).u16(70).u16(70) }.i32(5)
        val confirmedAt = System.currentTimeMillis()
        assertThat(send(APP_CONFIRM) { u8(BASAL_SETTING).u32(otp.toLong()) }.answer?.u8(4)).isEqualTo(0)
        assertThat(reports.none { it.u8(1) == BASAL_SETTING_REPORT }).isTrue()
        // The new rates run from the confirm on, also before the start is taken.
        assertThat(emulator.snapshot(linkUp = false).basal).isEqualTo(List(24) { 0.7 })

        val report = awaitReport(BASAL_SETTING_REPORT)
        assertThat(System.currentTimeMillis() - confirmedAt).isAtLeast(DiaconnPumpEmulator.FAST_BASAL_REPORT_MS)
        assertThat(report.u8(4)).isEqualTo(0)
        assertThat(ask(INJECTION_BASAL) { u8(1) }.u8(4)).isEqualTo(DiaconnPumpEmulator.BASAL_NOT_SAVED)

        Thread.sleep(confirmedAt + DiaconnPumpEmulator.FAST_BASAL_READY_MS + 50 - System.currentTimeMillis())
        assertThat(ask(INJECTION_BASAL) { u8(1) }.u8(4)).isEqualTo(0)
        assertThat(reports.count { it.u8(1) == BASAL_SETTING_REPORT }).isEqualTo(1)
    }

    @Test
    fun aBasalProfileWithAGroupMissingIsRefused() {
        var otp = 0
        for (group in 2..4) otp = ask(BASAL_SETTING) { u8(1).u8(group).u16(70).u16(70).u16(70).u16(70).u16(70).u16(70) }.i32(5)

        assertThat(send(APP_CONFIRM) { u8(BASAL_SETTING).u32(otp.toLong()) }.answer?.u8(4)).isEqualTo(2)
        assertThat(emulator.snapshot(linkUp = false).basal).isEqualTo(List(24) { 1.0 })
    }

    // --- Bolus ---

    @Test
    fun aBolusEndsWithItsResultReportAndRecord() {
        assertThat(set(BOLUS) { u16(50) }?.u8(4)).isEqualTo(0)
        assertThat(emulator.snapshot(linkUp = false).bolusRunning).isTrue()

        val result = awaitReport(0xE4)

        assertThat(result.u8(4)).isEqualTo(0)
        assertThat(result.u16(5)).isEqualTo(50)
        assertThat(result.u16(7)).isEqualTo(50)
        val record = records().single()
        assertThat(record.u8(4) and 0x3F).isEqualTo(0x0A)
        assertThat(record.u16(5)).isEqualTo(50)
        assertThat(record.u16(7)).isEqualTo(50)
        assertThat(ask(LOG_STATUS).u16(5)).isEqualTo(state.log.maxOf { it.number } + 1)
        val pumpState = emulator.snapshot(linkUp = false)
        assertThat(pumpState.boluses).containsExactly(0.5)
        assertThat(pumpState.bolusRunning).isFalse()
        assertThat(pumpState.reservoir!!).isWithin(0.001).of(199.5)
    }

    @Test
    fun aBolusStoppedFromTheAppReportsWhatItGave() {
        set(BOLUS) { u16(500) }
        awaitTrue { ask(SNACK).u16(8) >= 50 }

        val stop = ask(INJECTION_CANCEL) { u8(BOLUS) }
        val confirm = send(APP_CONFIRM) { u8(INJECTION_CANCEL).u32(stop.i32(5).toLong()) }

        assertThat(confirm.answer?.u8(4)).isEqualTo(0)
        val result = confirm.reportsAfter.single()
        assertThat(result.u8(1)).isEqualTo(0xE4)
        assertThat(result.u8(4)).isEqualTo(1)
        assertThat(result.u16(5)).isEqualTo(500)
        val given = result.u16(7)
        assertThat(given).isIn(50..499)
        val record = records().single()
        assertThat(record.u8(4) and 0x3F).isEqualTo(0x0B)
        assertThat(record.u16(7)).isEqualTo(given)
        assertThat(record.u8(10)).isEqualTo(4)
        assertThat(emulator.snapshot(linkUp = false).boluses).containsExactly(given / 100.0)
    }

    @Test
    fun aBolusThePumpStopsItselfReportsWhatItGave() {
        emulator.stopNextBolusAt(0.3)

        set(BOLUS) { u16(100) }
        val result = awaitReport(0xE4)

        assertThat(result.u8(4)).isNotEqualTo(1)
        assertThat(result.u16(7)).isEqualTo(30)
        val record = records().single()
        assertThat(record.u8(4) and 0x3F).isEqualTo(0x0B)
        assertThat(record.u8(10)).isEqualTo(1)
        assertThat(emulator.snapshot(linkUp = false).boluses).containsExactly(0.3)
    }

    @Test
    fun fromFirmware353TheBolusSendsItsProgress() {
        state.firmwareMajor = 3
        state.firmwareMinor = 53

        set(BOLUS) { u16(80) }
        awaitReport(0xE4)

        assertThat(reports.map { it.u8(1) }.first()).isEqualTo(0xEA)
        assertThat(reports.last().u8(1)).isEqualTo(0xE4)
        assertThat(reports.filter { it.u8(1) == 0xEA }.all { it.u16(4) == 80 }).isTrue()
    }

    @Test
    fun aBolusOnThePumpIsLoggedWithoutALink() {
        assertThat(emulator.bolusOnPump(1.5)).isTrue()

        val record = records().single()
        assertThat(record.u8(4) and 0x3F).isEqualTo(0x0A)
        assertThat(record.u16(7)).isEqualTo(150)
        assertThat(emulator.snapshot(linkUp = false).boluses).containsExactly(1.5)
    }

    // --- Temp basal ---

    @Test
    fun aTempBasalStartAndStopAreLoggedWithTheTimeTheAppSent() {
        assertThat(setTempBasal(status = 1, ratio = 1080)?.u8(4)).isEqualTo(0)

        val running = ask(TEMP_BASAL)
        assertThat(running.u8(5)).isEqualTo(1)
        assertThat(running.u8(6)).isEqualTo(2)
        assertThat(running.u16(7)).isEqualTo(1080)
        assertThat(running.u16(9)).isEqualTo(0)
        assertThat(emulator.snapshot(linkUp = false).tempBasal?.absolute).isEqualTo(0.8)
        val start = records().single()
        assertThat(start.u8(4)).isEqualTo(0x92)
        assertThat(start.u8(5)).isEqualTo(2)
        assertThat(start.u16(6)).isEqualTo(1080)
        assertThat(start.i32(8)).isEqualTo(TB_DTTM)

        assertThat(setTempBasal(status = 2, ratio = 1080)?.u8(4)).isEqualTo(0)

        val stop = records().last()
        assertThat(stop.u8(4)).isEqualTo(0x93)
        assertThat(stop.u16(5)).isEqualTo(1080)
        assertThat(stop.u8(7)).isEqualTo(4)
        assertThat(stop.i32(8)).isEqualTo(TB_DTTM)
        val stopped = ask(TEMP_BASAL)
        assertThat(stopped.u8(5)).isEqualTo(2)
        assertThat(stopped.u16(7)).isEqualTo(1080)
        assertThat(emulator.snapshot(linkUp = false).tempBasal).isNull()
    }

    // Real G8 (2026-11-11): a request right after the confirm of a temp basal is answered when the pump has carried it
    // out, after a confirm report and a temp basal report.
    @Test
    fun aRequestRightAfterATempBasalWaitsUntilItRuns() {
        val confirmedAt = System.currentTimeMillis()
        assertThat(set(TEMP_BASAL_SETTING) { tempBasal(status = 1, ratio = 1080) }?.u8(4)).isEqualTo(0)

        val exchange = send(TEMP_BASAL)

        assertThat(exchange.heldUntil).isGreaterThan(confirmedAt)
        assertThat(exchange.reportsBefore.map { it.u8(1) }).containsExactly(0xE8, 0xCA).inOrder()
        assertThat(exchange.reportsBefore.last().u8(4)).isEqualTo(1)
        assertThat(exchange.answer?.u8(5)).isEqualTo(1)
    }

    // Real G8 (2026-11-11): the driver sends a start right after the confirm of a stop. The pump answers it when the stop is
    // done, and takes it.
    @Test
    fun aStartRightAfterAStopIsTaken() {
        setTempBasal(status = 1, ratio = 1080)
        assertThat(set(TEMP_BASAL_SETTING) { tempBasal(status = 2, ratio = 1080) }?.u8(4)).isEqualTo(0)

        assertThat(set(TEMP_BASAL_SETTING) { tempBasal(status = 1, ratio = 1100) }?.u8(4)).isEqualTo(0)
        awaitTrue { ask(TEMP_BASAL).u16(7) == 1100 }
    }

    @Test
    fun aTempBasalIsRefusedAsThePumpRefusesIt() {
        assertThat(ask(TEMP_BASAL_SETTING) { tempBasal(status = 2, ratio = 1080) }.u8(4)).isEqualTo(36)
        setTempBasal(status = 1, ratio = 1080)
        assertThat(ask(TEMP_BASAL_SETTING) { tempBasal(status = 1, ratio = 1100) }.u8(4)).isEqualTo(35)
    }

    @Test
    fun aStopOnThePumpKeepsTheTempBasalAndItsEnd() {
        setTempBasal(status = 1, ratio = 1080)

        assertThat(emulator.suspendOnPump(true)).isTrue()

        assertThat(records().last().u8(4)).isEqualTo(0x43)
        assertThat(records().none { it.u8(4) and 0x3F == 0x13 }).isTrue()
        assertThat(emulator.snapshot(linkUp = false).suspended).isTrue()
        assertThat(emulator.snapshot(linkUp = false).tempBasal).isNull()
        assertThat(ask(BIG_MAIN_INFO).u8(14)).isEqualTo(1)

        // Ten minutes of the stop went by.
        synchronized(state) { state.tempBasalStartedAt -= 10 * 60_000L }
        assertThat(emulator.suspendOnPump(false)).isTrue()

        assertThat(records().last().u8(4)).isEqualTo(0x44)
        assertThat(emulator.snapshot(linkUp = false).tempBasal?.absolute).isEqualTo(0.8)
        assertThat(ask(TEMP_BASAL).u16(9)).isEqualTo(10)

        // Its first end goes by: the pump ends it as completed.
        synchronized(state) { state.tempBasalStartedAt -= 25 * 60_000L }
        assertThat(ask(TEMP_BASAL).u8(5)).isEqualTo(2)
        val end = records().last()
        assertThat(end.u8(4)).isEqualTo(0x93)
        assertThat(end.u8(7)).isEqualTo(0)
    }

    // --- Extended bolus ---

    // Real G8 (2026-11-11): a log status right after the confirm of an extended bolus start was answered at once, without
    // its record. The record came about 1.8 s later.
    @Test
    fun aReadRightAfterAnExtendedBolusStartIsNotHeld() {
        assertThat(set(EXTENDED_BOLUS) { u16(30).u16(150).u32(123) }?.u8(4)).isEqualTo(0)

        val exchange = send(LOG_STATUS)

        assertThat(exchange.heldUntil).isEqualTo(0L)
        assertThat(records()).isEmpty()
        awaitTrue { records().isNotEmpty() }
    }

    @Test
    fun anExtendedBolusStartAndStopAreLogged() {
        assertThat(setCarriedOut(EXTENDED_BOLUS) { u16(30).u16(150).u32(123) }?.u8(4)).isEqualTo(0)

        val start = records().single()
        assertThat(start.u8(4) and 0x3F).isEqualTo(0x0C)
        assertThat(start.u16(5)).isEqualTo(150)
        assertThat(start.u8(7)).isEqualTo(3)
        assertThat(emulator.snapshot(linkUp = false).extendedBolus).isEqualTo(EmulatedExtendedBolus(1.5, 30))
        assertThat(ask(BIG_MAIN_INFO).u8(64)).isEqualTo(1)

        assertThat(set(INJECTION_CANCEL) { u8(EXTENDED_BOLUS) }?.u8(4)).isEqualTo(0)
        val reportsAfterTheStop = send(LOG_STATUS).reportsBefore

        val stop = records().last()
        assertThat(stop.u8(4) and 0x3F).isEqualTo(0x0E)
        assertThat(stop.u8(8)).isEqualTo(4)
        assertThat(emulator.snapshot(linkUp = false).extendedBolus).isNull()
        // The confirm report, then the result report: stopped, 30 min set, 150 set.
        assertThat(reportsAfterTheStop.map { it.u8(1) }).containsExactly(0xE8, 0xE5).inOrder()
        val result = reportsAfterTheStop.last()
        assertThat(result.u8(4)).isEqualTo(1)
        assertThat(result.u16(5)).isEqualTo(30)
        assertThat(result.u16(9)).isEqualTo(150)
    }

    // --- The log ---

    @Test
    fun logStatusIsOnePastTheNewestRecord() {
        emulator.bolusOnPump(1.0)

        val status = ask(LOG_STATUS)
        assertThat(status.u16(5)).isEqualTo(1)
        assertThat(status.u8(7)).isEqualTo(0)
        assertThat(ask(BIG_LOG) { u16(0).u16(1).u8(100) }.u8(5)).isEqualTo(1)
        assertThat(ask(BIG_LOG) { u16(1).u16(2).u8(100) }.u8(5)).isEqualTo(0)
    }

    /**
     * A new pump has a record in its log, as every real one: at the first pairing the driver reads only the newest
     * record and then goes on after it. The tests here start with an empty log, so they can count the records.
     */
    @Test
    fun aNewPumpHasARecordInItsLog() {
        emulator = DiaconnPumpEmulator()

        assertThat(ask(LOG_STATUS).u16(5)).isEqualTo(1)
        val answer = ask(BIG_LOG) { u16(0).u16(1).u8(100) }
        assertThat(answer.u8(5)).isEqualTo(1)
        assertThat(answer.u8(9 + 4) and 0x3F).isEqualTo(RESET_SYS)
        assertThat(answer.u8(9 + 6)).isEqualTo(RESET_BEFORE_SHIPMENT)
    }

    // Real G8 (2026-11-11): request 54 got 0x27, request 55 got 0x29. A driver cannot match it by number.
    @Test
    fun aBigLogAnswerHasANumberOfThePumpsOwn() {
        val first = ask(BIG_LOG) { u16(0).u16(1).u8(100) }
        val second = ask(BIG_LOG) { u16(0).u16(1).u8(100) }

        assertThat(first.u8(2)).isNotEqualTo(seq - 1)
        assertThat(second.u8(2)).isNotEqualTo(seq)
        assertThat(second.u8(2)).isGreaterThan(first.u8(2))
    }

    @Test
    fun aBigLogAnswerHoldsAtMost11RecordsFromStartToBeforeEnd() {
        repeat(15) { emulator.bolusOnPump(0.1) }

        val answer = ask(BIG_LOG) { u16(2).u16(20).u8(100) }

        assertThat(answer.size).isEqualTo(182)
        assertThat(answer.u8(1)).isEqualTo(0xB2)
        assertThat(answer.u8(4)).isEqualTo(16)
        assertThat(answer.u8(5)).isEqualTo(11)
        assertThat((0 until 11).map { answer.u16(7 + 15 * it) }).containsExactlyElementsIn(2..12).inOrder()
        assertThat(answer.u8(6)).isEqualTo(0)
        assertThat(answer.u8(9 + 4) and 0x3F).isEqualTo(0x0A)
        assertThat(answer.copyOfRange(6 + 15 * 11, 181).all { it == 0xFF.toByte() }).isTrue()
        assertThat(ask(BIG_LOG) { u16(13).u16(15).u8(100) }.u8(5)).isEqualTo(2)
    }

    @Test
    fun theLogWrapsPast9999() {
        state.nextLogNumber = 9998
        repeat(3) { emulator.bolusOnPump(0.1) }

        val status = ask(LOG_STATUS)
        assertThat(status.u16(5)).isEqualTo(1)
        assertThat(status.u8(7)).isEqualTo(1)
        val old = ask(BIG_LOG) { u16(9998).u16(10000).u8(100) }
        assertThat(old.u8(5)).isEqualTo(2)
        assertThat(listOf(old.u8(6), old.u16(7), old.u8(21), old.u16(22))).containsExactly(0, 9998, 0, 9999).inOrder()
        val new = ask(BIG_LOG) { u16(0).u16(1).u8(100) }
        assertThat(listOf(new.u8(5), new.u8(6), new.u16(7))).containsExactly(1, 1, 0).inOrder()
    }

    @Test
    fun theHoursThatWentByAreLogged() {
        synchronized(state) { state.lastSettleAt = System.currentTimeMillis() - 2 * 3_600_000L }

        ask(BIG_MAIN_INFO)

        assertThat(allRecords().count { it.u8(4) and 0x3F == 0x2C }).isEqualTo(2)
        assertThat(emulator.snapshot(linkUp = false).reservoir!!).isWithin(0.01).of(198.0)
    }

    @Test
    fun refillChangesAndBatteryAreLogged() {
        // No basal, so the reservoir changes only by what is primed.
        state.basalRates.fill(0.0)
        emulator.refill(300.0)
        emulator.changeNeedle(0.3)
        emulator.changeTube(5.0)
        emulator.replaceBattery()

        assertThat(records().map { it.u8(4) and 0x3F }).containsExactly(0x1A, 0x1C, 0x18, 0x01).inOrder()
        assertThat(records()[1].u16(5)).isEqualTo(30)
        assertThat(records()[2].u16(7)).isEqualTo(29470)
        assertThat(records()[3].u8(6)).isEqualTo(3)
        assertThat(emulator.snapshot(linkUp = false).battery).isEqualTo(100)
    }

    // --- Faults ---

    @Test
    fun aLostCommandIsNeverDone() {
        emulator.loseCommand(EmulatedCommand.BOLUS_START)

        assertThat(send(BOLUS) { u16(100) }.answer).isNull()
        assertThat(state.commands).isEqualTo(0)
        assertThat(ask(BOLUS) { u16(100) }.u8(4)).isEqualTo(0)
    }

    @Test
    fun aLostAnswerOfASettingIsTheAnswerOfItsConfirm() {
        emulator.loseAnswer(EmulatedCommand.BOLUS_START)

        val otp = ask(BOLUS) { u16(100) }.i32(5)

        assertThat(send(APP_CONFIRM) { u8(BOLUS).u32(otp.toLong()) }.answer).isNull()
        assertThat(emulator.snapshot(linkUp = false).bolusRunning).isTrue()
    }

    @Test
    fun faultsFireInTheOrderTheyWereSet() {
        emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)
        emulator.loseAnswer(EmulatedCommand.STATUS)
        setTempBasal(status = 1, ratio = 1080)

        assertThat(send(TEMP_BASAL).answer).isNotNull()
        assertThat(setTempBasal(status = 2, ratio = 1080)).isNull()
        assertThat(send(TEMP_BASAL).answer).isNull()
        assertThat(send(TEMP_BASAL).answer?.u8(5)).isEqualTo(2)
    }

    // --- Save and restore ---

    @Test
    fun aSavedPumpGoesOnWithItsBolus() {
        set(BOLUS) { u16(200) }
        awaitTrue { ask(SNACK).u16(8) > 0 }

        val saved = emulator.saveState()
        // The app and its pump are gone; only the saved pump goes on.
        emulator.reportSink = null
        reports.clear()
        val restored = DiaconnPumpEmulator.restore(saved)
        restored.reportSink = { report -> reports += report }

        assertThat(restored.state.runningBolus).isNotNull()
        val result = awaitReport(0xE4)
        assertThat(result.u16(7)).isEqualTo(200)
        assertThat(restored.snapshot(linkUp = false).boluses).containsExactly(2.0)
        assertThat(restored.state.log.count { it.data.u8(4) and 0x3F == 0x0A }).isEqualTo(1)
    }

    // --- Helpers ---

    private fun PacketData.tempBasal(status: Int, ratio: Int): PacketData = u8(status).u8(2).u16(ratio).u32(TB_DTTM.toLong()).u32(0)

    private fun send(msgType: Int, fields: PacketData.() -> Unit = {}): DiaconnPumpEmulator.Exchange {
        seq = (seq + 1) and 0xFF
        val exchange = emulator.receive(DiaconnPacket.build(msgType, seq, PacketData().apply(fields).bytes()))
        reports += exchange.reportsBefore
        reports += exchange.reportsAfter
        exchange.answer?.let { answer ->
            // A big log answer has a number of the pump's own.
            if (msgType != BIG_LOG) assertThat(answer.u8(2)).isEqualTo(seq)
            assertThat(answer.u8(1)).isEqualTo((msgType and 0x3F) or 0x80)
            assertThat(DiaconnPacket.crc(answer, answer.size - 1)).isEqualTo(answer.last())
        }
        return exchange
    }

    private fun ask(msgType: Int, fields: PacketData.() -> Unit = {}): ByteArray = checkNotNull(send(msgType, fields).answer)

    /** A setting and its confirm; the answer to the confirm. */
    private fun set(msgType: Int, fields: PacketData.() -> Unit): ByteArray? {
        val answer = ask(msgType, fields)
        assertThat(answer.u8(4)).isEqualTo(0)
        return send(APP_CONFIRM) { u8(msgType).u32(answer.i32(5).toLong()) }.answer
    }

    private fun setTempBasal(status: Int, ratio: Int): ByteArray? = setCarriedOut(TEMP_BASAL_SETTING) { tempBasal(status, ratio) }

    /**
     * A setting the pump carries out a moment after its confirm, and then the wait until it has. The snapshot brings the
     * pump up to now, with no packet that a fault could take.
     */
    private fun setCarriedOut(msgType: Int, fields: PacketData.() -> Unit): ByteArray? =
        set(msgType, fields).also {
            Thread.sleep(DiaconnPumpEmulator.FAST_CARRY_OUT_MS + 50)
            emulator.snapshot(linkUp = false)
        }

    /** The log records, oldest first, without the hourly and daily ones: an hour may end while a test runs. */
    private fun records(): List<ByteArray> = synchronized(state) {
        state.log.sortedBy { it.number }.map { it.data }.filter { it.u8(4) and 0x3F !in PERIODIC_KINDS }
    }

    private fun allRecords(): List<ByteArray> = synchronized(state) { state.log.map { it.data } }

    private fun awaitReport(msgType: Int): ByteArray {
        awaitTrue { reports.any { it.u8(1) == msgType } }
        return reports.first { it.u8(1) == msgType }
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it) }

    private companion object {

        /** The serial of the real G8 whose answers these tests copy. */
        const val REAL_SERIAL = 64004

        // From the protocol, not from the emulator.
        const val BOLUS_SPEED_SETTING = 0x05
        const val BOLUS = 0x07
        const val EXTENDED_BOLUS = 0x08
        const val TEMP_BASAL_SETTING = 0x0A
        const val BASAL_SETTING = 0x0B
        const val INJECTION_BASAL = 0x0C
        const val TIME_SETTING = 0x0F
        const val INJECTION_CANCEL = 0x2B
        const val APP_CONFIRM = 0x37
        const val BOLUS_SPEED = 0x45
        const val SNACK = 0x47
        const val TEMP_BASAL = 0x4A
        const val TIME = 0x4F
        const val SNACK_LIMIT = 0x50
        const val BASAL_LIMIT = 0x52
        const val BIG_APS_MAIN_INFO = 0x54
        const val LOG_STATUS = 0x56
        const val BIG_LOG = 0x72
        const val RESET_SYS = 0x01
        const val RESET_BEFORE_SHIPMENT = 5
        const val BIG_MAIN_INFO = 0x73
        const val BASAL_SETTING_REPORT = 0xCB

        /** The TB_DTTM the driver always sends. */
        const val TB_DTTM = 946652400

        /** The hourly basal, the daily basal and the daily total. */
        val PERIODIC_KINDS = setOf(0x2C, 0x2E, 0x2F)
    }
}
