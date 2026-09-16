package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Carelevo activation [CustomCommand] marker classes in
 * [app.aaps.pump.carelevo.command] (CarelevoActivationCommands.kt) and [CmdSafetyCheck].
 *
 * These are pure, dependency-free marker commands routed through the AAPS CommandQueue: no
 * executor delegation happens inside them, so the tests assert their public contract —
 * [CustomCommand.statusDescription], stored constructor arguments and the [CustomCommand] type
 * contract. The commands are queued in memory only, so there is nothing to serialize.
 */
internal class CarelevoActivationCommandsTest {

    // region statusDescription — no-arg markers

    @Test
    fun `CmdNeedleCheck exposes NEEDLE CHECK status`() {
        assertThat(CmdNeedleCheck().statusDescription).isEqualTo("NEEDLE CHECK")
    }

    @Test
    fun `CmdSetBasal exposes SET BASAL status`() {
        assertThat(CmdSetBasal().statusDescription).isEqualTo("SET BASAL")
    }

    @Test
    fun `CmdAdditionalPriming exposes ADDITIONAL PRIMING status`() {
        assertThat(CmdAdditionalPriming().statusDescription).isEqualTo("ADDITIONAL PRIMING")
    }

    @Test
    fun `CmdDiscard exposes DISCARD PATCH status`() {
        assertThat(CmdDiscard().statusDescription).isEqualTo("DISCARD PATCH")
    }

    @Test
    fun `CmdPumpResume exposes PUMP RESUME status`() {
        assertThat(CmdPumpResume().statusDescription).isEqualTo("PUMP RESUME")
    }

    @Test
    fun `CmdSafetyCheck exposes SAFETY CHECK status`() {
        assertThat(CmdSafetyCheck().statusDescription).isEqualTo("SAFETY CHECK")
    }

    // endregion

    // region statusDescription + stored args — parameterized markers

    @Test
    fun `CmdPumpStop exposes PUMP STOP status and stores durationMin`() {
        val cmd = CmdPumpStop(durationMin = 30)
        assertThat(cmd.statusDescription).isEqualTo("PUMP STOP")
        assertThat(cmd.durationMin).isEqualTo(30)
    }

    @Test
    fun `CmdPumpStop stores zero and negative durations verbatim`() {
        assertThat(CmdPumpStop(0).durationMin).isEqualTo(0)
        assertThat(CmdPumpStop(-1).durationMin).isEqualTo(-1)
    }

    @Test
    fun `CmdTimeZoneUpdate exposes TIMEZONE UPDATE status and stores insulinAmount`() {
        val cmd = CmdTimeZoneUpdate(insulinAmount = 42)
        assertThat(cmd.statusDescription).isEqualTo("TIMEZONE UPDATE")
        assertThat(cmd.insulinAmount).isEqualTo(42)
    }

    @Test
    fun `CmdUpdateMaxBolus exposes UPDATE MAX BOLUS status and stores maxBolusDose`() {
        val cmd = CmdUpdateMaxBolus(maxBolusDose = 12.5)
        assertThat(cmd.statusDescription).isEqualTo("UPDATE MAX BOLUS")
        assertThat(cmd.maxBolusDose).isEqualTo(12.5)
    }

    @Test
    fun `CmdUpdateLowInsulinNotice exposes UPDATE LOW INSULIN NOTICE status and stores hours`() {
        val cmd = CmdUpdateLowInsulinNotice(hours = 6)
        assertThat(cmd.statusDescription).isEqualTo("UPDATE LOW INSULIN NOTICE")
        assertThat(cmd.hours).isEqualTo(6)
    }

    @Test
    fun `CmdUpdateExpiredThreshold exposes UPDATE EXPIRY THRESHOLD status and stores hours`() {
        val cmd = CmdUpdateExpiredThreshold(hours = 4)
        assertThat(cmd.statusDescription).isEqualTo("UPDATE EXPIRY THRESHOLD")
        assertThat(cmd.hours).isEqualTo(4)
    }

    @Test
    fun `CmdUpdateBuzzer exposes UPDATE BUZZER status and stores on true`() {
        val cmd = CmdUpdateBuzzer(on = true)
        assertThat(cmd.statusDescription).isEqualTo("UPDATE BUZZER")
        assertThat(cmd.on).isTrue()
    }

    @Test
    fun `CmdUpdateBuzzer stores on false`() {
        assertThat(CmdUpdateBuzzer(on = false).on).isFalse()
    }

    @Test
    fun `CmdAlarmClear exposes ALARM CLEAR status and stores alarm fields`() {
        val cmd = CmdAlarmClear(
            alarmId = "alarm-1",
            alarmType = AlarmType.ALERT,
            alarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN
        )
        assertThat(cmd.statusDescription).isEqualTo("ALARM CLEAR")
        assertThat(cmd.alarmId).isEqualTo("alarm-1")
        assertThat(cmd.alarmType).isEqualTo(AlarmType.ALERT)
        assertThat(cmd.alarmCause).isEqualTo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
    }

    @Test
    fun `CmdAlarmClearPatchDiscard exposes ALARM CLEAR PATCH DISCARD status and stores alarm fields`() {
        val cmd = CmdAlarmClearPatchDiscard(
            alarmId = "alarm-9",
            alarmType = AlarmType.WARNING,
            alarmCause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED
        )
        assertThat(cmd.statusDescription).isEqualTo("ALARM CLEAR PATCH DISCARD")
        assertThat(cmd.alarmId).isEqualTo("alarm-9")
        assertThat(cmd.alarmType).isEqualTo(AlarmType.WARNING)
        assertThat(cmd.alarmCause).isEqualTo(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)
    }

    // endregion

    // region CustomCommand / Serializable type contract

    @Test
    fun `every activation command is a CustomCommand`() {
        val commands: List<CustomCommand> = listOf(
            CmdNeedleCheck(),
            CmdSetBasal(),
            CmdAdditionalPriming(),
            CmdDiscard(),
            CmdPumpStop(30),
            CmdPumpResume(),
            CmdTimeZoneUpdate(1),
            CmdUpdateMaxBolus(1.0),
            CmdUpdateLowInsulinNotice(1),
            CmdUpdateExpiredThreshold(1),
            CmdUpdateBuzzer(true),
            CmdAlarmClear("a", AlarmType.ALERT, AlarmCause.ALARM_ALERT_OUT_OF_INSULIN),
            CmdAlarmClearPatchDiscard("a", AlarmType.WARNING, AlarmCause.ALARM_WARNING_PUMP_CLOGGED),
            CmdSafetyCheck()
        )
        commands.forEach { assertThat(it).isInstanceOf(CustomCommand::class.java) }
    }

}
