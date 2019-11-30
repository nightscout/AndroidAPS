package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;

@FunctionalInterface
public interface StatusResponseHandler {
    void handle(StatusResponse statusResponse);
}
