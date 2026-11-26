package app.aaps.pump.aaruy.common.enums

/**
 * Pump errors (0x80..) and alarms (0x81..), as high byte and low byte of a pump message.
 * Only 0x8015 and 0x8105 are seen on a real pump so far. The others come from the vendor code.
 */
enum class AlarmState(val state: Int) {
    NONE(0),
    PUMP_ZERO_RESERVOIR(1),                     // Set by the driver when the status shows 0 U, not sent by the pump
    PUMP_RUN_BASAL(0x800C),                     // Basal could not start
    PUMP_RUN_TMP_BASAL(0x800D),                 // Temp basal could not start
    PUMP_RUN_BOLUS(0x800E),                     // Bolus could not start
    PUMP_DISCHARGE_LIQUID(0x8010),              // Prime failed
    PUMP_PUSH_BACK(0x8011),                     // Rewind failed
    PUMP_PLEASE_RUN_BASAL(0x8012),              // Basal must run first
    PUMP_STOP_15M_LATER(0x8013),                // Without any action the pump stops insulin in 15 minutes
    PUMP_STOP_TOO_LONG(0x8015),                 // Stopped for 15 minutes. Seen on a real pump: one beep only
    PUMP_LOW_BATTERY_RESET(0x8017),             // Battery too low to rewind
    PUMP_LOW_BATTERY_DISCHARGE(0x8018),         // Battery too low to prime

    PUMP_LOW_POWER(0x8101),                     // Battery low
    PUMP_NO_POWER(0x8102),                      // Battery almost empty
    PUMP_LOW_RESERVOIR(0x8103),                 // Reservoir low
    PUMP_NO_RESERVOIR(0x8104),                  // Reservoir empty
    PUMP_MOTOR_BLOCK(0x8105),                   // Motor blocked (occlusion). Seen on a real pump: repeats until confirmed
    PUMP_MOTOR_FAILED(0x8106),                  // Motor failed to run
    PUMP_MOTOR_BUSY(0x8107),                    // Motor busy
    PUMP_MOTOR_REPEAT(0x8108),                  // Motor command repeated
    PUMP_MOTOR_MCU_BLOCK(0x8109),               // The second controller saw a block
    PUMP_MOTOR_MCU_ILLEGAL(0x810A),             // The second controller saw a delivery that was not asked for
    PUMP_MOTOR_MCU_PLUSES_EXTRA(0x810B),        // The second controller saw more insulin than expected
    PUMP_MOTOR_DESTRUCTIVE_ANOMALY(0x810C),     // The motor slips on the piston thread. The pump cannot deliver any more
    PUMP_MOTOR_FAILED_PLUS(0x810D),             // Motor failed to run, a pump fault
    PUMP_MOTOR_FAILED_ERROR(0x810E),            // A blocking fault. The pump stops and pulls the piston back (vendor, PR 5589)
    PUMP_MOTOR_AUTO_POWEROFF(0x8114),           // The pump switched itself off

    /**
     * Stands for every alarm (0x81..) this driver does not know, so a new one is not lost. The vendor (PR 5589,
     * 2026-11-25): every alarm above 0x8100 except low battery and low reservoir stops delivery, and only a reservoir
     * change starts it again. The code the pump sent is kept in `AaruyPump.unknownAlarmCode`, for the text.
     * Confirmed like any other alarm: the confirm tells the pump only "low battery or low reservoir" or "another alarm".
     */
    PUMP_UNKNOWN_ALARM(0x81FF);

    /** An alarm (0x81..) the pump holds until the phone confirms it. Seen on a real pump: a blocked motor repeats until then. */
    fun isConfirmedOnThePump(): Boolean = state and 0xFF00 == 0x8100

    /**
     * Held until the pump shows it is over, rather than answered by the user: alarms the pump holds, and the stop and
     * empty-reservoir states. A refused command (the other 0x80..) is not held, it is over when the user saw it.
     */
    fun isHeld(): Boolean = isConfirmedOnThePump() || this == PUMP_ZERO_RESERVOIR || this == PUMP_STOP_TOO_LONG || this == PUMP_STOP_15M_LATER

    companion object {

        /** An unknown alarm (0x8101 to 0x81FF) is [PUMP_UNKNOWN_ALARM]. Any other unknown code, also a refused command (0x80..), is [NONE]. */
        fun fromInt(state: Int) = AlarmState.entries.find { it.state == state }
            ?: if (state in 0x8101..0x81FF) PUMP_UNKNOWN_ALARM else NONE

        /**
         * The code of an alarm from the pump history. Seen on a real pump: only the low byte is kept, a blocked motor
         * (0x8105) is logged as 5. UNVERIFIED for the other alarms: they are taken as alarms (0x81..) too, as the history
         * has no other kind seen so far.
         */
        fun historyCode(data: Int): Int = if (data in 1..0xFF) 0x8100 or data else data

        /** An alarm from the pump history, see [historyCode]. */
        fun fromHistory(data: Int): AlarmState = fromInt(historyCode(data))

        fun isHighAlarm(state: AlarmState): Boolean {
            return state == PUMP_NO_POWER || state == PUMP_ZERO_RESERVOIR ||
                (state in PUMP_NO_RESERVOIR..PUMP_MOTOR_AUTO_POWEROFF) || state == PUMP_UNKNOWN_ALARM
        }

        fun isLowAlarm(state: AlarmState): Boolean {
            return (state == PUMP_LOW_POWER ||
                state == PUMP_LOW_RESERVOIR)
        }
    }
}
