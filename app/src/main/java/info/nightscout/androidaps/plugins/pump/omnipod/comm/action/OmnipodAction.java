package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;

public interface OmnipodAction<T> {
    T execute(OmnipodCommunicationService communicationService);
}
