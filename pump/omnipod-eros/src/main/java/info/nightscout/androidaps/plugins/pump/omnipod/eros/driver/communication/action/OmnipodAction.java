package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public interface OmnipodAction<T> {
    T execute(OmnipodRileyLinkCommunicationManager communicationService);
}
