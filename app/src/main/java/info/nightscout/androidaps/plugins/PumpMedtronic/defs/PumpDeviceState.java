package info.nightscout.androidaps.plugins.PumpMedtronic.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 6/11/18.
 */

public enum PumpDeviceState {

    NeverContacted(R.string.medtronic_pump_status_never_contacted), //
    Sleeping, //
    WakingUp(R.string.medtronic_pump_status_waking_up), //
    Active, //
    ErrorWhenCommunicating(R.string.medtronic_pump_status_error_comm), //
    TimeoutWhenCommunicating(R.string.medtronic_pump_status_timeout_comm), //
    ProblemContacting(R.string.medtronic_pump_status_problem_contacting), //
    InvalidConfiguration(R.string.medtronic_pump_status_invalid_config);


    Integer resourceId = null;

    PumpDeviceState() {

    }


    PumpDeviceState(int resourceId) {
        this.resourceId = resourceId;
    }

    public Integer getResourceId() {
        return resourceId;
    }
}
