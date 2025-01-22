package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class MedtronicLongNonKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    FirstPumpUse("medtronic_first_pump_use", 0),
    LastGoodPumpCommunicationTime("medtronic_lastGoodPumpCommunicationTime", 0L),
    LastPumpHistoryEntry("medtronic_pump_history_entry", 0L),
    LastPrime("medtronic_last_sent_prime", 0L),
    LastRewind("medtronic_last_sent_rewind", 0L),
    LastBatteryChange("medtronic_last_sent_battery_change", 0L),
}