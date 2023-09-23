package info.nightscout.pump.common.defs

import info.nightscout.pump.common.R

enum class PumpDeviceState(var resourceId: Int) {

    NeverContacted(R.string.pump_status_never_contacted),
    Sleeping(R.string.pump_status_sleeping),
    WakingUp(R.string.pump_status_waking_up),
    Active(R.string.pump_status_active),
    ErrorWhenCommunicating(R.string.pump_status_error_comm),
    TimeoutWhenCommunicating(R.string.pump_status_timeout_comm),

    // ProblemContacting(R.string.medtronic_pump_status_problem_contacting),
    PumpUnreachable(R.string.pump_status_pump_unreachable),
    InvalidConfiguration(R.string.pump_status_invalid_config);
}