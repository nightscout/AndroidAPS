package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.OmnipodRileyLinkCommunicationManager;

public interface OmnipodAction<T> {
    T execute(OmnipodRileyLinkCommunicationManager communicationService);
}
