package info.nightscout.androidaps.plugins.PumpMedtronic.defs;

/**
 * Created by andy on 6/11/18.
 */

public enum PumpDeviceState {

    NeverContacted, //
    Sleeping, //
    WakingUp, //
    Active, //
    ErrorWhenCommunicating, //
    TimeoutWhenCommunicating, //
    ProblemContacting, //
    InvalidConfiguration;


    Integer resourceId = null;

    PumpDeviceState() {

    }


    PumpDeviceState(int resourceId) {
        this.resourceId = resourceId;
    }

}
