package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class MedtronicLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    TbrsSet("medtronic_tbrs_set", 0),
    SmbBoluses("medtronic_smb_boluses_delivered", 0),
    StandardBoluses("medtronic_std_boluses_delivered", 0),
    FirstPumpUse("medtronic_first_pump_use", 0),
    LastGoodPumpCommunicationTime("medtronic_lastGoodPumpCommunicationTime", 0L),
    LastPumpHistoryEntry("medtronic_pump_history_entry", 0L),
    LastPrime("medtronic_last_sent_prime", 0L),
    LastRewind("medtronic_last_sent_rewind", 0L),
    LastBatteryChange("medtronic_last_sent_battery_change", 0L),
}