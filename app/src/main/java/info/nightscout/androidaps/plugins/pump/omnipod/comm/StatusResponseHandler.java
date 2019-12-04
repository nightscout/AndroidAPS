package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;

// TODO replace with Consumer when our min API level >= 24
@FunctionalInterface
public interface StatusResponseHandler {
    void handle(StatusResponse statusResponse);
}
