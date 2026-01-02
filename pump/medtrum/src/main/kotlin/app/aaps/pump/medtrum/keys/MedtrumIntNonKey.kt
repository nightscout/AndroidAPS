package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState

enum class MedtrumIntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    PumpState("pump_state", defaultValue = MedtrumPumpState.NONE.state.toInt()),
    CurrentSequenceNumber("current_sequence_number", defaultValue = 0),
    SyncedSequenceNumber("synced_sequence_number", defaultValue = 0),
    DeviceType("device_type", defaultValue = 0),
    PumpTimezoneOffset("pump_time_zone_offset", defaultValue = 0),
}
