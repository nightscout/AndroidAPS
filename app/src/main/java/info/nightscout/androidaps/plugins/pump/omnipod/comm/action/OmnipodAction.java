package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;

public interface OmnipodAction<T> {
    T execute(OmnipodCommunicationManager communicationService);
}
