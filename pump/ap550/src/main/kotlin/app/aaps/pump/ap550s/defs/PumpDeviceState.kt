package app.aaps.pump.ap550s.defs

import app.aaps.pump.ap550s.R

enum class PumpDeviceState(var resourceId: Int) {

    NeverContacted(R.string.pump_status_never_contacted),
    Sleeping(R.string.pump_status_sleeping),
    Pairing(R.string.pump_status_pairing),
    WakingUp(R.string.pump_status_waking_up),
    Active(R.string.pump_status_active),
    ErrorWhenCommunicating(R.string.pump_status_error_comm),
    TimeoutWhenCommunicating(R.string.pump_status_timeout_comm),
    InvalidConfiguration(R.string.pump_status_invalid_config);
}