package info.nightscout.androidaps.plugins.pump.omnipod.comm.data;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodResponseType;

public class PodCommResponse {

    PodResponseType podResponseType;

    Boolean acknowledged;
    Object customData;
    Object errorResponse;

    public boolean isAcknowledged() {
        return (acknowledged != null && acknowledged);
    }

    // some status data if it can be returned (battery, reservoir, etc)

}
