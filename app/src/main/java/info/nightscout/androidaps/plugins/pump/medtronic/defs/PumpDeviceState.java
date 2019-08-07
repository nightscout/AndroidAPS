package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 6/11/18.
 */

public enum PumpDeviceState {

    NeverContacted(R.string.medtronic_pump_status_never_contacted), //
    Sleeping(R.string.medtronic_pump_status_sleeping), //
    WakingUp(R.string.medtronic_pump_status_waking_up), //
    Active(R.string.medtronic_pump_status_active), //
    ErrorWhenCommunicating(R.string.medtronic_pump_status_error_comm), //
    TimeoutWhenCommunicating(R.string.medtronic_pump_status_timeout_comm), //
    // ProblemContacting(R.string.medtronic_pump_status_problem_contacting), //
    PumpUnreachable(R.string.medtronic_pump_status_pump_unreachable), //
    InvalidConfiguration(R.string.medtronic_pump_status_invalid_config);

    Integer resourceId;

    PumpDeviceState(int resourceId) {
        this.resourceId = resourceId;
    }

    public Integer getResourceId() {
        return resourceId;
    }
}
