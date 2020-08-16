package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 4.8.2019
 */
// TODO remove this class and use PumpDeviceState instead
//  BS: Actually, don't use PumpDeviceState in Omnipod driver at all; use PodStateManager
//  If PumpDeviceState is Omnipod specific; also remove that
public enum PodDeviceState {

    // FIXME
    NeverContacted(R.string.medtronic_pump_status_never_contacted), //
    Sleeping(R.string.medtronic_pump_status_sleeping), //
    WakingUp(R.string.medtronic_pump_status_waking_up), //
    Active(R.string.medtronic_pump_status_active), //
    ErrorWhenCommunicating(R.string.medtronic_pump_status_error_comm), //
    TimeoutWhenCommunicating(R.string.medtronic_pump_status_timeout_comm), //
    // ProblemContacting(R.string.medtronic_pump_status_problem_contacting), //
    PumpUnreachable(R.string.medtronic_pump_status_pump_unreachable), //
    InvalidConfiguration(R.string.medtronic_pump_status_invalid_config);

    Integer resourceId = null;


    PodDeviceState() {

    }


    PodDeviceState(int resourceId) {
        this.resourceId = resourceId;
    }


    public Integer getResourceId() {
        return resourceId;
    }
}
