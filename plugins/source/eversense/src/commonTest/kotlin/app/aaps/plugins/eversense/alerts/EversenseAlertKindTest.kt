package app.aaps.plugins.eversense.alerts

import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.DosingEffect
import app.aaps.plugins.eversense.enums.EversenseAlarm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EversenseAlertKindTest {

    /** The ids are stored with the user's choice: a renamed id would quietly drop what the user chose for it. */
    @Test
    fun theIdsStayAsTheyAre() {
        assertEquals(
            listOf(
                "transmitter_error", "sensor_replacement", "battery_empty", "sensor_temperature_high", "sensor_temperature_low",
                "transmitter_temperature_high", "no_sensor", "new_sensor", "calibrate_now", "calibration_past_due",
                "calibration_expired", "low_battery", "invalid_time", "sensor_stability", "transmitter_disconnected",
                "vibration_motor", "calibrate_again", "new_calibration_needed", "transmitter_replacement", "battery_error",
                "one_calibration_phase", "battery_status", "sensor_connection", "sensor_retirement_area", "glucose_suspend",
                "sensor_sync_confirmed", "two_calibration_phase", "unknown"
            ).map { "eversense.eversense_alarm_$it" } + "eversense.eversense_placement_title",
            EversenseAlertKind.all.map { it.id }
        )
    }

    /** A kind with an event that affects dosing always rings (decided 2026-11-24); only the rest can be made silent. */
    @Test
    fun onlyTheKindsThatDoNotAffectDosingCanBeSilenced() {
        assertEquals(
            listOf("vibration_motor", "one_calibration_phase", "battery_status", "sensor_sync_confirmed", "two_calibration_phase")
                .map { "eversense.eversense_alarm_$it" },
            EversenseAlertKind.all.filter { !it.isProtected }.map { it.id }
        )
    }

    /** Every alarm has a kind, except the glucose alarms: AAPS's own BG alarms raise those. */
    @Test
    fun everyAlarmButTheGlucoseAlarmsHasAKind() {
        EversenseAlarm.entries.forEach { alarm ->
            if (alarm.effect == DosingEffect.GLUCOSE) assertNull(EversenseAlertKind.of(alarm), alarm.name)
            else assertTrue(EversenseAlertKind.of(alarm) != null, alarm.name)
        }
    }

    @Test
    fun everyKindIsTheTransmitters() {
        EversenseAlertKind.all.forEach {
            assertEquals(EversenseAlertKind.SOURCE, it.source)
            assertEquals(AlertOrigin.CGM, it.origin)
        }
    }
}
