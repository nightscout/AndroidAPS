package app.aaps.pump.common.extensions

import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.pump.common.hw.rileylink.R

fun PumpDeviceState.stringResource() =
    when (this) {
        PumpDeviceState.NeverContacted           -> R.string.pump_status_never_contacted
        PumpDeviceState.Sleeping                 -> R.string.pump_status_sleeping
        PumpDeviceState.WakingUp                 -> R.string.pump_status_waking_up
        PumpDeviceState.Active                   -> R.string.pump_status_active
        PumpDeviceState.ErrorWhenCommunicating   -> R.string.pump_status_error_comm
        PumpDeviceState.TimeoutWhenCommunicating -> R.string.pump_status_timeout_comm
        PumpDeviceState.PumpUnreachable          -> R.string.pump_status_pump_unreachable
        PumpDeviceState.InvalidConfiguration     -> R.string.pump_status_invalid_config
    }